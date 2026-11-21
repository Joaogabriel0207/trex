package br.ufg.atlas.desktop;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.Window;

final class Operacoes {
    private static final int LIMITE_CSV = 512 * 1024;
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

    void importarCsv(String tipo) {
        FileChooser seletor = new FileChooser();
        seletor.setTitle("Importar CSV");
        seletor.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivos CSV (*.csv)", "*.csv"));
        File arquivo = seletor.showOpenDialog(janela);
        if (arquivo == null) return;
        ClienteHttp api = ambiente.api;
        String rota = "/" + tipo + "/importar";
        painel.executar(() -> {
            byte[] csv;
            try (InputStream entrada = Files.newInputStream(arquivo.toPath())) {
                csv = entrada.readNBytes(LIMITE_CSV + 1);
            }
            if (csv.length > LIMITE_CSV) throw new IOException("O arquivo CSV deve ter no máximo 512 KiB.");
            int quantidade = api.importarCsv(rota + "?validar=true", csv).get("quantidade").getAsInt();
            return new PreviaCsv(csv, quantidade);
        }, previa -> {
            String mensagem = "Confirma o cadastro de " + previa.quantidade() + " " + rotulo(tipo) + "?";
            if (tipo.equals("funcionarios")) {
                mensagem += "\n\nO CSV contém senhas iniciais. Guarde-o em local protegido.";
            }
            if (ambiente.confirmar("Importar CSV", mensagem, "Importar")) {
                Platform.runLater(() -> painel.executar(() -> api.importarCsv(rota, previa.dados()),
                        salvo -> resultadoEmMassa(salvo, "importado(s)", tipo)));
            }
        });
    }

    private record PreviaCsv(byte[] dados, int quantidade) {}

    void alterarTodos(String tipo, boolean excluir) {
        boolean funcionarios = tipo.equals("funcionarios");
        String titulo = excluir ? "Excluir todos" : "Desativar todos";
        String mensagem = excluir ? "Excluir definitivamente todos os " + rotulo(tipo) + "? O histórico será preservado."
                : "Desativar todos os " + rotulo(tipo) + " ativos? Os registros permanecerão cadastrados.";
        if (funcionarios) {
            mensagem += "\n\nSua conta de gerente permanecerá ativa.";
            if (excluir) mensagem += " Comandas e operações em andamento podem impedir a exclusão.";
            else mensagem += " As sessões das contas desativadas serão encerradas.";
        }
        if (!ambiente.confirmar(titulo, mensagem, excluir ? "Excluir definitivamente" : "Desativar")) return;
        ClienteHttp api = ambiente.api;
        String rota = "/" + tipo + (excluir ? "/excluir-todos" : "/desativar-todos");
        painel.executar(() -> api.alterar("POST", rota, new JsonObject()),
                salvo -> resultadoEmMassa(salvo, excluir ? "excluído(s)" : "desativado(s)", tipo));
    }

    private void resultadoEmMassa(JsonObject resposta, String acao, String tipo) {
        ambiente.mensagem("ATLAS", resposta.get("quantidade").getAsInt() + " registro(s) de "
                + rotulo(tipo) + " " + acao + ".");
    }

    private static String rotulo(String tipo) { return tipo.equals("funcionarios") ? "funcionários" : "produtos"; }

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
