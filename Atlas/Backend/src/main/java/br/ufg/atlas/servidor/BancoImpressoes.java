package br.ufg.atlas.servidor;

import br.ufg.atlas.modelo.Funcionario;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

final class BancoImpressoes {
    private BancoImpressoes() { }

    static JsonArray listar(Connection conexao) throws SQLException {
        String sql = "SELECT t.id, t.pedido_id, m.numero, t.setor, t.estado, t.tentativa, t.detalhe, f.nome AS garcom, "
                + "i.nome, i.quantidade, i.observacao FROM tarefas_impressao t "
                + "JOIN pedidos p ON p.id = t.pedido_id JOIN funcionarios f ON f.codigo = p.funcionario_codigo "
                + "JOIN comandas c ON c.id = p.comanda_id JOIN mesas m ON m.id = c.mesa_id "
                + "JOIN itens_pedido i ON i.pedido_id = p.id AND i.setor = t.setor ORDER BY t.id DESC, i.id";
        JsonArray tarefas = new JsonArray();
        try (PreparedStatement consulta = conexao.prepareStatement(sql); ResultSet linhas = consulta.executeQuery()) {
            int anterior = 0;
            JsonObject tarefa = null;
            StringBuilder texto = new StringBuilder();
            while (linhas.next()) {
                int id = linhas.getInt("id");
                if (anterior != id) {
                    if (tarefa != null) tarefa.addProperty("texto", texto.toString());
                    tarefa = new JsonObject();
                    tarefa.addProperty("id", id);
                    tarefa.addProperty("pedidoId", linhas.getInt("pedido_id"));
                    tarefa.addProperty("mesaNumero", linhas.getInt("numero"));
                    tarefa.addProperty("setor", linhas.getString("setor"));
                    tarefa.addProperty("estado", linhas.getString("estado"));
                    tarefa.addProperty("tentativa", linhas.getInt("tentativa"));
                    tarefa.addProperty("detalhe", linhas.getString("detalhe"));
                    tarefas.add(tarefa);
                    anterior = id;
                    texto.setLength(0);
                    texto.append("ATLAS · ").append(linhas.getString("setor")).append('\n')
                            .append("Pedido ").append(linhas.getInt("pedido_id")).append(" · Mesa ")
                            .append(linhas.getInt("numero")).append('\n').append("Atendimento: ")
                            .append(umaLinha(linhas.getString("garcom"))).append("\n\n");
                }
                texto.append(linhas.getInt("quantidade")).append(" × ")
                        .append(umaLinha(linhas.getString("nome"))).append('\n');
                String observacao = linhas.getString("observacao");
                if (!observacao.isEmpty()) texto.append("   Obs.: ").append(umaLinha(observacao)).append('\n');
            }
            if (tarefa != null) tarefa.addProperty("texto", texto.toString());
        }
        return tarefas;
    }

    private static String umaLinha(String campo) {
        return campo.replaceAll("\\p{Cntrl}", " ");
    }

    static JsonObject reservar(Connection conexao, Funcionario gerente, int id, JsonObject dados) throws SQLException {
        boolean repetir = dados.has("repetir") && Campos.logico(dados, "repetir");
        String origem = repetir ? "FALHA" : "PENDENTE";
        int tentativaAnterior = repetir ? Campos.positivo(dados, "tentativa") : 0;
        JsonObject tarefa = Consultas.um(conexao, "UPDATE tarefas_impressao SET estado = 'EM_TENTATIVA', "
                + "responsavel_codigo = ?, confirmado_por = NULL, detalhe = '', tentativa = tentativa + 1 "
                + "WHERE id = ? AND estado = ? AND tentativa = ? RETURNING id, tentativa",
                gerente.getCodigo(), id, origem, tentativaAnterior);
        if (tarefa == null) throw new ErroHttp(409, "A situação da impressão mudou. Consulte a fila.");
        return tarefa;
    }

    static JsonObject registrarResultado(Connection conexao, Funcionario gerente, int id, JsonObject dados)
            throws SQLException {
        String estado = Campos.texto(dados, "estado", 20);
        String detalhe = Campos.observacao(dados, "detalhe");
        int tentativa = Campos.positivo(dados, "tentativa");
        boolean conferido = dados.has("conferido") && Campos.logico(dados, "conferido");
        if (conferido && detalhe.isBlank()) throw new ErroHttp(400, "Descreva a conferência da fila.");
        if (!estado.equals("ENVIADA") && !estado.equals("FALHA") && !estado.equals("INCERTA")) {
            throw Campos.invalido("estado");
        }
        // Repetir o registro após uma resposta perdida não despacha outro trabalho.
        JsonObject tarefa = Consultas.um(conexao, "UPDATE tarefas_impressao SET estado = ?, detalhe = ?, confirmado_por = ? "
                + "WHERE id = ? AND tentativa = ? AND ((estado = 'EM_TENTATIVA' AND (responsavel_codigo = ? OR ?)) "
                + "OR (estado = 'INCERTA' AND ? AND ? IN ('ENVIADA', 'FALHA')) "
                + "OR (estado = ? AND detalhe = ? AND confirmado_por = ?)) RETURNING id",
                estado, detalhe, gerente.getCodigo(), id, tentativa, gerente.getCodigo(), conferido, conferido, estado,
                estado, detalhe, gerente.getCodigo());
        if (tarefa == null) throw new ErroHttp(409, "A tarefa não está reservada para esta operação.");
        return tarefa;
    }
}
