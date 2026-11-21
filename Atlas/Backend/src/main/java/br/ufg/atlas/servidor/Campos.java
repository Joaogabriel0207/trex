package br.ufg.atlas.servidor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.UUID;

final class Campos {
    private Campos() { }

    static String texto(JsonObject corpo, String campo, int limite) {
        String valor = cadeia(corpo, campo);
        if (valor.isBlank() || valor.length() > limite) {
            throw invalido(campo);
        }
        return valor;
    }

    static String observacao(JsonObject corpo, String campo) {
        if (!corpo.has(campo)) return "";
        String valor = cadeia(corpo, campo);
        if (valor.length() > 500) throw invalido(campo);
        return valor;
    }

    private static String cadeia(JsonObject corpo, String campo) {
        JsonElement valor = corpo.get(campo);
        if (valor == null || !valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isString()) {
            throw invalido(campo);
        }
        String texto = valor.getAsString().trim();
        if (texto.indexOf(0) >= 0) throw invalido(campo);
        return texto;
    }

    static String senha(JsonObject corpo) {
        JsonElement valor = corpo.get("senha");
        if (valor == null || !valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isString()
                || valor.getAsString().isBlank()) throw invalido("senha");
        return valor.getAsString();
    }

    static long inteiro(JsonObject corpo, String campo) {
        JsonElement valor = corpo.get(campo);
        if (valor == null || !valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isNumber()) {
            throw invalido(campo);
        }
        try {
            return valor.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException erro) {
            throw invalido(campo);
        }
    }

    static int positivo(JsonObject corpo, String campo) {
        long numero = inteiro(corpo, campo);
        if (numero < 1 || numero > Integer.MAX_VALUE) throw invalido(campo);
        return (int) numero;
    }

    static int identificador(String texto) {
        try {
            int numero = Integer.parseInt(texto);
            if (numero > 0) return numero;
        } catch (NumberFormatException erro) {
            throw invalido("id");
        }
        throw invalido("id");
    }

    static boolean logico(JsonObject corpo, String campo) {
        JsonElement valor = corpo.get(campo);
        if (valor == null || !valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isBoolean()) {
            throw invalido(campo);
        }
        return valor.getAsBoolean();
    }

    static UUID chave(JsonObject corpo) {
        String texto = texto(corpo, "chave", 36);
        try {
            UUID chave = UUID.fromString(texto);
            if (chave.toString().equalsIgnoreCase(texto)) return chave;
        } catch (IllegalArgumentException erro) {
            throw invalido("chave");
        }
        throw invalido("chave");
    }

    static ErroHttp invalido(String campo) {
        return new ErroHttp(400, "Verifique o campo " + campo + ".");
    }
}
