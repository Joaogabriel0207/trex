package br.ufg.atlas.desktop;

import java.net.URI;
import java.util.prefs.Preferences;

final class Preferencias {
    private final Preferences dados = Preferences.userRoot().node("br/ufg/atlas/desktop/v2");

    String servidor() {
        return dados.get("servidor", "http://localhost:8080");
    }

    String impressora(String setor) {
        return dados.get("fila." + setor, "");
    }

    void salvar(String endereco, String bar, String cozinha) {
        URI uri = URI.create(endereco.trim()).normalize();
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getQuery() != null || !uri.getPath().matches("/?")) {
            throw new IllegalArgumentException("Informe um endereço HTTP ou HTTPS com o nome ou IP do servidor e sua porta.");
        }
        dados.put("servidor", uri.getScheme() + "://" + uri.getRawAuthority());
        dados.put("fila.BAR", bar == null ? "" : bar);
        dados.put("fila.COZINHA", cozinha == null ? "" : cozinha);
    }

    String enderecoMqtt() {
        String alternativo = System.getenv("ATLAS_MQTT_URL");
        if (alternativo != null && !alternativo.isBlank()) return alternativo;
        return "tcp://" + URI.create(servidor()).getHost() + ":1883";
    }
}
