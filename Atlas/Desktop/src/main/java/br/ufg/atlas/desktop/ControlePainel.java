package br.ufg.atlas.desktop;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class ControlePainel {
    @FXML private Label titulo, operador, aviso;
    @FXML private Button inicio, pedidos, salao, cardapio, funcionarios, financeiro, producao, historico, configuracoes;
    @FXML private StackPane area;
    private Ambiente ambiente;
    private Button atual;
    private ControleInicio resumo;
    private Secoes listagem;
    private AvisosMqtt notificacoes;
    private Timeline consultaPeriodica;
    private final PauseTransition agruparEventos = new PauseTransition(Duration.millis(250));
    private int revisao;
    private boolean consultando, operando, consultaPendente, encerrado;

    void preparar(Ambiente ambiente) throws IOException {
        this.ambiente = ambiente;
        operador.setText(Dados.texto(ambiente.funcionario, "nome") + "  •  "
                + Dados.rotulo(Dados.texto(ambiente.funcionario, "cargo")));
        boolean gerente = Dados.texto(ambiente.funcionario, "cargo").equals("GERENTE");
        for (Button botao : new Button[]{inicio, pedidos, cardapio, funcionarios, producao, configuracoes}) {
            botao.setVisible(gerente);
            botao.setManaged(gerente);
        }
        mostrar(gerente ? inicio : financeiro);
        agruparEventos.setOnFinished(evento -> atualizar());
        notificacoes = new AvisosMqtt(ambiente.preferencias, () -> Platform.runLater(() -> {
            if (!encerrado) agruparEventos.playFromStart();
        }));
        notificacoes.iniciar();
        consultaPeriodica = new Timeline(new KeyFrame(Duration.seconds(45), evento -> atualizar()));
        consultaPeriodica.setCycleCount(Timeline.INDEFINITE);
        consultaPeriodica.play();
    }

    @FXML private void navegar(ActionEvent evento) {
        navegarPara((Button) evento.getSource());
    }

    private void navegarPara(Button destino) {
        try { mostrar(destino); }
        catch (IOException e) { aviso.setText("Falha ao abrir a área: " + e.getMessage()); }
    }

    private void mostrar(Button botao) throws IOException {
        if (operando || atual == botao) return;
        revisao++;
        if (atual != null) atual.getStyleClass().remove("selecionado");
        atual = botao;
        atual.getStyleClass().add("selecionado");
        titulo.setText(botao.getText());
        FXMLLoader arquivo = AtlasDesktop.carregar(botao == inicio ? "Inicio" : "Listagem");
        Parent raiz = arquivo.load();
        area.getChildren().setAll(raiz);
        resumo = null;
        listagem = null;
        if (botao == inicio) {
            resumo = arquivo.getController();
            resumo.preparar(() -> navegarPara(salao), () -> navegarPara(pedidos), () -> navegarPara(financeiro));
        }
        else listagem = new Secoes(this, arquivo.getController(), botao.getId());
        consultando = false;
        consultaPendente = false;
        aviso.setText("");
        atualizar();
    }

    @FXML void atualizar() {
        if (encerrado) return;
        if (consultando || operando) { consultaPendente = true; return; }
        int origem = revisao;
        consultando = true;
        aviso.setText("Consultando o servidor…");
        ClienteHttp api = ambiente.api;
        Consumer<String> falha = erro -> {
            if (origem != revisao) return;
            terminarConsulta(erro);
        };
        if (resumo != null) {
            ControleInicio tela = resumo;
            ambiente.executar(() -> tela.consultar(api), totais -> {
                if (origem != revisao) return;
                tela.exibir(totais);
                terminarConsulta("");
            }, falha);
        } else {
            Secoes tela = listagem;
            String rota = tela.rota();
            ambiente.executar(() -> api.listar(rota), dados -> {
                if (origem != revisao) return;
                tela.preencher(dados);
                terminarConsulta("");
            }, falha);
        }
    }

    private void terminarConsulta(String mensagem) {
        consultando = false;
        aviso.setText(mensagem);
        if (consultaPendente) {
            consultaPendente = false;
            atualizar();
        }
    }

    <T> void executar(Callable<T> tarefa, Consumer<T> sucesso) {
        if (operando) return;
        operando = true;
        area.setDisable(true);
        ambiente.executar(tarefa, resultado -> {
            try { sucesso.accept(resultado); }
            finally { concluirAcao(); }
        }, erro -> {
            try { ambiente.mensagem("Operação não concluída", erro); }
            finally { concluirAcao(); }
        });
    }

    private void concluirAcao() {
        operando = false;
        area.setDisable(false);
        atualizar();
    }

    Ambiente ambiente() { return ambiente; }

    void trocarHistorico() {
        listagem.alternarHistorico();
        revisao++;
        consultando = false;
        atualizar();
    }

    @FXML private void configurar() { if (!operando) ambiente.configurar(true); }
    @FXML private void sair() { if (!operando) ambiente.sair(); }

    void encerrar() {
        encerrado = true;
        if (consultaPeriodica != null) consultaPeriodica.stop();
        agruparEventos.stop();
        if (notificacoes != null) notificacoes.encerrar();
    }
}
