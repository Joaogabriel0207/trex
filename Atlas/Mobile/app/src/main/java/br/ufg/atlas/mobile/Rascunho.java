package br.ufg.atlas.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.UUID;

final class Rascunho {
    private final SharedPreferences arquivo;
    private final String dono;
    private long comanda;
    private int mesa;
    private String chave = "";
    private JSONArray itens = new JSONArray();

    Rascunho(Context contexto, String servidor, String codigo) {
        arquivo = contexto.getSharedPreferences("pedidos_do_garcom", Context.MODE_PRIVATE);
        dono = servidor + "/" + codigo;
        String salvo = arquivo.getString(dono, null);
        if (salvo == null) return;
        try {
            JSONObject dados = new JSONObject(salvo);
            comanda = dados.getLong("comandaId");
            mesa = dados.getInt("mesa");
            chave = dados.getString("chave");
            itens = dados.getJSONArray("itens");
        } catch (JSONException erro) {
            throw new IllegalStateException("O rascunho gravado não pôde ser lido.", erro);
        }
    }

    long comanda() { return comanda; }
    int mesa() { return mesa; }
    boolean pendente() { return !chave.isEmpty(); }
    JSONArray itens() { return itens; }

    void iniciar(long id, int numero) {
        if (comanda == id) return;
        if (itens.length() != 0) throw new IllegalStateException("Conclua o pedido da mesa " + mesa + " primeiro.");
        comanda = id;
        mesa = numero;
    }

    int indice(long produto) {
        for (int i = 0; i < itens.length(); i++) {
            if (itens.optJSONObject(i).optLong("produtoId") == produto) return i;
        }
        return -1;
    }

    void editar(long produto, String nome, long preco, int quantidade, String observacao) {
        if (pendente()) throw new IllegalStateException("Confirme o envio anterior antes de editar.");
        if (quantidade < 0) throw new IllegalArgumentException("A quantidade não pode ser negativa.");
        int posicao = indice(produto);
        if (quantidade == 0) {
            if (posicao >= 0) itens.remove(posicao);
        } else {
            JSONObject linha = Dados.objeto("produtoId", produto, "nome", nome, "precoCentavos", preco,
                    "quantidade", quantidade, "observacao", observacao);
            try {
                if (posicao < 0) itens.put(linha);
                else itens.put(posicao, linha);
            } catch (JSONException erro) {
                throw new IllegalArgumentException("Não foi possível atualizar o item.", erro);
            }
        }
        salvar();
    }

    long total() {
        long total = 0;
        for (int i = 0; i < itens.length(); i++) {
            JSONObject item = itens.optJSONObject(i);
            total = Math.addExact(total, Math.multiplyExact(item.optLong("precoCentavos"), item.optInt("quantidade")));
        }
        return total;
    }

    JSONObject prepararEnvio() {
        if (itens.length() == 0) throw new IllegalStateException("Selecione ao menos um produto.");
        if (!pendente()) chave = UUID.randomUUID().toString();
        // A mesma chave e o mesmo conteúdo sobrevivem à perda da resposta e ao reinício.
        salvar();
        JSONArray linhas = new JSONArray();
        for (int i = 0; i < itens.length(); i++) {
            JSONObject item = itens.optJSONObject(i);
            linhas.put(Dados.objeto("produtoId", item.optLong("produtoId"), "quantidade", item.optInt("quantidade"),
                    "observacao", item.optString("observacao")));
        }
        return Dados.objeto("chave", chave, "comandaId", comanda, "itens", linhas);
    }

    void recusar() {
        chave = "";
        salvar();
    }

    void limpar() {
        if (!arquivo.edit().remove(dono).commit()) throw new IllegalStateException("Falha ao limpar o rascunho.");
        chave = "";
        itens = new JSONArray();
        comanda = 0;
        mesa = 0;
    }

    private void salvar() {
        JSONObject dados = Dados.objeto("comandaId", comanda, "mesa", mesa, "chave", chave, "itens", itens);
        if (!arquivo.edit().putString(dono, dados.toString()).commit()) {
            throw new IllegalStateException("Sem espaço para salvar o rascunho. O envio pode ser repetido após liberar espaço.");
        }
    }
}
