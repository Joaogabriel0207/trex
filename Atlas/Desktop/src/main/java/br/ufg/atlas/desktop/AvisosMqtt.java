package br.ufg.atlas.desktop;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

final class AvisosMqtt {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(tarefa -> {
        Thread thread = new Thread(tarefa, "atlas-desktop-avisos");
        thread.setDaemon(true);
        return thread;
    });
    private MqttClient cliente;
    private boolean inscrito;
    private final Preferencias configuracao;
    private final Runnable mudou;

    AvisosMqtt(Preferencias configuracao, Runnable mudou) {
        this.configuracao = configuracao;
        this.mudou = mudou;
    }

    void iniciar() {
        executor.scheduleWithFixedDelay(this::acompanhar, 0, 10, TimeUnit.SECONDS);
    }

    private void acompanhar() {
        try {
            if (cliente == null) {
                cliente = new MqttClient(configuracao.enderecoMqtt(), MqttClient.generateClientId(), new MemoryPersistence());
                cliente.setTimeToWait(5000);
            }
            if (!cliente.isConnected()) {
                inscrito = false;
                MqttConnectOptions opcoes = new MqttConnectOptions();
                opcoes.setConnectionTimeout(3);
                opcoes.setKeepAliveInterval(20);
                String usuario = System.getenv("ATLAS_MQTT_USUARIO");
                String senha = System.getenv("ATLAS_MQTT_SENHA");
                if (usuario != null) opcoes.setUserName(usuario);
                if (senha != null) opcoes.setPassword(senha.toCharArray());
                cliente.connect(opcoes);
            }
            if (!inscrito) {
                cliente.subscribe("atlas/eventos", 1, (topico, mensagem) -> mudou.run());
                inscrito = true;
                mudou.run();
            }
        } catch (MqttException | IllegalArgumentException e) {
            fecharCliente();
            // A próxima rodada reconecta. A consulta HTTP periódica segue disponível.
        }
    }

    void encerrar() {
        if (executor.isShutdown()) return;
        executor.execute(this::fecharCliente);
        executor.shutdown();
    }

    private void fecharCliente() {
        inscrito = false;
        if (cliente == null) return;
        try {
            // Também interrompe a tentativa que ainda aguarda confirmação do broker.
            cliente.disconnectForcibly(0, 500, false);
        } catch (MqttException e) {
            System.err.println("Falha ao desconectar a conexão de avisos MQTT.");
        } finally {
            try {
                cliente.close(true);
            } catch (MqttException e) {
                System.err.println("Falha ao encerrar a conexão de avisos MQTT.");
            }
            cliente = null;
        }
    }
}
