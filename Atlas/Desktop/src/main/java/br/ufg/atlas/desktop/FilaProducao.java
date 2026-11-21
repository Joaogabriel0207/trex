package br.ufg.atlas.desktop;

import com.google.gson.JsonObject;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javax.print.PrintService;
import java.util.List;
import java.util.Optional;

final class FilaProducao {
    private final ControlePainel painel;
    private final Ambiente ambiente;

    FilaProducao(ControlePainel painel) {
        this.painel = painel;
        ambiente = painel.ambiente();
    }

    void imprimir(JsonObject tarefa) {
        String nome = ambiente.preferencias.impressora(Dados.texto(tarefa, "setor"));
        if (nome.isBlank()) {
            ambiente.mensagem("Impressora não configurada", "Selecione a fila do setor em Configurações.");
            return;
        }
        String titulo = "Pedido #" + Dados.texto(tarefa, "pedidoId") + " • " + Dados.rotulo(Dados.texto(tarefa, "setor"));
        Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION);
        confirmar.initOwner(ambiente.janela);
        confirmar.setTitle("Impressão");
        confirmar.setHeaderText(titulo);
        boolean repetir = Dados.texto(tarefa, "estado").equals("FALHA");
        confirmar.setContentText((repetir ? "Tentar novamente o envio para a fila " : "Enviar para a fila ") + nome + "?");
        ButtonType enviar = new ButtonType("Enviar", ButtonBar.ButtonData.OK_DONE);
        confirmar.getButtonTypes().setAll(ButtonType.CANCEL, enviar);
        if (confirmar.showAndWait().orElse(ButtonType.CANCEL) != enviar) return;
        String rota = "/impressoes/" + Dados.texto(tarefa, "id");
        ClienteHttp api = ambiente.api;
        painel.executar(() -> {
            PrintService fila = Impressao.localizar(nome);
            JsonObject reserva = new JsonObject();
            if (repetir) {
                reserva.addProperty("repetir", true);
                reserva.addProperty("tentativa", tarefa.get("tentativa").getAsInt());
            }
            int tentativa = api.alterar("POST", rota + "/reservar", reserva).get("tentativa").getAsInt();
            String estado = "ENVIADA";
            String detalhe = "Aceita pela fila " + nome;
            try {
                Impressao.enviar(fila, titulo, Dados.texto(tarefa, "texto"));
            } catch (Exception e) {
                estado = "INCERTA";
                detalhe = "A fila " + nome + " não confirmou o envio. Confira a fila antes de qualquer nova tentativa.";
            }
            try { resultado(api, rota, tentativa, estado, detalhe, false); }
            catch (Exception e) {
                throw new IllegalStateException("A tentativa começou. Confira a fila e use Registrar resultado no Desktop. " + e.getMessage(), e);
            }
            return detalhe;
        }, detalhe -> ambiente.mensagem(titulo, detalhe));
    }

    void conferir(JsonObject tarefa) {
        String rota = "/impressoes/" + Dados.texto(tarefa, "id");
        List<String> resultados = Dados.texto(tarefa, "estado").equals("INCERTA")
                ? List.of("Envio confirmado na fila", "Falha confirmada antes do envio")
                : List.of("Resultado desconhecido", "Envio confirmado na fila", "Falha confirmada antes do envio");
        ChoiceDialog<String> dialogo = new ChoiceDialog<>(null, resultados);
        dialogo.setSelectedItem(null);
        dialogo.getDialogPane().lookupButton(ButtonType.OK).disableProperty()
                .bind(dialogo.selectedItemProperty().isNull());
        dialogo.initOwner(ambiente.janela);
        dialogo.setTitle("Conferir impressão");
        dialogo.setHeaderText("Pedido #" + Dados.texto(tarefa, "pedidoId") + " • " + Dados.rotulo(Dados.texto(tarefa, "setor")));
        dialogo.setContentText("Resultado verificado na fila:");
        Optional<String> escolha = dialogo.showAndWait();
        if (escolha.isEmpty()) return;
        String estado = switch (escolha.get()) {
            case "Envio confirmado na fila" -> "ENVIADA";
            case "Falha confirmada antes do envio" -> "FALHA";
            default -> "INCERTA";
        };
        ClienteHttp api = ambiente.api;
        int tentativa = tarefa.get("tentativa").getAsInt();
        painel.executar(() -> resultado(api, rota, tentativa, estado, "Conferência manual: " + escolha.get(), true),
                salvo -> ambiente.mensagem("Conferência registrada", escolha.get()));
    }

    private static JsonObject resultado(ClienteHttp api, String rota, int tentativa, String estado, String detalhe, boolean conferido) throws Exception {
        JsonObject dados = new JsonObject();
        dados.addProperty("estado", estado);
        dados.addProperty("tentativa", tentativa);
        dados.addProperty("detalhe", detalhe);
        if (conferido) dados.addProperty("conferido", true);
        return api.alterar("POST", rota + "/resultado", dados);
    }
}
