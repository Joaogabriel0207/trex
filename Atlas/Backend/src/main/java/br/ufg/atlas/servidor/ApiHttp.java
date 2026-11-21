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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

final class ApiHttp implements HttpHandler {
    private static final int LIMITE_CORPO = 128 * 1024;
    private static final int LIMITE_CSV = 512 * 1024;
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
        boolean funcionarios = partes[0].equals("funcionarios");
        boolean massa = metodo.equals("POST") && partes.length == 2
                && (funcionarios || partes[0].equals("produtos"));
        boolean importar = massa && partes[1].equals("importar");
        String consulta = troca.getRequestURI().getRawQuery();
        boolean validar = importar && "validar=true".equals(consulta);
        if (importar && consulta != null && !validar) throw Campos.invalido("validar");
        String csv = importar ? lerCsv(troca) : null;
        JsonObject corpo = metodo.equals("GET") || importar ? new JsonObject() : lerCorpo(troca);
        JsonElement resultado;
        try (Connection conexao = banco.abrir()) {
            conexao.setAutoCommit(false);
            try {
                if (funcionarios && !metodo.equals("GET") && !validar) {
                    BancoFuncionarios.bloquearCadastro(conexao);
                }
                Funcionario pessoa = BancoFuncionarios.buscarAtivo(conexao, codigo);
                if (pessoa == null) {
                    sessoes.encerrar(token);
                    throw new ErroHttp(401, "Funcionário inativo. Entre novamente.");
                }
                // Uma alteração pode ter revogado o token enquanto aguardávamos o bloqueio no banco.
                sessoes.identificar(token);
                Permissoes.verificar(pessoa, metodo, partes);
                resultado = massa ? Dados.executarEmMassa(conexao, pessoa, partes, csv, validar)
                        : Dados.executar(conexao, pessoa, metodo, partes, corpo);
                if (funcionarios && resultado.isJsonArray() && massa) {
                    Set<String> revogados = new HashSet<>();
                    resultado.getAsJsonArray().forEach(linha ->
                            revogados.add(linha.getAsJsonObject().get("codigo").getAsString()));
                    confirmarRevogacao(conexao, revogados);
                    resultado = Dados.quantidade(revogados.size());
                } else if (funcionarios && metodo.equals("PUT")) {
                    confirmarRevogacao(conexao, Set.of(partes[1]));
                } else {
                    conexao.commit();
                }
            } catch (RuntimeException | SQLException falha) {
                conexao.rollback();
                throw falha;
            }
        }
        if (!metodo.equals("GET") && !validar) avisar.accept(partes[0]);
        return resultado;
    }

    private void confirmarRevogacao(Connection conexao, Set<String> codigos) throws SQLException {
        synchronized (sessoes) {
            conexao.commit();
            sessoes.revogarTodos(codigos);
        }
    }

    private JsonObject entrar(JsonObject corpo) throws SQLException {
        String codigo = Campos.texto(corpo, "codigo", 32);
        String senha = Campos.senha(corpo);
        try (Connection conexao = banco.abrir()) {
            conexao.setAutoCommit(false);
            Funcionario pessoa = BancoFuncionarios.autenticar(conexao, codigo, senha);
            if (pessoa == null) throw new ErroHttp(401, "Código ou senha incorretos.");
            if (!pessoa.podeAtender() && !pessoa.podeReceber()) {
                throw new ErroHttp(403, "Este cargo ainda não possui acesso operacional.");
            }
            JsonObject resposta = new JsonObject();
            resposta.addProperty("codigo", pessoa.getCodigo());
            resposta.addProperty("nome", pessoa.getNome());
            resposta.addProperty("cargo", pessoa.getCargo().name());
            // Registra o token enquanto a conta continua bloqueada contra desativação ou exclusão.
            synchronized (sessoes) {
                conexao.commit();
                resposta.addProperty("token", sessoes.abrir(codigo));
            }
            return resposta;
        }
    }

    private static String lerCsv(HttpExchange troca) throws IOException {
        byte[] dados = troca.getRequestBody().readNBytes(LIMITE_CSV + 1);
        if (dados.length > LIMITE_CSV) throw new ErroHttp(413, "O arquivo CSV deve ter no máximo 512 KiB.");
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(dados)).toString();
        } catch (CharacterCodingException erro) {
            throw new ErroHttp(400, "Salve o arquivo CSV com codificação UTF-8.");
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
