package br.ufg.atlas.desktop;

import java.io.IOException;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class ControleLogin {
    @FXML private TextField codigo;
    @FXML private PasswordField senha;
    @FXML private Button botaoEntrar, botaoConexao;
    @FXML private Label aviso;
    private Ambiente ambiente;

    void preparar(Ambiente ambiente) { this.ambiente = ambiente; }

    @FXML private void focarSenha() { senha.requestFocus(); }
    @FXML private void configurar() { ambiente.configurar(false); }

    @FXML private void entrar() {
        if (botaoEntrar.isDisabled()) return;
        String identificacao = codigo.getText().trim();
        String segredo = senha.getText();
        if (identificacao.isEmpty() || segredo.isEmpty()) {
            aviso.setText("Informe o código e a senha.");
            return;
        }
        botaoEntrar.setDisable(true);
        botaoConexao.setDisable(true);
        senha.clear();
        aviso.setText("Conectando…");
        ClienteHttp api = ambiente.api;
        ambiente.executar(() -> {
            var usuario = api.entrar(identificacao, segredo);
            String cargo = Dados.texto(usuario, "cargo");
            if (!cargo.equals("GERENTE") && !cargo.equals("CAIXA")) {
                api.alterar("POST", "/sair", new com.google.gson.JsonObject());
                throw new IllegalArgumentException("O acesso ao Desktop exige o cargo de gerente ou caixa.");
            }
            return usuario;
        }, usuario -> {
            try { ambiente.painel(usuario); }
            catch (IOException e) { falha("Falha ao carregar o painel: " + e.getMessage()); }
        }, this::falha);
    }

    private void falha(String mensagem) {
        botaoEntrar.setDisable(false);
        botaoConexao.setDisable(false);
        aviso.setText(mensagem);
        senha.requestFocus();
    }
}
