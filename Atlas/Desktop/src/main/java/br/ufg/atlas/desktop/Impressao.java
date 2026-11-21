package br.ufg.atlas.desktop;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;

final class Impressao {
    static List<String> filas() {
        List<String> nomes = new ArrayList<>();
        for (PrintService fila : PrintServiceLookup.lookupPrintServices(DocFlavor.SERVICE_FORMATTED.PRINTABLE, null)) {
            nomes.add(fila.getName());
        }
        Collections.sort(nomes);
        return nomes;
    }

    static PrintService localizar(String nome) {
        for (PrintService fila : PrintServiceLookup.lookupPrintServices(DocFlavor.SERVICE_FORMATTED.PRINTABLE, null)) {
            if (fila.getName().equals(nome)) return fila;
        }
        throw new IllegalArgumentException("Fila indisponível. Confira a impressora do setor em Configurações.");
    }

    static void enviar(PrintService fila, String titulo, String comprovante) throws PrintException {
        Printable pagina = (grafico, papel, indice) -> desenhar(grafico, papel, indice, comprovante);
        DocPrintJob trabalho = fila.createPrintJob();
        HashPrintRequestAttributeSet atributos = new HashPrintRequestAttributeSet();
        atributos.add(new JobName(titulo, java.util.Locale.forLanguageTag("pt-BR")));
        trabalho.print(new SimpleDoc(pagina, DocFlavor.SERVICE_FORMATTED.PRINTABLE, null), atributos);
    }

    private static int desenhar(Graphics grafico, PageFormat papel, int pagina, String texto) {
        grafico.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 9));
        FontMetrics fonte = grafico.getFontMetrics();
        int largura = Math.max(1, (int) papel.getImageableWidth() / Math.max(1, fonte.charWidth('M')));
        List<String> linhas = new ArrayList<>();
        for (String linha : texto.split("\\R", -1)) {
            if (linha.isEmpty()) linhas.add("");
            for (int inicio = 0; inicio < linha.length(); inicio += largura) {
                linhas.add(linha.substring(inicio, Math.min(inicio + largura, linha.length())));
            }
        }
        int capacidade = Math.max(1, (int) papel.getImageableHeight() / fonte.getHeight());
        int primeira = pagina * capacidade;
        if (primeira >= linhas.size()) return Printable.NO_SUCH_PAGE;
        int x = (int) papel.getImageableX();
        int y = (int) papel.getImageableY() + fonte.getAscent();
        for (int i = primeira; i < linhas.size() && i < primeira + capacidade; i++) {
            grafico.drawString(linhas.get(i), x, y);
            y += fonte.getHeight();
        }
        return Printable.PAGE_EXISTS;
    }
}
