package br.ufg.atlas.mobile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

final class Dados {
    private Dados() {}

    static JSONObject objeto(Object... pares) {
        JSONObject objeto = new JSONObject();
        try {
            for (int i = 0; i < pares.length; i += 2) {
                objeto.put((String) pares[i], pares[i + 1]);
            }
        } catch (JSONException erro) {
            throw new IllegalArgumentException("Dados inválidos.", erro);
        }
        return objeto;
    }

    static String reais(long centavos) {
        return NumberFormat.getCurrencyInstance(new Locale("pt", "BR")).format(BigDecimal.valueOf(centavos, 2));
    }

    static String estado(String valor) {
        switch (valor) {
            case "LIVRE": return "Livre";
            case "OCUPADA": return "Ocupada";
            case "ABERTA": return "Aberta";
            case "CONTA_SOLICITADA": return "Conta solicitada";
            case "ENCERRADA": return "Encerrada";
            case "REGISTRADO": return "Registrado";
            case "PRONTO": return "Pronto para entrega";
            case "ENTREGUE": return "Entregue";
            default: return valor;
        }
    }
}
