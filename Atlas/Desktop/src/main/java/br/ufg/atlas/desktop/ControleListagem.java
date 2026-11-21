package br.ufg.atlas.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class ControleListagem {
    @FXML private TableView<JsonObject> tabela;
    @FXML private Button acaoPrincipal, acaoSecundaria, acaoTerciaria;
    private Button[] botoes;
    private final Map<Button, Predicate<JsonObject>> permissoes = new HashMap<>();

    @FXML private void initialize() {
        botoes = new Button[]{acaoPrincipal, acaoSecundaria, acaoTerciaria};
        for (Button botao : botoes) {
            botao.setVisible(false);
            botao.setManaged(false);
            permissoes.put(botao, linha -> false);
        }
        tabela.getSelectionModel().selectedItemProperty().addListener((obs, antiga, atual) -> {
            for (Button botao : botoes) {
                botao.setDisable(!permissoes.get(botao).test(atual));
            }
        });
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabela.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    void coluna(String titulo, Function<JsonObject, String> valor) {
        TableColumn<JsonObject, String> coluna = new TableColumn<>(titulo);
        coluna.setCellValueFactory(celula -> new ReadOnlyStringWrapper(valor.apply(celula.getValue())));
        tabela.getColumns().add(coluna);
    }

    void campo(String titulo, String nome) {
        coluna(titulo, linha -> Dados.texto(linha, nome));
    }

    void codigo(String titulo, String nome) {
        coluna(titulo, linha -> Dados.rotulo(Dados.texto(linha, nome)));
    }

    void dinheiro(String titulo, String nome) {
        coluna(titulo, linha -> Dados.reais(linha.get(nome).getAsLong()));
    }

    void acao(int posicao, String titulo, Predicate<JsonObject> permite, Consumer<JsonObject> executar) {
        Button botao = botoes[posicao];
        botao.setText(titulo);
        botao.setVisible(true);
        botao.setManaged(true);
        botao.setDisable(!permite.test(null));
        permissoes.put(botao, permite);
        botao.setOnAction(evento -> {
            JsonObject atual = tabela.getSelectionModel().getSelectedItem();
            if (permite.test(atual)) executar.accept(atual);
        });
    }

    void limpar() {
        tabela.getItems().clear();
        tabela.getColumns().clear();
        for (Button botao : botoes) {
            botao.setVisible(false);
            botao.setManaged(false);
        }
    }

    void textoConformeSelecao(int posicao, Function<JsonObject, String> texto) {
        tabela.getSelectionModel().selectedItemProperty().addListener((obs, anterior, atual) ->
                botoes[posicao].setText(texto.apply(atual)));
    }

    void preencher(JsonArray dados, Predicate<JsonObject> incluir) {
        JsonObject anterior = tabela.getSelectionModel().getSelectedItem();
        String chave = anterior != null && anterior.has("codigo") ? "codigo" : "id";
        String selecionado = anterior == null ? null : Dados.texto(anterior, chave);
        tabela.getItems().clear();
        for (JsonElement elemento : dados) {
            JsonObject registro = elemento.getAsJsonObject();
            if (!incluir.test(registro)) continue;
            tabela.getItems().add(registro);
            if (Dados.texto(registro, chave).equals(selecionado)) tabela.getSelectionModel().select(registro);
        }
        tabela.sort();
    }
}
