package br.ufg.atlas.mobile;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.ArrayList;

final class Linhas extends BaseAdapter {
    static final class Linha {
        final String titulo;
        final String detalhe;
        final Runnable selecionar;
        Linha(String titulo, String detalhe, Runnable selecionar) {
            this.titulo = titulo;
            this.detalhe = detalhe;
            this.selecionar = selecionar;
        }
    }

    private final ArrayList<Linha> linhas;
    private final LayoutInflater inflador;
    private final boolean mesas;

    Linhas(Context contexto, ArrayList<Linha> linhas, boolean mesas) {
        inflador = LayoutInflater.from(contexto);
        this.linhas = linhas;
        this.mesas = mesas;
    }

    @Override public int getCount() { return linhas.size(); }
    @Override public Linha getItem(int posicao) { return linhas.get(posicao); }
    @Override public long getItemId(int posicao) { return posicao; }

    @Override public View getView(int posicao, View anterior, ViewGroup pai) {
        View vista = anterior == null ? inflador.inflate(mesas ? R.layout.mesa : R.layout.linha, pai, false) : anterior;
        TextView titulo = vista.findViewById(R.id.titulo);
        TextView detalhe = vista.findViewById(R.id.detalhe);
        Linha linha = getItem(posicao);
        titulo.setText(linha.titulo);
        detalhe.setText(linha.detalhe);
        if (mesas) {
            int cor = R.color.azul;
            if (linha.detalhe.equals("Livre")) cor = R.color.livre;
            if (linha.detalhe.equals("Conta solicitada")) cor = R.color.conta;
            detalhe.setTextColor(vista.getContext().getColor(cor));
        }
        return vista;
    }
}
