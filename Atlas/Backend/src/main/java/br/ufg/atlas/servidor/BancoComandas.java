package br.ufg.atlas.servidor;

import br.ufg.atlas.modelo.Cargo;
import br.ufg.atlas.modelo.Comanda;
import br.ufg.atlas.modelo.FormaPagamento;
import br.ufg.atlas.modelo.Funcionario;
import br.ufg.atlas.modelo.Pagamento;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;

final class BancoComandas {
    private static final String RESUMO = "SELECT c.id, m.numero AS mesa_numero, c.estado, "
            + "coalesce(sum(i.preco_centavos * i.quantidade), 0) AS total_centavos "
            + "FROM comandas c JOIN mesas m ON m.id = c.mesa_id LEFT JOIN pedidos p ON p.comanda_id = c.id "
            + "LEFT JOIN itens_pedido i ON i.pedido_id = p.id ";
    private static final String AGRUPAR = " GROUP BY c.id, m.numero ORDER BY c.id DESC";

    private BancoComandas() { }

    static JsonObject abrir(Connection conexao, Funcionario garcom, int mesaId) throws SQLException {
        JsonObject mesa = Consultas.um(conexao, "SELECT id FROM mesas WHERE id = ? FOR UPDATE", mesaId);
        if (mesa == null) throw new ErroHttp(404, "Mesa não encontrada.");
        JsonObject existente = Consultas.um(conexao,
                "SELECT id FROM comandas WHERE mesa_id = ? AND estado <> 'ENCERRADA' FOR UPDATE", mesaId);
        if (existente != null) return existente;
        return Consultas.um(conexao, "INSERT INTO comandas (mesa_id, funcionario_codigo, estado) "
                + "VALUES (?, ?, 'ABERTA') RETURNING id", mesaId, garcom.getCodigo());
    }

    static JsonObject bloquear(Connection conexao, int id) throws SQLException {
        JsonObject linha = Consultas.um(conexao, "SELECT id, estado FROM comandas WHERE id = ? FOR UPDATE", id);
        if (linha == null) throw new ErroHttp(404, "Comanda não encontrada.");
        return linha;
    }

    static long total(Connection conexao, int id) throws SQLException {
        JsonObject soma = Consultas.um(conexao, "SELECT coalesce(sum(i.preco_centavos * i.quantidade), 0) AS total "
                + "FROM pedidos p JOIN itens_pedido i ON i.pedido_id = p.id WHERE p.comanda_id = ?", id);
        return soma.get("total").getAsBigDecimal().longValueExact();
    }

    static JsonArray listar(Connection conexao, Funcionario funcionario) throws SQLException {
        String filtro = funcionario.getCargo() == Cargo.GARCOM ? "WHERE c.estado <> 'ENCERRADA'" : "";
        return Consultas.lista(conexao, RESUMO + filtro + AGRUPAR);
    }

    static JsonObject detalhar(Connection conexao, Funcionario funcionario, int id) throws SQLException {
        // Mantém a mesma comanda entre a leitura do estado e a leitura das linhas de consumo.
        JsonObject cabecalho = Consultas.um(conexao, "SELECT c.id, m.numero AS mesa_numero, c.estado FROM comandas c "
                + "JOIN mesas m ON c.mesa_id = m.id WHERE c.id = ? FOR SHARE OF c", id);
        if (cabecalho == null || (funcionario.getCargo() == Cargo.GARCOM
                && "ENCERRADA".equals(cabecalho.get("estado").getAsString()))) {
            throw new ErroHttp(404, "Comanda não encontrada.");
        }
        cabecalho.addProperty("totalCentavos", total(conexao, id));
        cabecalho.add("pedidos", ConsultaPedidos.daComanda(conexao, id));
        return cabecalho;
    }

    static JsonObject solicitar(Connection conexao, int id) throws SQLException {
        JsonObject linha = Consultas.um(conexao, "UPDATE comandas SET estado = 'CONTA_SOLICITADA' "
                + "WHERE id = ? AND estado IN ('ABERTA','CONTA_SOLICITADA') RETURNING id", id);
        if (linha == null) throw new ErroHttp(409, "A comanda não está em atendimento.");
        return linha;
    }

    static JsonObject pagar(Connection conexao, Funcionario caixa, int id, JsonObject dados) throws SQLException {
        JsonObject comanda = bloquear(conexao, id);
        long valor = Campos.inteiro(dados, "valorCentavos");
        FormaPagamento forma;
        try {
            forma = FormaPagamento.valueOf(Campos.texto(dados, "forma", 20));
        } catch (IllegalArgumentException erro) {
            throw Campos.invalido("forma");
        }
        Pagamento pagamento = new Pagamento(valor, forma);
        Comanda.validarPagamento(comanda.get("estado").getAsString(), total(conexao, id), pagamento);
        Consultas.executar(conexao, "INSERT INTO pagamentos (comanda_id, funcionario_codigo, valor_centavos, forma) "
                + "VALUES (?, ?, ?, ?)", id, caixa.getCodigo(), valor, forma.name());
        return Consultas.um(conexao, "UPDATE comandas SET estado = 'ENCERRADA', encerramento = current_timestamp "
                + "WHERE id = ? RETURNING id", id);
    }
}
