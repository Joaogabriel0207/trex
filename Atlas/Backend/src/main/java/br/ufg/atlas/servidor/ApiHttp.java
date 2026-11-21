package br.ufg.atlas.servidor;

import br.ufg.atlas.modelo.Funcionario;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;

final class ApiHttp implements HttpHandler {
    private static final int LIMITE_CORPO = 128 * 1024;
    private final Gson json = new GsonBuilder().serializeNulls().setStrictness(Strictness.STRICT).create();
    private final ConexaoBanco banco;
    private final Sessoes sessoes;
    private final Consumer<String> avisar;

    ApiHttp(ConexaoBanco banco, Sessoes sessoes, Consumer<String> avisar) {
        this.banco = banco;
        this.sessoes = sessoes;
        this.avisar = avisar;
    }

    @Override
    public void handle(HttpExchange troca) throws IOException {
        int status = 200;
        JsonElement resposta;
        try {
            resposta = atender(troca);
        } catch (ErroHttp erro) {
            status = erro.getStatus();
            resposta = erro(erro.getMessage());
        } catch (JsonParseException erro) {
            status = 400;
            resposta = erro("Corpo JSON inválido.");
        } catch (IllegalArgumentException erro) {
            status = 400;
            resposta = erro(erro.getMessage());
        } catch (IllegalStateException erro) {
            status = 409;
            resposta = erro(erro.getMessage());
        } catch (SQLException falha) {
            ErroHttp erro = erroBanco(falha);
            status = erro.getStatus();
            resposta = erro(erro.getMessage());
        } catch (RuntimeException falha) {
            System.err.println("Falha no atendimento HTTP: " + falha.getClass().getSimpleName());
            status = 500;
            resposta = erro("Falha interna ao atender a operação.");
        }

        try (troca) {
            byte[] dados = json.toJson(resposta).getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            troca.getResponseHeaders().set("Cache-Control", "no-store");
            troca.sendResponseHeaders(status, dados.length);
            troca.getResponseBody().write(dados);
        }
    }

    private JsonElement atender(HttpExchange troca) throws IOException, SQLException {
        String metodo = troca.getRequestMethod();
        String caminho = troca.getRequestURI().getPath();
        if (caminho.equals("/login") && metodo.equals("POST")) return entrar(lerCorpo(troca));
        String cabecalho = troca.getRequestHeaders().getFirst("Authorization");
        if (cabecalho == null || !cabecalho.startsWith("Bearer ")) {
            throw new ErroHttp(401, "Entre para acessar o sistema.");
        }
        String token = cabecalho.substring(7);
        String codigo = sessoes.identificar(token);
        if (caminho.equals("/sair") && metodo.equals("POST")) {
            sessoes.encerrar(token);
            return new JsonObject();
        }
        String[] partes = caminho.substring(1).split("/", -1);
        JsonObject corpo = metodo.equals("GET") ? new JsonObject() : lerCorpo(troca);
        JsonElement resultado;
        try (Connection conexao = banco.abrir()) {
            conexao.setAutoCommit(false);
            try {
                Funcionario pessoa = BancoFuncionarios.buscarAtivo(conexao, codigo);
                if (pessoa == null) {
                    sessoes.encerrar(token);
                    throw new ErroHttp(401, "Funcionário inativo. Entre novamente.");
                }
                Permissoes.verificar(pessoa, metodo, partes);
                resultado = Dados.executar(conexao, pessoa, metodo, partes, corpo);
                conexao.commit();
            } catch (RuntimeException | SQLException falha) {
                conexao.rollback();
                throw falha;
            }
        }
        if (metodo.equals("PUT") && partes[0].equals("funcionarios")) sessoes.revogar(partes[1]);
        if (!metodo.equals("GET")) avisar.accept(partes[0]);
        return resultado;
    }

    private JsonObject entrar(JsonObject corpo) throws SQLException {
        String codigo = Campos.texto(corpo, "codigo", 32);
        String senha = Campos.senha(corpo);
        try (Connection conexao = banco.abrir()) {
            Funcionario pessoa = BancoFuncionarios.autenticar(conexao, codigo, senha);
            if (pessoa == null) throw new ErroHttp(401, "Código ou senha incorretos.");
            if (!pessoa.podeAtender() && !pessoa.podeReceber()) {
                throw new ErroHttp(403, "Este cargo ainda não possui acesso operacional.");
            }
            JsonObject resposta = new JsonObject();
            resposta.addProperty("codigo", pessoa.getCodigo());
            resposta.addProperty("nome", pessoa.getNome());
            resposta.addProperty("cargo", pessoa.getCargo().name());
            resposta.addProperty("token", sessoes.abrir(codigo));
            return resposta;
        }
    }

    private JsonObject lerCorpo(HttpExchange troca) throws IOException {
        byte[] dados = troca.getRequestBody().readNBytes(LIMITE_CORPO + 1);
        if (dados.length > LIMITE_CORPO) throw new ErroHttp(413, "Requisição muito grande.");
        if (dados.length == 0) return new JsonObject();
        JsonElement valor = json.fromJson(new String(dados, StandardCharsets.UTF_8), JsonElement.class);
        if (valor == null || !valor.isJsonObject()) throw new ErroHttp(400, "Envie um objeto JSON.");
        return valor.getAsJsonObject();
    }

    private static ErroHttp erroBanco(SQLException falha) {
        String estado = String.valueOf(falha.getSQLState());
        if (estado.equals("23505")) return new ErroHttp(409, "Já existe um registro com esta identificação.");
        if (estado.equals("40001") || estado.equals("40P01")) return new ErroHttp(503, "Operação concorrente. Tente novamente.");
        if (estado.startsWith("23") || estado.startsWith("22")) return new ErroHttp(400, "Confira os dados informados.");
        System.err.println("Falha JDBC. SQLState " + estado);
        if (estado.startsWith("08")) return new ErroHttp(503, "O banco de dados está indisponível.");
        return new ErroHttp(500, "Não foi possível concluir a operação no banco.");
    }

    private static JsonObject erro(String mensagem) {
        JsonObject resposta = new JsonObject();
        resposta.addProperty("erro", mensagem);
        return resposta;
    }
}
