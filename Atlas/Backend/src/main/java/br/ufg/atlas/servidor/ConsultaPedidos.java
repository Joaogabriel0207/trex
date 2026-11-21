package br.ufg.atlas.servidor;

import br.ufg.atlas.modelo.Cargo;
import br.ufg.atlas.modelo.Funcionario;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

final class ConsultaPedidos {
    private static final String CONSULTA = "SELECT p.id, p.comanda_id, m.numero, "
            + "coalesce(f.codigo, p.funcionario_codigo_historico) AS codigo, "
            + "coalesce(f.nome, p.funcionario_nome_historico) AS funcionario, p.estado, p.criacao, "
            + "coalesce(i.produto_id, i.produto_id_historico) AS produto_id, "
            + "i.nome, i.setor, i.quantidade, i.observacao, i.preco_centavos "
            + "FROM pedidos p JOIN comandas c ON c.id = p.comanda_id JOIN mesas m ON m.id = c.mesa_id "
            + "LEFT JOIN funcionarios f ON f.codigo = p.funcionario_codigo JOIN itens_pedido i ON i.pedido_id = p.id ";

    private ConsultaPedidos() { }

    static JsonArray listar(Connection conexao, Funcionario funcionario) throws SQLException {
        if (funcionario.getCargo() == Cargo.GARCOM) {
            return ler(conexao, "WHERE p.funcionario_codigo = ?", funcionario.getCodigo());
        }
        return ler(conexao, "");
    }

    static JsonArray daComanda(Connection conexao, int comandaId) throws SQLException {
        return ler(conexao, "WHERE p.comanda_id = ?", comandaId);
    }

    private static JsonArray ler(Connection conexao, String filtro, Object... parametros) throws SQLException {
        JsonArray pedidos = new JsonArray();
        String sql = CONSULTA + filtro + " ORDER BY p.id DESC, i.id";
        int ultimoId = 0;
        JsonObject pedido = null;
        long total = 0;
        try (PreparedStatement comando = Consultas.preparar(conexao, sql, parametros);
             ResultSet linhas = comando.executeQuery()) {
            while (linhas.next()) {
                int id = linhas.getInt("id");
                if (id != ultimoId) {
                    pedido = cabecalho(linhas);
                    pedidos.add(pedido);
                    ultimoId = id;
                    total = 0;
                }
                pedido.getAsJsonArray("itens").add(item(linhas));
                long subtotal = Math.multiplyExact(linhas.getLong("preco_centavos"), linhas.getInt("quantidade"));
                total = Math.addExact(total, subtotal);
                pedido.addProperty("totalCentavos", total);
            }
        }
        return pedidos;
    }

    private static JsonObject cabecalho(ResultSet linha) throws SQLException {
        JsonObject pedido = new JsonObject();
        pedido.addProperty("id", linha.getInt("id"));
        pedido.addProperty("comandaId", linha.getInt("comanda_id"));
        pedido.addProperty("mesaNumero", linha.getInt("numero"));
        pedido.addProperty("funcionarioCodigo", linha.getString("codigo"));
        pedido.addProperty("funcionarioNome", linha.getString("funcionario"));
        pedido.addProperty("estado", linha.getString("estado"));
        pedido.addProperty("criadoEm", linha.getObject("criacao", OffsetDateTime.class).toString());
        pedido.add("itens", new JsonArray());
        return pedido;
    }

    private static JsonObject item(ResultSet linha) throws SQLException {
        JsonObject item = new JsonObject();
        item.addProperty("produtoId", linha.getInt("produto_id"));
        item.addProperty("nome", linha.getString("nome"));
        item.addProperty("quantidade", linha.getInt("quantidade"));
        item.addProperty("observacao", linha.getString("observacao"));
        item.addProperty("precoCentavos", linha.getLong("preco_centavos"));
        item.addProperty("setor", linha.getString("setor"));
        return item;
    }
}
