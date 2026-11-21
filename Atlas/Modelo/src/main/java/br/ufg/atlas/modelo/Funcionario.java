package br.ufg.atlas.modelo;

public final class Funcionario {
    private final String codigo;
    private final String nome;
    private final Cargo cargo;
    private final boolean ativo;

    public Funcionario(String codigo, String nome, Cargo cargo, boolean ativo) {
        if (codigo == null || !codigo.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Código de funcionário inválido.");
        }
        if (nome == null || nome.isBlank() || nome.trim().length() > 120 || cargo == null) {
            throw new IllegalArgumentException("Informe nome e cargo válidos.");
        }
        this.codigo = codigo;
        this.nome = nome.trim();
        this.cargo = cargo;
        this.ativo = ativo;
    }

    public boolean podeAdministrar() { return ativo && cargo == Cargo.GERENTE; }
    public boolean podeAtender() { return ativo && cargo == Cargo.GARCOM; }
    public boolean podeReceber() { return ativo && (cargo == Cargo.GERENTE || cargo == Cargo.CAIXA); }
    public String getCodigo() { return codigo; }
    public String getNome() { return nome; }
    public Cargo getCargo() { return cargo; }
    public boolean isAtivo() { return ativo; }
}
