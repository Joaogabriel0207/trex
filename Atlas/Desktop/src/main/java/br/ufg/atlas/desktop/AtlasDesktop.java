package br.ufg.atlas.desktop;

import java.io.IOException;
import java.net.URL;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class AtlasDesktop extends Application {
    private Ambiente ambiente;

    @Override
    public void start(Stage janela) throws IOException {
        Font.loadFont(recurso("Inter-Regular.ttf").toExternalForm(), 14);
        Font.loadFont(recurso("Inter-Bold.ttf").toExternalForm(), 14);
        ambiente = new Ambiente(janela);
        ambiente.login();
        janela.setTitle("ATLAS • Gerência do restaurante");
        janela.setMinWidth(820);
        janela.setMinHeight(580);
        janela.show();
    }

    static URL recurso(String nome) {
        URL url = AtlasDesktop.class.getResource("/desktop/" + nome);
        if (url == null) throw new IllegalStateException("Recurso do Desktop ausente: " + nome);
        return url;
    }

    static FXMLLoader carregar(String nome) {
        return new FXMLLoader(recurso(nome + ".fxml"));
    }

    static Scene cena(Parent raiz) {
        Scene cena = new Scene(raiz, 1080, 710);
        cena.getStylesheets().add(recurso("atlas.css").toExternalForm());
        return cena;
    }

    @Override
    public void stop() {
        if (ambiente != null) ambiente.encerrar();
    }

    public static void main(String[] argumentos) {
        launch(argumentos);
    }
}
