package br.ufg.atlas.modelo;

public final class ItemPedido {
    private final Produto produto;
    private final int quantidade;
    private final String observacao;
    private final long precoCentavos;

    public ItemPedido(Produto produto, int quantidade, String observacao) {
        if (produto == null || !produto.isDisponivel()) {
            throw new IllegalStateException("Produto indisponível para este pedido.");
        }
        if (quantidade < 1 || quantidade > 999) {
            throw new IllegalArgumentException("A quantidade deve estar entre 1 e 999.");
        }
        if (observacao != null && observacao.length() > 240) {
            throw new IllegalArgumentException("A observação comporta até 240 caracteres.");
        }
        this.produto = produto;
        this.quantidade = quantidade;
        this.observacao = observacao == null ? "" : observacao.trim();
        this.precoCentavos = produto.getPrecoCentavos();
    }

    public long calcularSubtotal() { return precoCentavos * quantidade; }
    public Produto getProduto() { return produto; }
    public String getNome() { return produto.getNome(); }
    public Setor getSetor() { return produto.getSetor(); }
    public int getQuantidade() { return quantidade; }
    public String getObservacao() { return observacao; }
    public long getPrecoCentavos() { return precoCentavos; }
}
