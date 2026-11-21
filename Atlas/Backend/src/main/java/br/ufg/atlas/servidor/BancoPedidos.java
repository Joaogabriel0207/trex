package br.ufg.atlas.servidor;

import br.ufg.atlas.modelo.Comanda;
import br.ufg.atlas.modelo.Funcionario;
import br.ufg.atlas.modelo.ItemPedido;
import br.ufg.atlas.modelo.Pedido;
import br.ufg.atlas.modelo.Produto;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;

final class BancoPedidos {
    private BancoPedidos() { }

    static JsonObject registrar(Connection conexao, Funcionario garcom, JsonObject dados) throws SQLException {
        int comandaId = Campos.positivo(dados, "comandaId");
        UUID chave = Campos.chave(dados);
        if (!dados.has("itens") || !dados.get("itens").isJsonArray()) {
            throw Campos.invalido("itens");
        }
        JsonArray itens = dados.getAsJsonArray("itens");
        if (itens.size() == 0 || itens.size() > 100) throw new ErroHttp(400, "Selecione entre 1 e 100 itens.");
        // Solicitar a conta, pagar e acrescentar pedidos disputam o mesmo bloqueio.
        JsonObject comanda = BancoComandas.bloquear(conexao, comandaId);
        JsonObject existente = buscarChave(conexao, garcom.getCodigo(), chave);
        if (existente != null) return conferirRepeticao(conexao, existente, comandaId, itens);
        Comanda.exigirAberta(comanda.get("estado").getAsString());
        JsonObject registro = Consultas.um(conexao, "INSERT INTO pedidos (comanda_id, funcionario_codigo, chave, estado) "
                + "VALUES (?, ?, ?, 'REGISTRADO') ON CONFLICT (funcionario_codigo, chave) DO NOTHING RETURNING id",
                comandaId, garcom.getCodigo(), chave);
        if (registro == null) {
            // Uma tentativa com a mesma chave pode ter chegado simultaneamente para outra mesa.
            return conferirRepeticao(conexao, buscarChave(conexao, garcom.getCodigo(), chave), comandaId, itens);
        }
        ArrayList<ItemPedido> selecionados = new ArrayList<>();
        for (JsonElement elemento : itens) {
            JsonObject recebido = lerItem(elemento);
            Produto produto = BancoSalao.buscarParaPedido(conexao, Campos.positivo(recebido, "produtoId"));
            selecionados.add(new ItemPedido(produto, Campos.positivo(recebido, "quantidade"),
                    Campos.observacao(recebido, "observacao")));
        }
        Pedido pedido = new Pedido(garcom, selecionados);
        try {
            Math.addExact(BancoComandas.total(conexao, comandaId), pedido.calcularTotal());
        } catch (ArithmeticException erro) {
            throw new ErroHttp(400, "O total excede o limite em centavos.");
        }
        int pedidoId = registro.get("id").getAsInt();
        for (ItemPedido item : selecionados) {
            Consultas.executar(conexao, "INSERT INTO itens_pedido "
                    + "(pedido_id, produto_id, nome, setor, preco_centavos, quantidade, observacao) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)", pedidoId, item.getProduto().getId(), item.getNome(),
                    item.getSetor().name(), item.getPrecoCentavos(), item.getQuantidade(), item.getObservacao());
        }
        Consultas.executar(conexao, "INSERT INTO tarefas_impressao (pedido_id, setor, estado) "
                + "SELECT DISTINCT pedido_id, setor, 'PENDENTE' FROM itens_pedido WHERE pedido_id = ?", pedidoId);
        return registro;
    }

    private static JsonObject buscarChave(Connection conexao, String codigo, UUID chave) throws SQLException {
        return Consultas.um(conexao, "SELECT id, comanda_id FROM pedidos WHERE funcionario_codigo = ? AND chave = ?",
                codigo, chave);
    }

    private static JsonObject conferirRepeticao(Connection conexao, JsonObject pedido, int comandaId, JsonArray itens)
            throws SQLException {
        int pedidoId = pedido.get("id").getAsInt();
        JsonArray anteriores = Consultas.lista(conexao, "SELECT produto_id, quantidade, observacao FROM itens_pedido "
                + "WHERE pedido_id = ? ORDER BY id", pedidoId);
        if (pedido.get("comandaId").getAsInt() != comandaId || anteriores.size() != itens.size()) throw chaveEmUso();
        for (int i = 0; i < itens.size(); i++) {
            JsonObject recebido = lerItem(itens.get(i));
            JsonObject anterior = anteriores.get(i).getAsJsonObject();
            if (anterior.get("produtoId").getAsInt() != Campos.positivo(recebido, "produtoId")
                    || anterior.get("quantidade").getAsInt() != Campos.positivo(recebido, "quantidade")
                    || !anterior.get("observacao").getAsString().equals(Campos.observacao(recebido, "observacao"))) {
                throw chaveEmUso();
            }
        }
        JsonObject resposta = new JsonObject();
        resposta.addProperty("id", pedidoId);
        return resposta;
    }

    private static JsonObject lerItem(JsonElement elemento) {
        if (!elemento.isJsonObject()) throw Campos.invalido("itens");
        return elemento.getAsJsonObject();
    }

    private static ErroHttp chaveEmUso() {
        return new ErroHttp(409, "A chave de confirmação já pertence a outro pedido.");
    }

    static JsonObject marcarPronto(Connection conexao, int id) throws SQLException {
        JsonObject pedido = Consultas.um(conexao, "UPDATE pedidos SET estado = 'PRONTO' "
                + "WHERE id = ? AND estado = 'REGISTRADO' RETURNING id", id);
        if (pedido == null) throw new ErroHttp(409, "O pedido não está aguardando preparo.");
        return pedido;
    }

    static JsonObject entregar(Connection conexao, int id, Funcionario garcom) throws SQLException {
        JsonObject pedido = Consultas.um(conexao, "UPDATE pedidos SET estado = 'ENTREGUE' "
                + "WHERE id = ? AND funcionario_codigo = ? AND estado = 'PRONTO' RETURNING id",
                id, garcom.getCodigo());
        if (pedido == null) throw new ErroHttp(409, "Somente um pedido próprio e pronto pode ser entregue.");
        return pedido;
    }
}
