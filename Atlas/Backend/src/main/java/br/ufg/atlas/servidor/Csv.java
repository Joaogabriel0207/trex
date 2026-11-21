package br.ufg.atlas.servidor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class Csv {
    record Linha(int numero, List<String> campos) {
        String campo(int indice) { return campos.get(indice); }

        boolean logico(int indice, String nome) {
            return switch (campo(indice).strip().toUpperCase(Locale.ROOT)) {
                case "SIM" -> true;
                case "NÃO", "NAO" -> false;
                default -> throw Campos.invalido(nome);
            };
        }

        ErroHttp erro(int status, String mensagem) {
            return new ErroHttp(status, "Linha " + numero + ": " + mensagem);
        }
    }

    private enum Estado { ABERTO, ENTRE_ASPAS, APOS_ASPAS }

    private Csv() { }

    static List<Linha> ler(String texto, int limite, String... cabecalho) {
        List<Linha> linhas = new ArrayList<>();
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        Estado estado = Estado.ABERTO;
        int numero = 1;
        int inicio = 1;
        int cursor = texto.startsWith("\uFEFF") ? 1 : 0;
        // A última quebra sintética permite tratar a linha final da mesma forma.
        for (; cursor <= texto.length(); cursor++) {
            char letra = cursor < texto.length() ? texto.charAt(cursor) : '\n';
            if (letra == '\r') {
                if (cursor + 1 < texto.length() && texto.charAt(cursor + 1) == '\n') cursor++;
                letra = '\n';
            }
            if (estado == Estado.ENTRE_ASPAS) {
                if (letra == '"') estado = Estado.APOS_ASPAS;
                else campo.append(letra);
                if (letra == '\n') numero++;
                continue;
            }
            if (letra == '"' && estado == Estado.APOS_ASPAS) {
                campo.append('"');
                estado = Estado.ENTRE_ASPAS;
            } else if (letra == ';' || letra == '\n') {
                campos.add(campo.toString());
                campo.setLength(0);
                if (letra == ';' && campos.size() >= cabecalho.length) {
                    throw erroCampo(numero, campos.size(), cabecalho, "há colunas a mais");
                }
                if (letra == '\n') {
                    if (campos.size() != 1 || !campos.get(0).isBlank() || estado == Estado.APOS_ASPAS) {
                        linhas.add(new Linha(inicio, List.copyOf(campos)));
                        if (linhas.size() > limite + 1) {
                            throw new ErroHttp(400, "O CSV aceita até " + limite + " registros.");
                        }
                    }
                    campos.clear();
                    inicio = ++numero;
                }
                estado = Estado.ABERTO;
            } else if (letra == '"' && campo.length() == 0 && estado == Estado.ABERTO) {
                estado = Estado.ENTRE_ASPAS;
            } else if (letra == '"' || estado == Estado.APOS_ASPAS) {
                throw erroCampo(numero, campos.size(), cabecalho, "aspas inválidas");
            } else {
                campo.append(letra);
            }
        }
        if (estado == Estado.ENTRE_ASPAS) {
            throw erroCampo(inicio, campos.size(), cabecalho, "aspas sem fechamento");
        }
        if (linhas.isEmpty() || !linhas.get(0).campos().equals(Arrays.asList(cabecalho))) {
            int linha = linhas.isEmpty() ? 1 : linhas.get(0).numero();
            throw new ErroHttp(400, "Linha " + linha + ": cabeçalho esperado: " + String.join(";", cabecalho) + ".");
        }
        if (linhas.size() == 1) throw new ErroHttp(400, "O CSV deve conter ao menos um registro.");
        List<Linha> registros = linhas.subList(1, linhas.size());
        for (Linha linha : registros) {
            if (linha.campos().size() < cabecalho.length) {
                throw erroCampo(linha.numero(), linha.campos().size(), cabecalho, "coluna ausente");
            }
        }
        return registros;
    }

    private static ErroHttp erroCampo(int numero, int indice, String[] cabecalho, String mensagem) {
        String campo = indice < cabecalho.length ? cabecalho[indice] : "após " + cabecalho[cabecalho.length - 1];
        return new ErroHttp(400, "Linha " + numero + ", campo " + campo + ": " + mensagem + ".");
    }
}
