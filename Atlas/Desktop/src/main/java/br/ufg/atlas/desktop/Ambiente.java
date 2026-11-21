package br.ufg.atlas.desktop;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Stage;

final class Ambiente {
    final Stage janela;
    final Preferencias preferencias = new Preferencias();
    private final ExecutorService rede = Executors.newSingleThreadExecutor(tarefa -> {
        Thread thread = new Thread(tarefa, "atlas-desktop-http");
        thread.setDaemon(true);
        return thread;
    });
    ClienteHttp api;
    JsonObject funcionario;
    private ControlePainel painel;
    private int sessao;

    Ambiente(Stage janela) { this.janela = janela; }

    void login() throws IOException {
        sessao++;
        if (painel != null) painel.encerrar();
        painel = null;
        funcionario = null;
        api = new ClienteHttp(preferencias.servidor());
        FXMLLoader arquivo = AtlasDesktop.carregar("Login");
        Parent raiz = arquivo.load();
        ControleLogin tela = arquivo.getController();
        tela.preparar(this);
        exibir(raiz);
    }

    void painel(JsonObject usuario) throws IOException {
        funcionario = usuario;
        FXMLLoader arquivo = AtlasDesktop.carregar("Painel");
        Parent raiz = arquivo.load();
        painel = arquivo.getController();
        exibir(raiz);
        painel.preparar(this);
    }

    private void exibir(Parent raiz) {
        if (janela.getScene() == null) janela.setScene(AtlasDesktop.cena(raiz));
        else janela.getScene().setRoot(raiz);
    }

    <T> void executar(Callable<T> tarefa, Consumer<T> sucesso, Consumer<String> erro) {
        int origem = sessao;
        rede.execute(() -> {
            try {
                T resultado = tarefa.call();
                Platform.runLater(() -> { if (origem == sessao) sucesso.accept(resultado); });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (origem != sessao) return;
                    if (e instanceof ClienteHttp.SessaoExpirada) {
                        voltarAoLogin();
                        mensagem("Sessão encerrada", "Entre novamente para continuar.");
                    } else {
                        erro.accept(e.getMessage() == null ? "Não foi possível concluir a operação." : e.getMessage());
                    }
                });
            }
        });
    }

    void sair() {
        ClienteHttp anterior = api;
        rede.execute(() -> {
            try { anterior.alterar("POST", "/sair", new JsonObject()); }
            catch (Exception e) { System.err.println("Backend indisponível ao encerrar a sessão."); }
        });
        voltarAoLogin();
    }

    private void voltarAoLogin() {
        try { login(); }
        catch (IOException e) { mensagem("Falha ao carregar o login", e.getMessage()); }
    }

    void configurar(boolean gerencia) {
        try {
            String anterior = preferencias.servidor();
            FXMLLoader arquivo = AtlasDesktop.carregar("Configuracoes");
            Parent raiz = arquivo.load();
            Stage dialogo = new Stage();
            dialogo.initOwner(janela);
            dialogo.initModality(Modality.WINDOW_MODAL);
            dialogo.setTitle(gerencia ? "Configurações do Desktop" : "Conexão ao Backend");
            Scene cena = new Scene(raiz);
            cena.getStylesheets().add(AtlasDesktop.recurso("atlas.css").toExternalForm());
            dialogo.setScene(cena);
            ControleConfiguracoes tela = arquivo.getController();
            tela.preparar(this, dialogo, gerencia);
            dialogo.showAndWait();
            if (!anterior.equals(preferencias.servidor())) {
                if (funcionario != null) sair();
                else api = new ClienteHttp(preferencias.servidor());
            }
        } catch (IOException e) {
            mensagem("Configurações indisponíveis", e.getMessage());
        }
    }

    void mensagem(String titulo, String texto) {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION);
        alerta.initOwner(janela);
        alerta.setTitle("ATLAS");
        alerta.setHeaderText(titulo);
        alerta.setContentText(texto);
        alerta.showAndWait();
    }

    boolean confirmar(String titulo, String texto, String acao) {
        ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType confirmar = new ButtonType(acao, ButtonBar.ButtonData.OK_DONE);
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION, texto, cancelar, confirmar);
        alerta.initOwner(janela);
        alerta.setTitle("ATLAS");
        alerta.setHeaderText(titulo);
        alerta.getDialogPane().getStylesheets().add(AtlasDesktop.recurso("atlas.css").toExternalForm());
        ((Button) alerta.getDialogPane().lookupButton(confirmar)).setDefaultButton(false);
        ((Button) alerta.getDialogPane().lookupButton(cancelar)).setDefaultButton(true);
        return alerta.showAndWait().orElse(cancelar) == confirmar;
    }

    void detalhar(String titulo, String descricao, String texto) {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION);
        alerta.initOwner(janela);
        alerta.setTitle(titulo);
        alerta.setHeaderText(descricao);
        TextArea leitura = new TextArea(texto);
        leitura.setEditable(false);
        leitura.setWrapText(true);
        leitura.setPrefSize(520, 320);
        alerta.getDialogPane().setContent(leitura);
        alerta.setResizable(true);
        alerta.showAndWait();
    }

    void encerrar() {
        if (painel != null) painel.encerrar();
        rede.shutdownNow();
    }
}
