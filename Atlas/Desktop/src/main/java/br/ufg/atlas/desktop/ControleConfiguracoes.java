package br.ufg.atlas.desktop;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ControleConfiguracoes {
    @FXML private TextField servidor;
    @FXML private VBox impressoras;
    @FXML private ComboBox<String> bar, cozinha;
    @FXML private Label aviso;
    private Ambiente ambiente;
    private Stage janela;

    void preparar(Ambiente ambiente, Stage janela, boolean gerencia) {
        this.ambiente = ambiente;
        this.janela = janela;
        servidor.setText(ambiente.preferencias.servidor());
        impressoras.setVisible(gerencia);
        impressoras.setManaged(gerencia);
        bar.setValue(ambiente.preferencias.impressora("BAR"));
        cozinha.setValue(ambiente.preferencias.impressora("COZINHA"));
        if (gerencia) {
            ambiente.executar(Impressao::filas, filas -> {
                bar.getItems().setAll(filas);
                cozinha.getItems().setAll(filas);
                if (filas.isEmpty()) aviso.setText("Nenhuma fila compatível instalada neste computador.");
            }, aviso::setText);
        }
    }

    @FXML private void salvar() {
        try {
            ambiente.preferencias.salvar(servidor.getText(), bar.getValue(), cozinha.getValue());
            janela.close();
        } catch (IllegalArgumentException e) {
            aviso.setText("Endereço inválido. Use http://nome-do-servidor:8080 ou HTTPS.");
        }
    }

    @FXML private void cancelar() { janela.close(); }
}
