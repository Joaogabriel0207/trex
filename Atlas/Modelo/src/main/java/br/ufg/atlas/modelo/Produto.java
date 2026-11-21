package br.ufg.atlas.modelo;

public final class Produto {
    private final long id;
    private final String nome;
    private final String categoria;
    private long precoCentavos;
    private final Setor setor;
    private boolean disponivel;

    public Produto(long id, String nome, String categoria, long precoCentavos, Setor setor, boolean disponivel) {
        if (id <= 0 || setor == null) {
            throw new IllegalArgumentException("Produto precisa de identificação e setor.");
        }
        this.id = id;
        this.nome = validarTexto(nome);
        this.categoria = validarTexto(categoria);
        alterarPreco(precoCentavos);
        this.setor = setor;
        this.disponivel = disponivel;
    }

    private String validarTexto(String texto) {
        if (texto == null || texto.isBlank() || texto.trim().length() > 120) {
            throw new IllegalArgumentException("Nome e categoria devem ter entre 1 e 120 caracteres.");
        }
        return texto.trim();
    }

    public void alterarPreco(long precoCentavos) {
        if (precoCentavos <= 0 || precoCentavos > 100_000_000) {
            throw new IllegalArgumentException("Preço fora da faixa permitida.");
        }
        this.precoCentavos = precoCentavos;
    }

    public void definirDisponibilidade(boolean disponivel) { this.disponivel = disponivel; }
    public long getId() { return id; }
    public String getNome() { return nome; }
    public String getCategoria() { return categoria; }
    public long getPrecoCentavos() { return precoCentavos; }
    public Setor getSetor() { return setor; }
    public boolean isDisponivel() { return disponivel; }
}
