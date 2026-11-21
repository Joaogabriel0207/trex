package br.ufg.atlas.mobile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class Api {
    static final class Recusa extends IOException {
        final int status;
        Recusa(int status, String mensagem) {
            super(mensagem);
            this.status = status;
        }
    }

    private final String base;
    private final String token;

    Api(String base, String token) {
        this.base = base;
        this.token = token;
    }

    JSONArray listar(String rota) throws IOException, JSONException {
        return new JSONArray(enviar(rota, null));
    }

    JSONObject obter(String rota) throws IOException, JSONException {
        return new JSONObject(enviar(rota, null));
    }

    JSONObject alterar(String rota, JSONObject dados) throws IOException, JSONException {
        return new JSONObject(enviar(rota, dados));
    }

    private String enviar(String rota, JSONObject dados) throws IOException {
        HttpURLConnection conexao = (HttpURLConnection) new URL(base + rota).openConnection();
        try {
            conexao.setConnectTimeout(6000);
            conexao.setReadTimeout(10000);
            conexao.setInstanceFollowRedirects(false);
            conexao.setRequestProperty("Accept", "application/json");
            if (!token.isEmpty()) conexao.setRequestProperty("Authorization", "Bearer " + token);
            if (dados != null) {
                byte[] corpo = dados.toString().getBytes(StandardCharsets.UTF_8);
                conexao.setRequestMethod("POST");
                conexao.setDoOutput(true);
                conexao.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conexao.setFixedLengthStreamingMode(corpo.length);
                try (OutputStream saida = conexao.getOutputStream()) {
                    saida.write(corpo);
                }
            }
            int status = conexao.getResponseCode();
            if (status < 200 || status >= 300) {
                String mensagem = "O servidor recusou a operação (HTTP " + status + ").";
                String resposta = ler(conexao.getErrorStream());
                try {
                    mensagem = new JSONObject(resposta).optString("erro", mensagem);
                } catch (JSONException ignorado) {
                    // Servidores intermediários também podem responder com texto.
                }
                throw new Recusa(status, mensagem);
            }
            return ler(conexao.getInputStream());
        } finally {
            conexao.disconnect();
        }
    }

    private String ler(InputStream entrada) throws IOException {
        if (entrada == null) return "";
        try (BufferedReader leitor = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
            StringBuilder texto = new StringBuilder();
            String linha;
            while ((linha = leitor.readLine()) != null) texto.append(linha);
            return texto.toString();
        }
    }
}
