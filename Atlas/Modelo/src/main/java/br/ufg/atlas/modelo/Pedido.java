package br.ufg.atlas.modelo;

import java.util.ArrayList;
import java.util.List;

public final class Pedido {
    private final Funcionario responsavel;
    private final ArrayList<ItemPedido> itens;
    private Comanda comanda;
    private String estado = "REGISTRADO";

    public Pedido(Funcionario responsavel, List<ItemPedido> itens) {
        if (responsavel == null || !responsavel.podeAtender()) {
            throw new IllegalStateException("Somente um garçom ativo pode lançar o pedido.");
        }
        if (itens == null || itens.isEmpty() || itens.size() > 100) {
            throw new IllegalArgumentException("Selecione entre 1 e 100 itens válidos.");
        }
        for (ItemPedido item : itens) {
            if (item == null) throw new IllegalArgumentException("Item de pedido ausente.");
        }
        this.responsavel = responsavel;
        this.itens = new ArrayList<>(itens);
    }

    public long calcularTotal() {
        long total = 0;
        for (ItemPedido item : itens) {
            total = Math.addExact(total, item.calcularSubtotal());
        }
        return total;
    }

    public void marcarPronto(Funcionario gerente) {
        if (gerente == null || !gerente.podeAdministrar()) {
            throw new IllegalStateException("A gerência controla o preparo.");
        }
        if (!"REGISTRADO".equals(estado)) {
            throw new IllegalStateException("O estado atual do pedido impede esta operação.");
        }
        estado = "PRONTO";
    }

    public void registrarEntrega(Funcionario garcom) {
        if (garcom == null || !garcom.podeAtender() || !garcom.getCodigo().equals(responsavel.getCodigo())) {
            throw new IllegalStateException("Somente o garçom responsável pode registrar a entrega.");
        }
        if (!"PRONTO".equals(estado)) {
            throw new IllegalStateException("O estado atual do pedido impede esta operação.");
        }
        estado = "ENTREGUE";
    }

    public List<ItemPedido> getItens() { return new ArrayList<>(itens); }

    void vincular(Comanda destino) {
        if (comanda != null) throw new IllegalStateException("O pedido já pertence a uma comanda.");
        comanda = destino;
    }

    public Comanda getComanda() { return comanda; }
    public Funcionario getResponsavel() { return responsavel; }
    public String getEstado() { return estado; }
}
