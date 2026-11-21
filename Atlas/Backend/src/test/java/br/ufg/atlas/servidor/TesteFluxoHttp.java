package br.ufg.atlas.servidor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Execute na raiz do projeto com ATLAS_TESTE_BANCO_URL e ATLAS_TESTE_BANCO_USUARIO definidos. */
public final class TesteFluxoHttp {
    private final HttpClient cliente = HttpClient.newHttpClient();
    private final String base;
    private final String senha;

    private TesteFluxoHttp(int porta, String senha) {
        base = "http://127.0.0.1:" + porta;
        this.senha = senha;
    }

    public static void main(String[] argumentos) throws Exception {
        String endereco = System.getenv("ATLAS_TESTE_BANCO_URL");
        String usuario = System.getenv("ATLAS_TESTE_BANCO_USUARIO");
        if (endereco == null || usuario == null) {
            System.out.println("Não executado. Configure ATLAS_TESTE_BANCO_URL e ATLAS_TESTE_BANCO_USUARIO.");
            return;
        }
        String senhaBanco = System.getenv("ATLAS_TESTE_BANCO_SENHA");
        String senha = UUID.randomUUID().toString();
        Path arquivo = Path.of("BancoDeDados", "esquema.sql");
        try (BancoDeTeste banco = new BancoDeTeste(endereco, usuario, senhaBanco)) {
            ConexaoBanco isolado = banco.preparar(arquivo, senha);
            HttpServer servidor = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            ExecutorService atendimento = Executors.newFixedThreadPool(2);
            servidor.setExecutor(atendimento);
            servidor.createContext("/", new ApiHttp(isolado, new Sessoes(Clock.systemUTC()), recurso -> {}));
            try {
                servidor.start();
                new TesteFluxoHttp(servidor.getAddress().getPort(), senha).testar();
            } finally {
                servidor.stop(0);
                atendimento.shutdownNow();
            }
        }
        System.out.println("Aprovado. Fluxo HTTP e JDBC concluído e esquema de teste removido.");
    }

    private void testar() throws Exception {
        requisitar("GET", "/mesas", null, null, 401);
        String gerente = entrar("gerencia");
        String garcom = cadastrarFuncionario(gerente, "atendimento", "GARCOM");
        String outro = cadastrarFuncionario(gerente, "colega", "GARCOM");
        String caixa = cadastrarFuncionario(gerente, "caixa", "CAIXA");
        String supervisao = cadastrarFuncionario(gerente, "supervisao", "GERENTE");
        requisitar("GET", "/funcionarios", garcom, null, 403);
        int mesa = id(enviar("/mesas", gerente, "{\"numero\":1}"));
        int bar = cadastrarProduto(gerente, "Suco", "BAR", 700);
        int cozinha = cadastrarProduto(gerente, "Sopa", "COZINHA", 1300);
        String mesaSelecionada = "{\"mesaId\":" + mesa + "}";
        int comanda = id(enviar("/comandas", garcom, mesaSelecionada));
        exigir(id(enviar("/comandas", outro, mesaSelecionada)) == comanda, "Uma comanda por mesa");

        String bebida = "{\"produtoId\":" + bar + ",\"quantidade\":2,\"observacao\":\"sem gelo\"}";
        String prato = "{\"produtoId\":" + cozinha + ",\"quantidade\":1}";
        UUID chave = UUID.randomUUID();
        String misto = pedido(comanda, chave, bebida + "," + prato);
        int pedido = enviarDuasVezes(garcom, misto);
        exigir(id(enviar("/pedidos", garcom, misto)) == pedido, "Reenvio retorna o mesmo pedido");
        requisitar("POST", "/pedidos", garcom, pedido(comanda, chave, prato), 409);
        enviar("/pedidos", garcom, pedido(comanda, UUID.randomUUID(), bebida));
        enviar("/pedidos", outro, pedido(comanda, UUID.randomUUID(), prato));
        exigir(listar("/pedidos", garcom).size() == 2, "Listagem limitada ao próprio garçom");
        exigir(listar("/pedidos", gerente).size() == 3, "Pedido misto e dois pedidos de setor único");
        requisitar("PUT", "/produtos/" + bar, gerente, produto("Suco atualizado", "COZINHA", 900), 200);
        verificarImpressao(gerente, supervisao, pedido);
        String entregas = "/pedidos/" + pedido;
        requisitar("POST", entregas + "/entregar", garcom, "{}", 409);
        enviar(entregas + "/pronto", gerente, "{}");
        requisitar("POST", entregas + "/entregar", outro, "{}", 409);
        enviar(entregas + "/entregar", garcom, "{}");

        String conta = "/comandas/" + comanda;
        enviar(conta + "/solicitar", garcom, "{}");
        requisitar("POST", "/pedidos", garcom, pedido(comanda, UUID.randomUUID(), prato), 409);
        JsonObject consumo = consultar(conta, caixa).getAsJsonObject();
        exigir(consumo.get("totalCentavos").getAsLong() == 5400, "Consumo conserva os preços originais");
        exigir(consumo.getAsJsonArray("pedidos").size() == 3, "Três pedidos na mesma comanda");
        String pagar = "{\"valorCentavos\":5400,\"forma\":\"PIX\"}";
        requisitar("POST", conta + "/pagar", garcom, pagar, 403);
        enviar(conta + "/pagar", caixa, pagar);
        exigir(id(enviar("/pedidos", garcom, misto)) == pedido, "Reenvio confirmado após encerrar a comanda");
        requisitar("POST", "/pedidos", garcom, pedido(comanda, UUID.randomUUID(), prato), 409);
        requisitar("POST", conta + "/pagar", caixa, pagar, 409);
        exigir(listar("/mesas", garcom).get(0).getAsJsonObject().get("estado").getAsString().equals("LIVRE"),
                "Mesa liberada pelo pagamento");
        exigir(consultar(conta, gerente).getAsJsonObject().get("estado").getAsString().equals("ENCERRADA"),
                "Histórico da comanda encerrada");
        requisitar("GET", conta, garcom, null, 404);
        enviar("/sair", garcom, "{}");
        requisitar("GET", "/mesas", garcom, null, 401);
    }

