package br.ufg.atlas.servidor;

import com.sun.net.httpserver.HttpServer;
import java.io.Console;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Servidor {
    private Servidor() {}

    public static void main(String[] argumentos) throws Exception {
        ConexaoBanco banco = new ConexaoBanco();
        if (argumentos.length > 0) {
            if (argumentos.length != 1 || !"criar-gerente".equals(argumentos[0])) {
                throw new IllegalArgumentException("Use criar-gerente para cadastrar o primeiro acesso.");
            }
            criarGerente(banco);
            return;
        }
        int porta = Integer.parseInt(System.getenv().getOrDefault("ATLAS_HTTP_PORTA", "8080"));
        String interfaceRede = System.getenv().getOrDefault("ATLAS_HTTP_HOST", "0.0.0.0");
        HttpServer servidor = HttpServer.create(new InetSocketAddress(interfaceRede, porta), 32);
        AvisosMqtt avisos = new AvisosMqtt();
        ExecutorService atendimento = Executors.newFixedThreadPool(4);
        servidor.createContext("/", new ApiHttp(banco, new Sessoes(Clock.systemUTC()), avisos::notificar));
        servidor.setExecutor(atendimento);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            servidor.stop(1);
            atendimento.shutdownNow();
            avisos.close();
        }));
        avisos.iniciar();
        servidor.start();
        System.out.println("ATLAS HTTP em " + interfaceRede + ":" + servidor.getAddress().getPort());
    }

    private static void criarGerente(ConexaoBanco banco) throws SQLException {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Abra um terminal interativo e execute a classe Servidor com criar-gerente.");
        }
        String codigo = console.readLine("Código do primeiro gerente: ");
        String nome = console.readLine("Nome: ");
        char[] senha = console.readPassword("Senha (8 a 128 caracteres): ");
        if (senha == null) throw new IllegalArgumentException("Cadastro interrompido.");
        try (Connection conexao = banco.abrir()) {
            conexao.setAutoCommit(false);
            try {
                BancoFuncionarios.criarPrimeiroGerente(conexao, codigo, nome, new String(senha));
                conexao.commit();
                console.printf("Gerente cadastrado.%n");
            } catch (RuntimeException | SQLException erro) {
                conexao.rollback();
                throw erro;
            }
        } finally {
            Arrays.fill(senha, '\0');
        }
    }
}
