package br.ufg.atlas.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.EditText;
import java.net.URI;

final class Configuracao {
    private final SharedPreferences preferencias;
    private static String senhaMqtt = "";

    Configuracao(Context contexto) {
        preferencias = contexto.getSharedPreferences("rede", Context.MODE_PRIVATE);
    }

    String servidor() { return preferencias.getString("servidor", ""); }

    String broker() {
        String proprio = preferencias.getString("broker", "");
        if (!proprio.isEmpty()) return proprio;
        return "tcp://" + URI.create(servidor()).getHost() + ":1883";
    }

    String usuarioMqtt() { return preferencias.getString("usuario", ""); }
    char[] senhaMqtt() { return senhaMqtt.toCharArray(); }

    void editar(Activity tela) {
        View campos = tela.getLayoutInflater().inflate(R.layout.configuracao, null);
        EditText endereco = campos.findViewById(R.id.endereco);
        EditText broker = campos.findViewById(R.id.broker);
        EditText usuario = campos.findViewById(R.id.usuario_mqtt);
        EditText senha = campos.findViewById(R.id.senha_mqtt);
        endereco.setText(servidor());
        broker.setText(preferencias.getString("broker", ""));
        usuario.setText(usuarioMqtt());
        senha.setText(senhaMqtt);
        AlertDialog dialogo = new AlertDialog.Builder(tela).setTitle("Configurações de rede")
                .setView(campos).setNegativeButton("Cancelar", null).setPositiveButton("Salvar", null).create();
        dialogo.setOnShowListener(evento -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String base = endereco.getText().toString().trim();
            String mqtt = broker.getText().toString().trim();
            if (!valido(base, "http", "https")) {
                endereco.setError("Use o endereço HTTP ou HTTPS do computador na rede.");
                return;
            }
            if (!mqtt.isEmpty() && !valido(mqtt, "tcp", "ssl")) {
                broker.setError("Use tcp://servidor:porta ou ssl://servidor:porta.");
                return;
            }
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            if (mqtt.endsWith("/")) mqtt = mqtt.substring(0, mqtt.length() - 1);
            preferencias.edit().putString("servidor", base).putString("broker", mqtt)
                    .putString("usuario", usuario.getText().toString().trim()).apply();
            senhaMqtt = senha.getText().toString();
            dialogo.dismiss();
        }));
        dialogo.show();
    }

    private static boolean valido(String texto, String primeiro, String segundo) {
        try {
            URI endereco = URI.create(texto);
            String host = endereco.getHost();
            return (primeiro.equals(endereco.getScheme()) || segundo.equals(endereco.getScheme()))
                    && host != null && !host.equalsIgnoreCase("localhost") && !host.startsWith("127.")
                    && !host.equals("[::1]") && endereco.getPort() <= 65535 && endereco.getPort() != 0
                    && endereco.getUserInfo() == null && endereco.getQuery() == null && endereco.getFragment() == null
                    && (endereco.getPath().isEmpty() || endereco.getPath().equals("/"));
        } catch (IllegalArgumentException erro) {
            return false;
        }
    }
}