    private void verificarImpressao(String gerente, String supervisao, int pedido) throws Exception {
        JsonArray tarefas = listar("/impressoes", gerente);
        exigir(tarefas.size() == 4, "Uma tarefa por setor presente em cada pedido");
        int conferidas = 0;
        for (JsonElement elemento : tarefas) {
            JsonObject tarefa = elemento.getAsJsonObject();
            if (tarefa.get("pedidoId").getAsInt() != pedido) continue;
            boolean bar = tarefa.get("setor").getAsString().equals("BAR");
            String texto = tarefa.get("texto").getAsString();
            exigir(texto.contains("Suco") == bar && texto.contains("Sopa") != bar, "Separação do comprovante por setor");
            String caminho = "/impressoes/" + id(tarefa);
            JsonObject reserva = enviar(caminho + "/reservar", gerente, "{}").getAsJsonObject();
            exigir(reserva.get("tentativa").getAsInt() == 1, "Primeira reserva");
            requisitar("POST", caminho + "/reservar", supervisao, "{}", 409);
            if (bar) {
                String falha = "{\"estado\":\"FALHA\",\"detalhe\":\"Fila indisponível\",\"tentativa\":1}";
                enviar(caminho + "/resultado", gerente, falha);
                requisitar("POST", caminho + "/reservar", gerente, "{}", 409);
                JsonObject nova = enviar(caminho + "/reservar", gerente, "{\"repetir\":true,\"tentativa\":1}").getAsJsonObject();
                exigir(nova.get("tentativa").getAsInt() == 2, "Repetição manual de falha confirmada");
                requisitar("POST", caminho + "/resultado", gerente, falha, 409);
                enviar(caminho + "/resultado", gerente, "{\"estado\":\"ENVIADA\",\"tentativa\":2}");
            } else {
                enviar(caminho + "/resultado", gerente, "{\"estado\":\"INCERTA\",\"tentativa\":1}");
                requisitar("POST", caminho + "/reservar", gerente, "{\"repetir\":true,\"tentativa\":1}", 409);
                enviar(caminho + "/resultado", supervisao,
                        "{\"estado\":\"ENVIADA\",\"detalhe\":\"Conferência da fila de teste\",\"conferido\":true,\"tentativa\":1}");
            }
            requisitar("POST", caminho + "/reservar", gerente, "{}", 409);
            conferidas++;
        }
        exigir(conferidas == 2, "Pedido misto tem duas tarefas");
    }

