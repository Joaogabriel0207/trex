package br.ufg.atlas.servidor;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class Sessoes {
    private record Sessao(String codigo, long validade) {}
    private final Map<String, Sessao> sessoes = new HashMap<>();
    private final Clock relogio;

    Sessoes(Clock relogio) { this.relogio = relogio; }

    synchronized String abrir(String codigo) {
        long agora = relogio.millis();
        sessoes.values().removeIf(sessao -> sessao.validade() <= agora);
        String token = UUID.randomUUID().toString();
        sessoes.put(token, new Sessao(codigo, agora + Duration.ofHours(8).toMillis()));
        return token;
    }

    synchronized String identificar(String token) {
        Sessao sessao = sessoes.get(token);
        if (sessao == null || sessao.validade() <= relogio.millis()) {
            sessoes.remove(token);
            throw new ErroHttp(401, "Sessão expirada ou inválida. Entre novamente.");
        }
        return sessao.codigo();
    }

    synchronized void encerrar(String token) { sessoes.remove(token); }

    synchronized void revogar(String codigo) {
        sessoes.values().removeIf(sessao -> sessao.codigo().equals(codigo));
    }
}
