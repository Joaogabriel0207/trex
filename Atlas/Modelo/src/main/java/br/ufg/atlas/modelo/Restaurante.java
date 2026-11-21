package br.ufg.atlas.modelo;

import java.util.ArrayList;

public final class Restaurante {
    private final ArrayList<Mesa> mesas = new ArrayList<>();
    private final Cardapio cardapio = new Cardapio();

    public Mesa cadastrarMesa(int numero) {
        if (buscarMesa(numero) != null) throw new IllegalArgumentException("Número de mesa já cadastrado.");
        Mesa mesa = new Mesa(numero);
        mesas.add(mesa);
        return mesa;
    }

    public Mesa buscarMesa(int numero) {
        for (Mesa mesa : mesas) {
            if (mesa.getNumero() == numero) return mesa;
        }
        return null;
    }

    public Cardapio getCardapio() { return cardapio; }
}
