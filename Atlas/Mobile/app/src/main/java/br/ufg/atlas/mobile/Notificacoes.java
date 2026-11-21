package br.ufg.atlas.mobile;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

final class Notificacoes {
    interface Ouvinte {
        void conexao(boolean conectada);
        void atualizar();
    }

    private final ScheduledExecutorService fila = Executors.newSingleThreadScheduledExecutor();
    private final Configuracao configuracao;
    private final Ouvinte ouvinte;
    private MqttClient cliente;
    private volatile boolean encerrado;

    Notificacoes(Configuracao configuracao, Ouvinte ouvinte) {
        this.configuracao = configuracao;
        this.ouvinte = ouvinte;
        fila.scheduleWithFixedDelay(this::conectar, 0, 15, TimeUnit.SECONDS);
    }

    private void conectar() {
        if (encerrado || (cliente != null && cliente.isConnected())) return;
        try {
            if (cliente == null) {
                cliente = new MqttClient(configuracao.broker(), MqttClient.generateClientId(), new MemoryPersistence());
                cliente.setTimeToWait(5000);
                cliente.setCallback(new MqttCallback() {
                    @Override public void connectionLost(Throwable motivo) {
                        if (!encerrado) ouvinte.conexao(false);
                    }
                    @Override public void messageArrived(String topico, MqttMessage mensagem) {
                        if (!encerrado) ouvinte.atualizar();
                    }
                    @Override public void deliveryComplete(IMqttDeliveryToken entrega) {}
                });
            }
            MqttConnectOptions opcoes = new MqttConnectOptions();
            opcoes.setConnectionTimeout(4);
            opcoes.setKeepAliveInterval(25);
            if (!configuracao.usuarioMqtt().isEmpty()) {
                opcoes.setUserName(configuracao.usuarioMqtt());
                opcoes.setPassword(configuracao.senhaMqtt());
            }
            cliente.connect(opcoes);
            cliente.subscribe("atlas/eventos", 1);
            if (!encerrado) {
                ouvinte.conexao(true);
                ouvinte.atualizar();
            }
        } catch (MqttException | IllegalArgumentException erro) {
            fecharCliente();
            if (!encerrado) ouvinte.conexao(false);
        }
    }

    void encerrar() {
        encerrado = true;
        fila.execute(this::fecharCliente);
        fila.shutdown();
    }

    private void fecharCliente() {
        if (cliente == null) return;
        try {
            cliente.disconnectForcibly(0, 500, false);
        } catch (MqttException ignorado) {
            // A rede pode já ter sido interrompida.
        } finally {
            try { cliente.close(true); } catch (MqttException ignorado) { }
            cliente = null;
        }
    }
}
