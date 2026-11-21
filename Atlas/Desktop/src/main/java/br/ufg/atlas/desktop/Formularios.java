package br.ufg.atlas.desktop;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Window;

final class Formularios {
    private final FXMLLoader arquivo;
    private final Dialog<JsonObject> dialogo = new Dialog<>();
    private final ButtonType confirmar = new ButtonType("Confirmar", ButtonBar.ButtonData.OK_DONE);
    private JsonObject resultado;

    private Formularios(Window janela, String tela, String titulo) throws IOException {
        arquivo = AtlasDesktop.carregar(tela);
        Parent raiz = arquivo.load();
        dialogo.initOwner(janela);
        dialogo.setTitle(titulo);
        dialogo.getDialogPane().setContent(raiz);
        dialogo.getDialogPane().getStylesheets().add(AtlasDesktop.recurso("atlas.css").toExternalForm());
        dialogo.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE), confirmar);
    }

    private TextField campo(String nome) { return (TextField) arquivo.getNamespace().get(nome); }
    private CheckBox caixa(String nome) { return (CheckBox) arquivo.getNamespace().get(nome); }

    @SuppressWarnings("unchecked")
    private ComboBox<String> opcoes(String nome, String... valores) {
        ComboBox<String> lista = (ComboBox<String>) arquivo.getNamespace().get(nome);
        lista.getItems().setAll(valores);
        lista.setConverter(new javafx.util.StringConverter<String>() {
            @Override public String toString(String valor) { return valor == null ? "" : Dados.rotulo(valor); }
            @Override public String fromString(String texto) { return texto; }
        });
        lista.getSelectionModel().selectFirst();
        return lista;
    }

    private Optional<JsonObject> solicitar(Supplier<JsonObject> ler) {
        dialogo.setResultConverter(botao -> botao == confirmar ? resultado : null);
        dialogo.getDialogPane().lookupButton(confirmar).addEventFilter(ActionEvent.ACTION, evento -> {
            try { resultado = ler.get(); }
            catch (IllegalArgumentException | ArithmeticException e) {
                dialogo.setHeaderText(e.getMessage());
                evento.consume();
            }
        });
        return dialogo.showAndWait();
    }

    private String obrigatorio(String nome) {
        String valor = campo(nome).getText().trim();
        if (valor.isEmpty()) throw new IllegalArgumentException("Preencha o campo " + nome + ".");
        return valor;
    }

    static Optional<JsonObject> mesa(Window janela) throws IOException {
        Formularios tela = new Formularios(janela, "Mesa", "Cadastrar mesa");
        return tela.solicitar(() -> {
            int numero;
            try { numero = Integer.parseInt(tela.obrigatorio("numero")); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Informe um número inteiro de mesa."); }
            if (numero <= 0) throw new IllegalArgumentException("O número da mesa deve ser positivo.");
            JsonObject dados = new JsonObject();
            dados.addProperty("numero", numero);
            return dados;
        });
    }

    static Optional<JsonObject> produto(Window janela, JsonObject anterior) throws IOException {
        Formularios tela = new Formularios(janela, "Produto", anterior == null ? "Cadastrar produto" : "Editar produto");
        ComboBox<String> setor = tela.opcoes("setor", "BAR", "COZINHA");
        if (anterior != null) {
            tela.campo("nome").setText(Dados.texto(anterior, "nome"));
            tela.campo("categoria").setText(Dados.texto(anterior, "categoria"));
            tela.campo("preco").setText(BigDecimal.valueOf(anterior.get("precoCentavos").getAsLong(), 2)
                    .toPlainString().replace('.', ','));
            setor.setValue(Dados.texto(anterior, "setor"));
            tela.caixa("disponivel").setSelected(anterior.get("disponivel").getAsBoolean());
        }
        return tela.solicitar(() -> {
            JsonObject dados = new JsonObject();
            dados.addProperty("nome", tela.obrigatorio("nome"));
            dados.addProperty("categoria", tela.obrigatorio("categoria"));
            String informado = tela.obrigatorio("preco").replace(',', '.');
            if (!informado.matches("[0-9]{1,12}([.][0-9]{1,2})?")) {
                throw new IllegalArgumentException("Informe o preço em reais com até duas casas decimais.");
            }
            long centavos = new BigDecimal(informado).movePointRight(2).longValueExact();
            dados.addProperty("precoCentavos", centavos);
            dados.addProperty("setor", setor.getValue());
            dados.addProperty("disponivel", tela.caixa("disponivel").isSelected());
            return dados;
        });
    }

    static Optional<JsonObject> funcionario(Window janela, JsonObject anterior) throws IOException {
        Formularios tela = new Formularios(janela, "Funcionario", anterior == null ? "Cadastrar funcionário" : "Editar funcionário");
        ComboBox<String> cargo = tela.opcoes("cargo", "GARCOM", "MAITRE", "GERENTE", "SOMMELIER",
                "PADEIRO", "CAIXA", "BARMAN", "CUMIM", "COZINHEIRO", "AUXILIAR_LIMPEZA");
        if (anterior != null) {
            tela.campo("codigo").setText(Dados.texto(anterior, "codigo"));
            tela.campo("codigo").setEditable(false);
            tela.campo("nome").setText(Dados.texto(anterior, "nome"));
            cargo.setValue(Dados.texto(anterior, "cargo"));
            ((Label) tela.arquivo.getNamespace().get("rotuloSenha")).setText("Nova senha (opcional)");
            tela.caixa("ativo").setSelected(anterior.get("ativo").getAsBoolean());
        }
        return tela.solicitar(() -> {
            JsonObject dados = new JsonObject();
            if (anterior == null) dados.addProperty("codigo", tela.obrigatorio("codigo"));
            dados.addProperty("nome", tela.obrigatorio("nome"));
            dados.addProperty("cargo", cargo.getValue());
            dados.addProperty("ativo", tela.caixa("ativo").isSelected());
            String senha = tela.campo("senha").getText();
            if (!senha.isEmpty()) dados.addProperty("senha", senha);
            else if (anterior == null) throw new IllegalArgumentException("Informe a senha inicial.");
            return dados;
        });
    }

    static Optional<JsonObject> pagamento(Window janela, JsonObject conta) throws IOException {
        Formularios tela = new Formularios(janela, "Pagamento", "Receber e fechar conta");
        long total = conta.get("totalCentavos").getAsLong();
        Label resumo = (Label) tela.arquivo.getNamespace().get("resumo");
        resumo.setText("Mesa " + Dados.texto(conta, "mesaNumero") + " • Comanda #" + Dados.texto(conta, "id")
                + "\nReceber o total de " + Dados.reais(total) + " e liberar a mesa.");
        ComboBox<String> forma = tela.opcoes("forma", "DINHEIRO", "CARTAO", "PIX");
        return tela.solicitar(() -> {
            JsonObject dados = new JsonObject();
            dados.addProperty("forma", forma.getValue());
            dados.addProperty("valorCentavos", total);
            return dados;
        });
    }
}
