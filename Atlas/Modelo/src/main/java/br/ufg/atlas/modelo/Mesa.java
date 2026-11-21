package br.ufg.atlas.modelo;

public final class Mesa {
    private final int numero;
    private Comanda comanda;

    public Mesa(int numero) {
        if (numero <= 0) throw new IllegalArgumentException("Informe um número de mesa positivo.");
        this.numero = numero;
    }

    public Comanda abrirComanda() {
        if (getComandaAberta() == null) comanda = new Comanda(this);
        return comanda;
    }

    public Comanda getComandaAberta() {
        if (comanda == null || "ENCERRADA".equals(comanda.getEstado())) return null;
        return comanda;
    }

    public int getNumero() { return numero; }
}
