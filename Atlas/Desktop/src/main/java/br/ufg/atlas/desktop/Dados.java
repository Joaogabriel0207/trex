package br.ufg.atlas.desktop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class Dados {
    private static final Locale BRASIL = Locale.forLanguageTag("pt-BR");

    static String texto(JsonObject dados, String campo) {
        JsonElement valor = dados.get(campo);
        return valor == null || valor.isJsonNull() ? "" : valor.getAsString();
    }

    static String reais(long centavos) {
        return NumberFormat.getCurrencyInstance(BRASIL).format(BigDecimal.valueOf(centavos, 2));
    }

    static String rotulo(String codigo) {
        return switch (codigo) {
            case "GARCOM" -> "Garçom";
            case "MAITRE" -> "Maître";
            case "AUXILIAR_LIMPEZA" -> "Auxiliar de limpeza";
            case "CONTA_SOLICITADA" -> "Conta solicitada";
            case "EM_TENTATIVA" -> "Em tentativa";
            case "CARTAO" -> "Cartão";
            case "PIX" -> "Pix";
            case "true" -> "Sim";
            case "false" -> "Não";
            case "" -> "—";
            default -> codigo.substring(0, 1).toUpperCase(BRASIL) + codigo.substring(1).toLowerCase(BRASIL);
        };
    }

    static String dataLocal(String data) {
        try {
            return OffsetDateTime.parse(data).atZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (java.time.format.DateTimeParseException e) {
            return data;
        }
    }

    static String itens(JsonObject pedido) {
        StringBuilder texto = new StringBuilder();
        for (JsonElement elemento : pedido.getAsJsonArray("itens")) {
            JsonObject item = elemento.getAsJsonObject();
            long preco = item.get("precoCentavos").getAsLong();
            int quantidade = item.get("quantidade").getAsInt();
            texto.append(quantidade).append(" × ").append(texto(item, "nome"))
                    .append("  •  ").append(reais(preco)).append(" cada  •  ")
                    .append(reais(preco * quantidade)).append("\n");
            String observacao = texto(item, "observacao");
            if (!observacao.isEmpty()) texto.append("    ").append(observacao).append("\n");
        }
        return texto.toString();
    }

    static String consumo(JsonObject comanda) {
        StringBuilder resumo = new StringBuilder();
        for (JsonElement elemento : comanda.getAsJsonArray("pedidos")) {
            JsonObject pedido = elemento.getAsJsonObject();
            resumo.append("Pedido #").append(texto(pedido, "id")).append(" • ")
                    .append(rotulo(texto(pedido, "estado"))).append("\n")
                    .append(itens(pedido)).append("\n");
        }
        return resumo.append("Total da comanda: ").append(reais(comanda.get("totalCentavos").getAsLong())).toString();
    }
}
