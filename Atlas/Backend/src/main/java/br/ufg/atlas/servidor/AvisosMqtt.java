package br.ufg.atlas.servidor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

final class AvisosMqtt implements AutoCloseable {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final MqttClient cliente;
    private final MqttConnectOptions opcoes = new MqttConnectOptions();
    private boolean falhaInformada;

    AvisosMqtt() throws MqttException {
        String endereco = System.getenv().getOrDefault("ATLAS_MQTT_URL", "tcp://localhost:1883");
        cliente = new MqttClient(endereco, "atlas-srv-" + UUID.randomUUID(), new MemoryPersistence());
        cliente.setTimeToWait(3000);
        opcoes.setConnectionTimeout(3);
        opcoes.setCleanSession(true);
        String usuario = System.getenv("ATLAS_MQTT_USUARIO");
        String senha = System.getenv("ATLAS_MQTT_SENHA");
        if (usuario != null) opcoes.setUserName(usuario);
        if (senha != null) opcoes.setPassword(senha.toCharArray());
    }

    void iniciar() {
        executor.scheduleWithFixedDelay(this::conectar, 0, 15, TimeUnit.SECONDS);
    }

    private synchronized void conectar() {
        if (executor.isShutdown() || cliente.isConnected()) return;
        try {
            cliente.connect(opcoes);
            falhaInformada = false;
            publicar("atualizacao");
        } catch (MqttException erro) {
            informarFalha();
            desconectar();
        }
    }

    void notificar(String recurso) {
        if (!cliente.isConnected() || executor.isShutdown()) return;
        try {
            executor.execute(() -> publicar(recurso));
        } catch (RejectedExecutionException encerrando) {
            // O encerramento pode começar entre a verificação e o agendamento.
        }
    }

    private synchronized void publicar(String recurso) {
        if (executor.isShutdown() || !cliente.isConnected()) return;
        try {
            cliente.publish("atlas/eventos", recurso.getBytes(StandardCharsets.UTF_8), 1, false);
        } catch (MqttException erro) {
            informarFalha();
            desconectar();
        }
    }

    private void informarFalha() {
        if (!falhaInformada) System.err.println("MQTT indisponível. Consultas e alterações continuam via HTTP.");
        falhaInformada = true;
    }

    @Override
    public synchronized void close() {
        executor.shutdownNow();
        desconectar();
        try {
            cliente.close(true);
        } catch (MqttException erro) {
            System.err.println("Não foi possível concluir o encerramento do MQTT.");
        }
    }

    private void desconectar() {
        try {
            // O Paho ainda pode estar aguardando a confirmação do broker após o timeout.
            cliente.disconnectForcibly(0, 1000, false);
        } catch (MqttException erro) {
            System.err.println("Não foi possível interromper a conexão MQTT.");
        }
    }
}
