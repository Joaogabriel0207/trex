package br.ufg.atlas.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class ControleInicio {
    @FXML private Label ocupadas, pendentes, contas;
    @FXML private Button cartaoSalao, cartaoPedidos, cartaoFinanceiro;

    void preparar(Runnable abrirSalao, Runnable abrirPedidos, Runnable abrirFinanceiro) {
        cartaoSalao.setOnAction(evento -> abrirSalao.run());
        cartaoPedidos.setOnAction(evento -> abrirPedidos.run());
        cartaoFinanceiro.setOnAction(evento -> abrirFinanceiro.run());
    }

    int[] consultar(ClienteHttp api) throws Exception {
        JsonArray mesas = api.listar("/mesas");
        JsonArray pedidos = api.listar("/pedidos");
        int[] totais = new int[3];
        for (JsonElement mesa : mesas) {
            String estado = Dados.texto(mesa.getAsJsonObject(), "estado");
            if (!estado.equals("LIVRE")) totais[0]++;
            if (estado.equals("CONTA_SOLICITADA")) totais[2]++;
        }
        for (JsonElement pedido : pedidos) {
            if (!Dados.texto(pedido.getAsJsonObject(), "estado").equals("ENTREGUE")) totais[1]++;
        }
        return totais;
    }

    void exibir(int[] totais) {
        ocupadas.setText(String.valueOf(totais[0]));
        pendentes.setText(String.valueOf(totais[1]));
        contas.setText(String.valueOf(totais[2]));
    }
}
