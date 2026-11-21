package br.ufg.atlas.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class ClienteHttp {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final String endereco;
    private String token = "";

    ClienteHttp(String endereco) {
        this.endereco = endereco;
    }

    JsonObject entrar(String codigo, String senha) throws Exception {
        JsonObject dados = new JsonObject();
        dados.addProperty("codigo", codigo);
        dados.addProperty("senha", senha);
        JsonObject resposta = alterar("POST", "/login", dados);
        token = resposta.get("token").getAsString();
        return resposta;
    }

    JsonArray listar(String caminho) throws Exception {
        return requisitar("GET", caminho, null).getAsJsonArray();
    }

    JsonObject consultar(String caminho) throws Exception {
        return requisitar("GET", caminho, null).getAsJsonObject();
    }

    JsonObject alterar(String metodo, String caminho, JsonObject dados) throws Exception {
        return requisitar(metodo, caminho, dados).getAsJsonObject();
    }

    private JsonElement requisitar(String metodo, String caminho, JsonObject corpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(endereco + caminho))
                .timeout(Duration.ofSeconds(10)).header("Accept", "application/json");
        if (!token.isEmpty()) pedido.header("Authorization", "Bearer " + token);
        if (corpo != null) {
            pedido.header("Content-Type", "application/json; charset=utf-8")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(corpo.toString()));
        }
        HttpResponse<String> resposta;
        try {
            resposta = http.send(pedido.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IOException("Sem resposta do Backend. Verifique a conexão e atualize os dados.", e);
        }
        JsonElement dados;
        try {
            dados = JsonParser.parseString(resposta.body());
        } catch (RuntimeException e) {
            throw new IOException("Resposta inesperada do servidor (HTTP " + resposta.statusCode() + ").", e);
        }
        if (resposta.statusCode() / 100 != 2) {
            String erro = "Falha HTTP " + resposta.statusCode();
            if (dados.isJsonObject() && dados.getAsJsonObject().has("erro")) {
                erro = dados.getAsJsonObject().get("erro").getAsString();
            }
            if (resposta.statusCode() == 401 && !token.isEmpty()) throw new SessaoExpirada(erro);
            throw new IOException(erro);
        }
        return dados;
    }

    static final class SessaoExpirada extends IOException {
        SessaoExpirada(String mensagem) { super(mensagem); }
    }
}
