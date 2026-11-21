package br.ufg.atlas.mobile;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

final class Montagem {
    private final Atendimento tela;
    private final EditText pesquisa;
    private final Spinner categorias;
    private JSONArray produtos = new JSONArray();
    private ArrayList<String> nomesCategorias = new ArrayList<>();

    Montagem(Atendimento tela) {
        this.tela = tela;
        pesquisa = tela.findViewById(R.id.pesquisa);
        categorias = tela.findViewById(R.id.categoria);
        pesquisa.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence texto, int inicio, int tamanho, int depois) {}
            @Override public void onTextChanged(CharSequence texto, int inicio, int antes, int tamanho) { filtrar(); }
            @Override public void afterTextChanged(Editable texto) {}
        });
        categorias.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> pai, View v, int indice, long id) { filtrar(); }
            @Override public void onNothingSelected(AdapterView<?> pai) {}
        });
    }

    void cardapio() {
        if (tela.rascunho.pendente()) { resumo(); return; }
        tela.rascunho.iniciar(tela.comandaId, tela.mesaNumero);
        tela.mostrar(Atendimento.Pagina.CARDAPIO, "Montar pedido", "Mesa " + tela.mesaNumero);
        atualizarProdutos();
    }

    void atualizarProdutos() {
        tela.executar(() -> tela.api.listar("/produtos"), lista -> {
            produtos = lista;
            String anterior = (String) categorias.getSelectedItem();
            nomesCategorias = new ArrayList<>();
            nomesCategorias.add("Todas as categorias");
            for (int i = 0; i < produtos.length(); i++) {
                JSONObject produto = produtos.getJSONObject(i);
                String categoria = produto.getString("categoria");
                if (produto.getBoolean("disponivel") && !nomesCategorias.contains(categoria)) nomesCategorias.add(categoria);
            }
            ArrayAdapter<String> adaptador = new ArrayAdapter<>(tela, android.R.layout.simple_spinner_item, nomesCategorias);
            adaptador.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            categorias.setAdapter(adaptador);
            categorias.setSelection(Math.max(0, nomesCategorias.indexOf(anterior)));
            filtrar();
        });
    }

    private void filtrar() {
        if (tela.pagina != Atendimento.Pagina.CARDAPIO) return;
        ArrayList<Linhas.Linha> linhas = new ArrayList<>();
        String trecho = pesquisa.getText().toString().trim().toLowerCase(Locale.ROOT);
        int filtro = categorias.getSelectedItemPosition();
        for (int i = 0; i < produtos.length(); i++) {
            JSONObject produto = produtos.optJSONObject(i);
            String nome = produto.optString("nome");
            String categoria = produto.optString("categoria");
            if (!produto.optBoolean("disponivel") || !nome.toLowerCase(Locale.ROOT).contains(trecho)) continue;
            if (filtro > 0 && !categoria.equals(categorias.getSelectedItem())) continue;
            int posicao = tela.rascunho.indice(produto.optLong("id"));
            int quantidade = posicao < 0 ? 0 : tela.rascunho.itens().optJSONObject(posicao).optInt("quantidade");
            String detalhe = Dados.reais(produto.optLong("precoCentavos")) + " · " + categoria;
            if (quantidade > 0) detalhe += " · " + quantidade + " no pedido";
            linhas.add(new Linhas.Linha(nome, detalhe, () -> editar(produto.optLong("id"), nome, produto.optLong("precoCentavos"))));
        }
        tela.linhas(linhas, "Nenhum produto disponível para este filtro.");
        if (tela.rascunho.itens().length() > 0) {
            tela.acao(R.id.principal, "Ver pedido · " + Dados.reais(tela.rascunho.total()), this::resumo);
        } else tela.acao(R.id.principal, "", null);
    }

    private void editar(long produto, String nome, long preco) {
        View campos = tela.getLayoutInflater().inflate(R.layout.editar_item, null);
        EditText quantidade = campos.findViewById(R.id.quantidade);
        EditText observacao = campos.findViewById(R.id.observacao);
        observacao.setFilters(new InputFilter[]{new InputFilter.LengthFilter(240)});
        int indice = tela.rascunho.indice(produto);
        if (indice < 0 && tela.rascunho.itens().length() >= 100) {
            tela.aviso("Um pedido comporta até 100 itens distintos.");
            return;
        }
        JSONObject existente = indice < 0 ? null : tela.rascunho.itens().optJSONObject(indice);
        quantidade.setText(existente == null ? "1" : String.valueOf(existente.optInt("quantidade")));
        quantidade.setSelectAllOnFocus(true);
        if (existente != null) observacao.setText(existente.optString("observacao"));
        AlertDialog.Builder construtor = new AlertDialog.Builder(tela).setTitle(nome + " · " + Dados.reais(preco))
                .setView(campos).setNegativeButton("Cancelar", null).setPositiveButton("Salvar", null);
        if (existente != null) {
            construtor.setNeutralButton("Remover", (d, v) -> salvar(produto, nome, preco, 0, ""));
        }
        AlertDialog dialogo = construtor.create();
        dialogo.setOnShowListener(evento -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int valor;
            try { valor = Integer.parseInt(quantidade.getText().toString()); }
            catch (NumberFormatException erro) { valor = 0; }
            if (valor < 1 || valor > 999) {
                quantidade.setError("Informe uma quantidade entre 1 e 999.");
                return;
            }
            if (salvar(produto, nome, preco, valor, observacao.getText().toString().trim())) dialogo.dismiss();
        }));
        dialogo.show();
    }

    private boolean salvar(long id, String nome, long preco, int quantidade, String observacao) {
        try {
            tela.rascunho.editar(id, nome, preco, quantidade, observacao);
            if (tela.pagina == Atendimento.Pagina.RESUMO) resumo(); else filtrar();
            return true;
        } catch (IllegalStateException erro) {
            tela.erro(erro);
            return false;
        }
    }

    void resumo() {
        Rascunho rascunho = tela.rascunho;
        boolean pendente = rascunho.pendente();
        String subtitulo = "Mesa " + tela.mesaNumero + " · Total estimado " + Dados.reais(rascunho.total());
        if (pendente) subtitulo += "\nAguardando confirmação. Consulte o resultado usando o mesmo pedido.";
        tela.mostrar(Atendimento.Pagina.RESUMO, "Resumo do pedido", subtitulo);
        ArrayList<Linhas.Linha> linhas = new ArrayList<>();
        for (int i = 0; i < rascunho.itens().length(); i++) {
            JSONObject item = rascunho.itens().optJSONObject(i);
            String nome = item.optString("nome");
            long preco = item.optLong("precoCentavos");
            int quantidade = item.optInt("quantidade");
            String descricao = Dados.reais(preco) + " cada · Subtotal " + Dados.reais(preco * quantidade);
            if (!item.optString("observacao").isEmpty()) descricao += "\n" + item.optString("observacao");
            linhas.add(new Linhas.Linha(quantidade + " × " + nome, descricao,
                    pendente ? null : () -> editar(item.optLong("produtoId"), nome, preco)));
        }
        tela.linhas(linhas, "O pedido está vazio.");
        if (!linhas.isEmpty()) {
            tela.acao(R.id.principal, pendente ? "Confirmar resultado do envio" : "Enviar pedido", () -> {
                if (pendente) enviar();
                else tela.confirmar("Enviar pedido?", subtituloDoEnvio(), "Enviar", this::enviar);
            });
        }
        if (!pendente) {
            tela.acao(R.id.secundaria, "Voltar ao cardápio", this::cardapio);
        }
    }

    private String subtituloDoEnvio() {
        return "Mesa " + tela.mesaNumero + " · Total estimado " + Dados.reais(tela.rascunho.total());
    }

    private void enviar() {
        JSONObject corpo;
        try { corpo = tela.rascunho.prepararEnvio(); }
        catch (IllegalStateException erro) { tela.erro(erro); return; }
        resumo();
        tela.executar(() -> tela.api.alterar("/pedidos", corpo), resposta -> {
            long id = resposta.getLong("id");
            tela.rascunho.limpar();
            tela.aviso("Pedido " + id + " confirmado.");
            tela.salao.pedidos();
        }, erro -> {
            int status = erro instanceof Api.Recusa ? ((Api.Recusa) erro).status : 0;
            if (status == 400 || status == 404 || status == 409 || status == 422) {
                tela.rascunho.recusar();
                resumo();
                tela.erro(erro);
            } else {
                resumo();
                tela.aviso("Envio sem confirmação. Repita a consulta do resultado quando houver conexão.");
            }
        });
    }
}
