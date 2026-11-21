package br.ufg.atlas.mobile;

import android.app.AlertDialog;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

final class Salao {
    private final Atendimento tela;

    Salao(Atendimento tela) { this.tela = tela; }

    void mesas() {
        tela.mostrar(Atendimento.Pagina.MESAS, "Selecionar mesa", "");
        tela.executar(() -> tela.api.listar("/mesas"), mesas -> {
            ArrayList<Linhas.Linha> linhas = new ArrayList<>();
            for (int i = 0; i < mesas.length(); i++) {
                JSONObject mesa = mesas.getJSONObject(i);
                linhas.add(new Linhas.Linha("Mesa " + mesa.getInt("numero"), Dados.estado(mesa.getString("estado")),
                        () -> selecionar(mesa)));
            }
            tela.linhas(linhas, "Nenhuma mesa cadastrada.");
        });
    }

    void mesaLivre() {
        tela.mostrar(Atendimento.Pagina.COMANDA, "Mesa " + tela.mesaNumero, "");
        tela.executar(() -> tela.api.listar("/mesas"), lista -> {
            for (int i = 0; i < lista.length(); i++) {
                JSONObject mesa = lista.getJSONObject(i);
                if (mesa.getLong("id") == tela.mesaId) { selecionar(mesa); return; }
            }
            mesas();
        });
    }

    private void selecionar(JSONObject mesa) {
        tela.mesaId = mesa.optLong("id");
        tela.mesaNumero = mesa.optInt("numero");
        tela.comandaId = mesa.optLong("comandaId", 0);
        if (tela.comandaId != 0) { comanda(); return; }
        long mesaId = mesa.optLong("id");
        tela.mostrar(Atendimento.Pagina.COMANDA, "Mesa " + tela.mesaNumero, "Mesa livre");
        tela.linhas(new ArrayList<>(), "Nenhuma comanda aberta.");
        tela.acao(R.id.principal, "Abrir comanda", () -> tela.confirmar("Abrir comanda?",
                "Mesa " + tela.mesaNumero, "Abrir", () -> tela.executar(
                        () -> tela.api.alterar("/comandas", new JSONObject().put("mesaId", mesaId)), resposta -> {
                            tela.comandaId = resposta.getLong("id");
                            comanda();
                        })));
    }

    void comanda() {
        final long id = tela.comandaId;
        tela.mostrar(Atendimento.Pagina.COMANDA, "Mesa " + tela.mesaNumero, "");
        tela.executar(() -> tela.api.obter("/comandas/" + id), comanda -> {
            String estado = comanda.getString("estado");
            String total = Dados.reais(comanda.getLong("totalCentavos"));
            tela.mostrar(Atendimento.Pagina.COMANDA, "Mesa " + comanda.getInt("mesaNumero"),
                    "Comanda " + id + " · " + Dados.estado(estado) + "\nTotal confirmado   " + total);
            JSONArray pedidos = comanda.getJSONArray("pedidos");
            ArrayList<Linhas.Linha> linhas = new ArrayList<>();
            for (int i = 0; i < pedidos.length(); i++) {
                JSONObject pedido = pedidos.getJSONObject(i);
                linhas.add(new Linhas.Linha("Pedido " + pedido.getLong("id") + " · " + Dados.reais(pedido.getLong("totalCentavos")),
                        descreverItens(pedido), () -> detalhes(pedido, false)));
            }
            tela.linhas(linhas, "Nenhum item registrado.");
            if ("ABERTA".equals(estado)) {
                tela.acao(R.id.principal, "Ver cardápio", () -> abrirCardapio(id));
                tela.acao(R.id.secundaria, "Solicitar conta", () -> tela.confirmar("Solicitar a conta?",
                        "Total confirmado: " + total + ". O caixa receberá o pagamento e encerrará a comanda.",
                        "Solicitar", () -> tela.executar(() -> tela.api.alterar("/comandas/" + id + "/solicitar", new JSONObject()),
                                resposta -> { tela.aviso("Conta solicitada ao caixa."); comanda(); })));
            }
        }, erro -> {
            if (erro instanceof Api.Recusa && ((Api.Recusa) erro).status == 404) {
                tela.aviso("Esta comanda já não está disponível.");
                mesas();
            } else tela.erro(erro);
        });
    }

    private void abrirCardapio(long id) {
        try {
            tela.rascunho.iniciar(id, tela.mesaNumero);
            if (tela.rascunho.pendente()) tela.montagem.resumo();
            else tela.montagem.cardapio();
        } catch (IllegalStateException conflito) {
            tela.confirmar("Pedido em montagem", conflito.getMessage(), "Retomar", () -> {
                tela.comandaId = tela.rascunho.comanda();
                tela.mesaNumero = tela.rascunho.mesa();
                tela.montagem.resumo();
            });
        }
    }

    void pedidos() {
        tela.mostrar(Atendimento.Pagina.PEDIDOS, "Meus pedidos", "");
        tela.executar(() -> tela.api.listar("/pedidos"), pedidos -> {
            ArrayList<Linhas.Linha> linhas = new ArrayList<>();
            for (int i = 0; i < pedidos.length(); i++) {
                JSONObject pedido = pedidos.getJSONObject(i);
                linhas.add(new Linhas.Linha("Pedido " + pedido.getLong("id") + " · Mesa " + pedido.getInt("mesaNumero"),
                        Dados.estado(pedido.getString("estado")) + " · " + Dados.reais(pedido.getLong("totalCentavos")),
                        () -> detalhes(pedido, true)));
            }
            tela.linhas(linhas, "Você ainda não lançou pedidos.");
        });
    }

    private void detalhes(JSONObject pedido, boolean proprio) {
        long id = pedido.optLong("id");
        String estado = pedido.optString("estado");
        String descricao = "Mesa " + pedido.optInt("mesaNumero") + " · " + Dados.estado(estado) + "\n\n"
                + descreverItens(pedido) + "\n\nTotal: " + Dados.reais(pedido.optLong("totalCentavos"));
        AlertDialog.Builder dialogo = new AlertDialog.Builder(tela).setTitle("Pedido " + id)
                .setMessage(descricao).setNegativeButton("Fechar", null);
        if (proprio && "PRONTO".equals(estado)) {
            dialogo.setPositiveButton("Marcar entregue", (d, v) -> tela.executar(
                    () -> tela.api.alterar("/pedidos/" + id + "/entregar", new JSONObject()),
                    resposta -> { tela.aviso("Entrega registrada."); pedidos(); }));
        }
        dialogo.show();
    }

    private String descreverItens(JSONObject pedido) {
        StringBuilder texto = new StringBuilder();
        JSONArray itens = pedido.optJSONArray("itens");
        for (int i = 0; itens != null && i < itens.length(); i++) {
            JSONObject item = itens.optJSONObject(i);
            int quantidade = item.optInt("quantidade");
            if (i > 0) texto.append("\n");
            texto.append(quantidade).append(" × ").append(item.optString("nome")).append(" · ")
                    .append(Dados.reais(quantidade * item.optLong("precoCentavos")));
            String observacao = item.optString("observacao");
            if (!observacao.isEmpty()) texto.append("\n").append(observacao);
        }
        return texto.toString();
    }
}
