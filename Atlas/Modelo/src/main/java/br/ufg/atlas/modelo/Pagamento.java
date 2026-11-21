package br.ufg.atlas.modelo;

public final class Pagamento {
    private final long valorCentavos;
    private final FormaPagamento forma;

    public Pagamento(long valorCentavos, FormaPagamento forma) {
        if (valorCentavos < 0 || forma == null) {
            throw new IllegalArgumentException("Informe um valor e uma forma de pagamento válidos.");
        }
        this.valorCentavos = valorCentavos;
        this.forma = forma;
    }

    public long getValorCentavos() { return valorCentavos; }
    public FormaPagamento getForma() { return forma; }
}
