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
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Execute na raiz do projeto com ATLAS_TESTE_BANCO_URL e ATLAS_TESTE_BANCO_USUARIO definidos. */
public final class TesteFluxoHttp {
    private static final String PRODUTOS = "/produtos/importar";
    private static final String FUNCIONARIOS = "/funcionarios/importar";
    private static final String CSV_PRODUTOS = "nome;descricao;preco;categoria;setor;imagem;disponivel\r\n";
    private static final String CSV_FUNCIONARIOS = "username;name;role;schedule;password;active\n";
    private final HttpClient cliente = HttpClient.newHttpClient();
    private final String base;
    private final String senha;
    private final ConexaoBanco banco;
    private final Sessoes sessoes;
    private final ConcurrentLinkedQueue<String> avisos;

    private TesteFluxoHttp(int porta, String senha, ConexaoBanco banco, Sessoes sessoes,
            ConcurrentLinkedQueue<String> avisos) {
        base = "http://127.0.0.1:" + porta;
        this.senha = senha;
        this.banco = banco;
        this.sessoes = sessoes;
        this.avisos = avisos;
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
            Sessoes sessoes = new Sessoes(Clock.systemUTC());
            ConcurrentLinkedQueue<String> avisos = new ConcurrentLinkedQueue<>();
            servidor.createContext("/", new ApiHttp(isolado, sessoes, avisos::add));
            try {
                servidor.start();
                new TesteFluxoHttp(servidor.getAddress().getPort(), senha, isolado, sessoes, avisos).testar();
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
        for (JsonElement elemento : listar("/pedidos", gerente)) {
            JsonObject registro = elemento.getAsJsonObject();
            if (registro.get("estado").getAsString().equals("ENTREGUE")) continue;
            String caminho = "/pedidos/" + id(registro);
            enviar(caminho + "/pronto", gerente, "{}");
            enviar(caminho + "/entregar", registro.get("funcionarioCodigo").getAsString().equals("colega")
                    ? outro : garcom, "{}");
        }
        testarProdutosEmMassa(gerente, garcom, misto, pedido, bar, conta);
        testarFuncionariosEmMassa(gerente, garcom, outro, caixa, supervisao, conta, mesa);
        String novo = entrar("atendimento");
        enviar("/sair", novo, "{}");
        requisitar("GET", "/mesas", novo, null, 401);
    }

    private void testarProdutosEmMassa(String gerente, String garcom, String misto, int pedido, int bar, String conta)
            throws Exception {
        String bebida = "Café com leite;\"Bebida; com \"\"creme\"\"\r\ne canela\";8,50;Teste;BAR;"
                + "https://example.test/cafe.png;SIM\r\n";
        String prato = "Porção;;15.25;Teste;COZINHA;;NÃO\r\n";
        avisos.clear();
        requisitar("POST", PRODUTOS, garcom, CSV_PRODUTOS + bebida, 403);
        requisitar("POST", "/produtos/desativar-todos", garcom, "{}", 403);
        requisitar("POST", "/produtos/excluir-todos", garcom, "{}", 403);
        exigir(quantidade(enviar(PRODUTOS + "?validar=true", gerente, "\uFEFF" + CSV_PRODUTOS + bebida + prato)) == 2,
                "Prévia reconhece BOM, aspas, acentos, vírgula e registros");
        exigir(listar("/produtos", gerente).size() == 2 && avisos.isEmpty(), "Prévia não grava nem notifica");
        erroCsv(PRODUTOS, gerente, CSV_PRODUTOS + bebida + "A;;2,00;Ausente;BAR;;SIM", 400, 4, "categoria");
        erroCsv(PRODUTOS, gerente, CSV_PRODUTOS + "A;;2,00;Teste;BALCAO;;SIM", 400, 2, "setor");
        erroCsv(PRODUTOS, gerente, CSV_PRODUTOS + "A;;2,001;Teste;BAR;;SIM", 400, 2, "preco");
        erroCsv(PRODUTOS, gerente, CSV_PRODUTOS + "A;;0;Teste;BAR;;SIM", 400, 2, "Preço");
        erroCsv(PRODUTOS, gerente, CSV_PRODUTOS + "A;;2;Teste;BAR;http://example.test/a;SIM", 400, 2, "imagem");
        erroCsv(PRODUTOS, gerente, CSV_PRODUTOS + "A;;2;Teste;BAR;", 400, 2, "disponivel");
        erroCsv(PRODUTOS, gerente, CSV_PRODUTOS + "A;\"sem fechamento;2;Teste;BAR;;SIM", 400, 2, "descricao");
        requisitar("POST", PRODUTOS, gerente, "x".repeat(512 * 1024 + 1), 413);
        exigir(listar("/produtos", gerente).size() == 2 && avisos.isEmpty(), "CSV inválido não grava parcialmente");
        exigir(quantidade(enviar(PRODUTOS, gerente, "\uFEFF" + CSV_PRODUTOS + bebida + prato)) == 2,
                "Importação inteira confirmada");
        JsonObject cafe = localizar(listar("/produtos", gerente), "nome", "Café com leite");
        exigir(cafe.get("precoCentavos").getAsLong() == 850
                && cafe.get("descricao").getAsString().equals("Bebida; com \"creme\"\ne canela")
                && cafe.get("imagem").getAsString().startsWith("https://"), "Campos opcionais e preço persistidos");
        exigir(quantidade(enviar(PRODUTOS, gerente, CSV_PRODUTOS + bebida)) == 1
                && listar("/produtos", gerente).size() == 5, "Nome repetido cria outro produto");
        exigir(quantidade(enviar("/produtos/desativar-todos", gerente, "{}")) == 4
                && listar("/produtos", garcom).isEmpty() && listar("/produtos", gerente).size() == 5,
                "Desativar preserva os registros e retira o catálogo do garçom");
        exigir(quantidade(enviar("/produtos/excluir-todos", gerente, "{}")) == 5
                && listar("/produtos", gerente).isEmpty(), "Exclusão física de todos os produtos");
        exigir(id(enviar("/pedidos", garcom, misto)) == pedido, "Pedido continua idempotente após excluir produtos");
        JsonObject historico = consultar(conta, gerente).getAsJsonObject();
        exigir(historico.get("totalCentavos").getAsLong() == 5400
                && historico.getAsJsonArray("pedidos").size() == 3, "Histórico e totais da comanda intactos");
        try (Connection conexao = banco.abrir()) {
            JsonObject item = Consultas.um(conexao, "SELECT produto_id, categoria FROM itens_pedido "
                    + "WHERE pedido_id = ? AND produto_id_historico = ?", pedido, bar);
            exigir(item.get("produtoId").isJsonNull() && item.get("categoria").getAsString().equals("Teste"),
                    "Produto desvinculado, ID e categoria históricos preservados");
            exigir(Consultas.um(conexao, "SELECT id FROM categorias WHERE nome = 'Teste'") != null,
                    "Categoria preservada");
        }
        exigir(avisos.stream().filter("produtos"::equals).count() == 4, "Avisos apenas para operações aplicadas");
    }

    private void testarFuncionariosEmMassa(String gerente, String garcom, String outro, String caixa, String supervisao,
            String conta, int mesa) throws Exception {
        avisos.clear();
        String ana = "ana_csv;Ana Júlia;GARCOM;18h às 00h;" + senha + ";SIM\n";
        String bar = "bar_csv;João do Bar;BAR;;" + senha + ";SIM\n";
        String cozinha = "cozinha_csv;Maria;COZINHA;;" + senha + ";SIM\n";
        String novoCaixa = "caixa_csv;Pedro;CAIXA;;" + senha + ";NÃO\n";
        String novaGerente = "gerente_csv;Carla;GERENTE;;" + senha + ";SIM\n";
        String csv = CSV_FUNCIONARIOS + ana + bar + cozinha + novoCaixa + novaGerente;
        for (String rota : new String[]{FUNCIONARIOS, "/funcionarios/desativar-todos", "/funcionarios/excluir-todos"}) {
            requisitar("POST", rota, garcom, rota.equals(FUNCIONARIOS) ? csv : "{}", 403);
        }
        exigir(quantidade(enviar(FUNCIONARIOS + "?validar=true", gerente, csv)) == 5
                && listar("/funcionarios", gerente).size() == 5 && avisos.isEmpty(), "Prévia de funcionários sem efeitos");
        erroCsv(FUNCIONARIOS, gerente, CSV_FUNCIONARIOS + ana + ana, 409, 3, "username");
        erroCsv(FUNCIONARIOS, gerente, CSV_FUNCIONARIOS + ana + "atendimento;Outro;GARCOM;;" + senha + ";SIM",
                409, 3, "username");
        erroCsv(FUNCIONARIOS, gerente, CSV_FUNCIONARIOS + ana + "a;Outro;GARCOM;;curta;SIM", 400, 3, "senha");
        erroCsv(FUNCIONARIOS, gerente, CSV_FUNCIONARIOS + "a;Outro;GARCOM;;" + "x".repeat(129) + ";SIM",
                400, 2, "senha");
        erroCsv(FUNCIONARIOS, gerente, CSV_FUNCIONARIOS + "a;Outro;OUTRO;;" + senha + ";SIM", 400, 2, "cargo");
        erroCsv(FUNCIONARIOS, gerente, CSV_FUNCIONARIOS + "a.b;Outro;GARCOM;;" + senha + ";SIM", 400, 2, "Código");
        erroCsv(FUNCIONARIOS, gerente, CSV_FUNCIONARIOS + "a;;GARCOM;;" + senha + ";SIM", 400, 2, "nome");
        erroCsv(FUNCIONARIOS, gerente, CSV_FUNCIONARIOS + "a;Outro;GARCOM;;" + senha + ";TALVEZ", 400, 2, "active");
        exigir(listar("/funcionarios", gerente).size() == 5 && avisos.isEmpty(), "Importação de funcionários atômica");
        exigir(quantidade(enviar(FUNCIONARIOS, gerente, csv)) == 5
                && "funcionarios".equals(avisos.poll()), "Cinco cargos importados e notificados");
        JsonArray equipe = listar("/funcionarios", gerente);
        JsonObject registro = localizar(equipe, "codigo", "ana_csv");
        exigir(registro.get("expediente").getAsString().equals("18h às 00h") && !equipe.toString().contains(senha)
                && !registro.has("senhaHash"), "Expediente preservado e credenciais omitidas");
        exigir(localizar(equipe, "codigo", "bar_csv").get("cargo").getAsString().equals("BARMAN")
                && localizar(equipe, "codigo", "cozinha_csv").get("cargo").getAsString().equals("COZINHEIRO"),
                "Apelidos do CSV usam cargos existentes");
        String atendente = entrar("ana_csv");
        String novaSessaoGerente = entrar("gerente_csv");
        requisitar("POST", "/login", null, login("caixa_csv").toString(), 401);
        requisitar("POST", "/login", null, login("bar_csv").toString(), 403);
        requisitar("POST", "/login", null, login("cozinha_csv").toString(), 403);
        requisitar("PUT", "/funcionarios/ana_csv", gerente,
                "{\"nome\":\"Ana Atualizada\",\"cargo\":\"GARCOM\",\"ativo\":true}", 200);
        requisitar("GET", "/mesas", atendente, null, 401);
        atendente = entrar("ana_csv");
        exigir(localizar(listar("/funcionarios", gerente), "codigo", "ana_csv").get("expediente")
                .getAsString().equals("18h às 00h"), "Edição individual preserva expediente");

        int aberta = id(enviar("/comandas", atendente, "{\"mesaId\":" + mesa + "}"));
        int impressao = id(listar("/impressoes", gerente).asList().stream().filter(tarefa ->
                tarefa.getAsJsonObject().get("estado").getAsString().equals("PENDENTE")).findFirst().orElseThrow());
        String tarefa = "/impressoes/" + impressao;
        enviar(tarefa + "/reservar", supervisao, "{}");
        avisos.clear();
        String impedimento = requisitar("POST", "/funcionarios/excluir-todos", gerente, "{}", 409)
                .getAsJsonObject().get("erro").getAsString();
        exigir(impedimento.contains("Ana Atualizada (ana_csv)") && impedimento.contains("(supervisao)")
                && listar("/funcionarios", gerente).size() == 10 && avisos.isEmpty(),
                "Comanda e impressão em andamento impedem a exclusão inteira");
        enviar(tarefa + "/resultado", supervisao, "{\"estado\":\"ENVIADA\",\"tentativa\":1}");
        enviar("/comandas/" + aberta + "/solicitar", atendente, "{}");
        enviar("/comandas/" + aberta + "/pagar", caixa, "{\"valorCentavos\":0,\"forma\":\"PIX\"}");
        exigir(quantidade(enviar("/funcionarios/desativar-todos", gerente, "{}")) == 8,
                "Desativação mantém o gerente em uso, ignora quem já era inativo");
        exigir(listar("/funcionarios", gerente).size() == 10, "Desativação conserva os funcionários");
        for (String revogada : new String[]{garcom, outro, caixa, supervisao, atendente, novaSessaoGerente}) {
            try {
                sessoes.identificar(revogada);
                throw new AssertionError("Sessão permaneceu na memória");
            } catch (ErroHttp erro) {
                exigir(erro.getStatus() == 401, "Sessão revogada da memória");
            }
            requisitar("GET", "/mesas", revogada, null, 401);
        }
        exigir(quantidade(enviar("/funcionarios/excluir-todos", gerente, "{}")) == 9
                && listar("/funcionarios", gerente).size() == 1, "Exclusão física preserva o gerente responsável");
        JsonObject historico = consultar(conta, gerente).getAsJsonObject();
        exigir(historico.get("totalCentavos").getAsLong() == 5400
                && localizar(historico.getAsJsonArray("pedidos"), "funcionarioCodigo", "atendimento")
                    .get("funcionarioNome").getAsString().equals("Teste atendimento")
                && listar("/impressoes", gerente).size() == 4, "Histórico de comandas, pedidos e impressão preservado");
        try (Connection conexao = banco.abrir()) {
            for (String tabela : new String[]{"comandas", "pedidos", "pagamentos"}) {
                JsonObject antigo = Consultas.um(conexao, "SELECT funcionario_codigo, funcionario_codigo_historico, "
                        + "funcionario_nome_historico FROM " + tabela + " LIMIT 1");
                exigir(antigo.get("funcionarioCodigo").isJsonNull()
                        && !antigo.get("funcionarioCodigoHistorico").isJsonNull()
                        && !antigo.get("funcionarioNomeHistorico").isJsonNull(), "Vínculo histórico em " + tabela);
            }
            JsonObject tarefaAntiga = Consultas.um(conexao, "SELECT responsavel_codigo, confirmado_por, "
                    + "responsavel_nome_historico, confirmado_nome_historico FROM tarefas_impressao WHERE id = ?", impressao);
            exigir(tarefaAntiga.get("responsavelCodigo").isJsonNull() && tarefaAntiga.get("confirmadoPor").isJsonNull()
                    && !tarefaAntiga.get("responsavelNomeHistorico").isJsonNull()
                    && !tarefaAntiga.get("confirmadoNomeHistorico").isJsonNull(), "Histórico dos operadores de impressão");
        }
        exigir(avisos.stream().filter("funcionarios"::equals).count() == 2, "Notificações após desativação e exclusão");
        cadastrarFuncionario(gerente, "atendimento", "GARCOM");
        exigir(listar("/pedidos", entrar("atendimento")).isEmpty(), "Novo cadastro com mesmo código não herda pedidos");
        requisitar("GET", "/mesas", garcom, null, 401);
    }

    private void erroCsv(String rota, String token, String csv, int status, int numero, String campo) throws Exception {
        String erro = requisitar("POST", rota, token, csv, status).getAsJsonObject().get("erro").getAsString();
        exigir(erro.contains("Linha " + numero) && erro.contains(campo) && !erro.contains(senha),
                "Erro de CSV aponta a linha e o campo sem exibir a senha");
    }

    private static int quantidade(JsonElement resposta) {
        return resposta.getAsJsonObject().get("quantidade").getAsInt();
    }

    private static JsonObject localizar(JsonArray registros, String campo, String valor) {
        return registros.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(registro -> registro.get(campo).getAsString().equals(valor)).findFirst().orElseThrow();
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
        return enviar("/login", null, login(codigo).toString()).getAsJsonObject().get("token").getAsString();
    }

    private JsonObject login(String codigo) {
        JsonObject dados = new JsonObject();
        dados.addProperty("codigo", codigo);
        dados.addProperty("senha", senha);
        return dados;
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
            pedido.header("Content-Type", rota.contains("/importar") ? "text/csv; charset=utf-8" : "application/json");
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