    private String entrar(String codigo) throws Exception {
        JsonObject dados = new JsonObject();
        dados.addProperty("codigo", codigo);
        dados.addProperty("senha", senha);
        return enviar("/login", null, dados.toString()).getAsJsonObject().get("token").getAsString();
    }

    private String cadastrarFuncionario(String gerente, String codigo, String cargo) throws Exception {
        JsonObject dados = new JsonObject();
        dados.addProperty("codigo", codigo);
        dados.addProperty("nome", "Teste " + codigo);
        dados.addProperty("cargo", cargo);
        dados.addProperty("senha", senha);
        dados.addProperty("ativo", true);
        enviar("/funcionarios", gerente, dados.toString());
        return entrar(codigo);
    }

    private static String produto(String nome, String setor, int preco) {
        JsonObject dados = new JsonObject();
        dados.addProperty("nome", nome);
        dados.addProperty("categoria", "Teste");
        dados.addProperty("precoCentavos", preco);
        dados.addProperty("setor", setor);
        dados.addProperty("disponivel", true);
        return dados.toString();
    }

    private int cadastrarProduto(String gerente, String nome, String setor, int preco) throws Exception {
        return id(enviar("/produtos", gerente, produto(nome, setor, preco)));
    }

    private static String pedido(int comanda, UUID chave, String itens) {
        return "{\"comandaId\":" + comanda + ",\"chave\":\"" + chave + "\",\"itens\":[" + itens + "]}";
    }

    private static int id(JsonElement elemento) { return elemento.getAsJsonObject().get("id").getAsInt(); }

    private static void exigir(boolean condicao, String etapa) {
        if (!condicao) throw new AssertionError(etapa);
    }

    private JsonElement consultar(String rota, String token) throws Exception {
        return requisitar("GET", rota, token, null, 200);
    }

    private JsonArray listar(String rota, String token) throws Exception {
        return consultar(rota, token).getAsJsonArray();
    }

    private JsonElement enviar(String rota, String token, String corpo) throws Exception {
        return requisitar("POST", rota, token, corpo, 200);
    }

    private int enviarDuasVezes(String token, String corpo) throws Exception {
        HttpRequest pedido = preparar("POST", "/pedidos", token, corpo);
        var primeira = cliente.sendAsync(pedido, HttpResponse.BodyHandlers.ofString());
        var segunda = cliente.sendAsync(pedido, HttpResponse.BodyHandlers.ofString());
        int idPrimeiro = id(lerResposta(primeira.get(), "POST", "/pedidos", 200));
        int idSegundo = id(lerResposta(segunda.get(), "POST", "/pedidos", 200));
        exigir(idPrimeiro == idSegundo, "Envios concorrentes retornam o mesmo pedido");
        return idPrimeiro;
    }

    private JsonElement requisitar(String metodo, String rota, String token, String corpo, int esperado)
            throws Exception {
        HttpRequest pedido = preparar(metodo, rota, token, corpo);
        return lerResposta(cliente.send(pedido, HttpResponse.BodyHandlers.ofString()), metodo, rota, esperado);
    }

    private HttpRequest preparar(String metodo, String rota, String token, String corpo) {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(base + rota)).timeout(Duration.ofSeconds(20));
        if (token != null) pedido.header("Authorization", "Bearer " + token);
        if (corpo == null) {
            pedido.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            pedido.header("Content-Type", "application/json");
            pedido.method(metodo, HttpRequest.BodyPublishers.ofString(corpo));
        }
        return pedido.build();
    }

    private JsonElement lerResposta(HttpResponse<String> resposta, String metodo, String rota, int esperado) {
        exigir(resposta.statusCode() == esperado, metodo + " " + rota + " retornou " + resposta.statusCode()
                + ", esperado " + esperado);
        return JsonParser.parseString(resposta.body());
    }
}
