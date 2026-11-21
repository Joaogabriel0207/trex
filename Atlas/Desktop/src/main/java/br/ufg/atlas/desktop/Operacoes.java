package br.ufg.atlas.desktop;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javafx.stage.Window;

final class Operacoes {
    private final Ambiente ambiente;
    private final ControlePainel painel;
    private final Window janela;

    Operacoes(ControlePainel painel) {
        this.painel = painel;
        ambiente = painel.ambiente();
        janela = ambiente.janela;
    }

    void editar(String tipo, JsonObject anterior) {
        try {
            Optional<JsonObject> dados = switch (tipo) {
                case "mesas" -> Formularios.mesa(janela);
                case "produtos" -> Formularios.produto(janela, anterior);
                case "funcionarios" -> Formularios.funcionario(janela, anterior);
                default -> throw new IllegalArgumentException("Cadastro desconhecido.");
            };
            if (dados.isEmpty()) return;
            String rota = "/" + tipo;
            if (anterior != null) {
                String chave = Dados.texto(anterior, tipo.equals("funcionarios") ? "codigo" : "id");
                rota += "/" + URLEncoder.encode(chave, StandardCharsets.UTF_8).replace("+", "%20");
            }
            salvar(anterior == null ? "POST" : "PUT", rota, dados.get(), "Cadastro salvo.");
        } catch (IOException e) {
            ambiente.mensagem("Formulário indisponível", e.getMessage());
        }
    }

    void pagar(JsonObject linha) {
        try {
            Optional<JsonObject> dados = Formularios.pagamento(janela, linha);
            dados.ifPresent(valor -> salvar("POST", "/comandas/" + Dados.texto(linha, "id") + "/pagar",
                    valor, "Pagamento registrado. A mesa foi liberada."));
        } catch (IOException e) {
            ambiente.mensagem("Formulário indisponível", e.getMessage());
        }
    }

    void pronto(JsonObject linha) {
        salvar("POST", "/pedidos/" + Dados.texto(linha, "id") + "/pronto", new JsonObject(),
                "Pedido pronto para entrega.");
    }

    private void salvar(String metodo, String rota, JsonObject dados, String mensagem) {
        ClienteHttp api = ambiente.api;
        painel.executar(() -> api.alterar(metodo, rota, dados), salvo -> ambiente.mensagem("ATLAS", mensagem));
    }

    void comanda(String id) {
        ClienteHttp api = ambiente.api;
        painel.executar(() -> api.consultar("/comandas/" + id), dados ->
                ambiente.detalhar("Consumo", "Mesa " + Dados.texto(dados, "mesaNumero") + " • Comanda #"
                        + id + " • " + Dados.rotulo(Dados.texto(dados, "estado")), Dados.consumo(dados)));
    }

    void pedido(JsonObject dados) {
        String titulo = "Pedido #" + Dados.texto(dados, "id") + " • Mesa " + Dados.texto(dados, "mesaNumero");
        String texto = "Responsável: " + Dados.texto(dados, "funcionarioNome") + " ("
                + Dados.texto(dados, "funcionarioCodigo") + ")\n" + Dados.dataLocal(Dados.texto(dados, "criadoEm"))
                + " • " + Dados.rotulo(Dados.texto(dados, "estado")) + "\n\n" + Dados.itens(dados)
                + "\nTotal: " + Dados.reais(dados.get("totalCentavos").getAsLong());
        ambiente.detalhar("Detalhes do pedido", titulo, texto);
    }

    void comprovante(JsonObject tarefa) {
        String detalhe = Dados.texto(tarefa, "detalhe");
        String texto = Dados.texto(tarefa, "texto") + (detalhe.isEmpty() ? "" : "\n\nÚltima tentativa: " + detalhe);
        ambiente.detalhar("Comprovante de produção", "Pedido #" + Dados.texto(tarefa, "pedidoId")
                + " • " + Dados.rotulo(Dados.texto(tarefa, "setor")), texto);
    }
}
