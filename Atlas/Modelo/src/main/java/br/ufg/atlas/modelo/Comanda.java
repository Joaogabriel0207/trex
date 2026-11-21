package br.ufg.atlas.modelo;

import java.util.ArrayList;
import java.util.List;

public final class Comanda {
    private final Mesa mesa;
    private final ArrayList<Pedido> pedidos = new ArrayList<>();
    private String estado = "ABERTA";
    private Pagamento pagamento;

    Comanda(Mesa mesa) { this.mesa = mesa; }

    public void incluirPedido(Pedido pedido) {
        exigirAberta(estado);
        if (pedido == null) throw new IllegalArgumentException("Informe o pedido.");
        pedido.vincular(this);
        pedidos.add(pedido);
    }

    public void solicitarConta() {
        exigirAberta(estado);
        estado = "CONTA_SOLICITADA";
    }

    public void fecharConta(Funcionario caixa, Pagamento pagamento) {
        if (caixa == null || !caixa.podeReceber()) {
            throw new IllegalStateException("Funcionário sem permissão de caixa.");
        }
        validarPagamento(estado, calcularTotal(), pagamento);
        this.pagamento = pagamento;
        estado = "ENCERRADA";
    }

    public static void exigirAberta(String estado) {
        if (!"ABERTA".equals(estado)) throw new IllegalStateException("A comanda não está aberta.");
    }

    public static void validarPagamento(String estado, long total, Pagamento pagamento) {
        if (!"CONTA_SOLICITADA".equals(estado)) {
            throw new IllegalStateException("Solicite a conta antes do pagamento.");
        }
        if (pagamento == null || pagamento.getValorCentavos() != total) {
            throw new IllegalArgumentException("O pagamento deve corresponder ao total da comanda.");
        }
    }

    public long calcularTotal() {
        long total = 0;
        for (Pedido pedido : pedidos) total = Math.addExact(total, pedido.calcularTotal());
        return total;
    }

    public List<Pedido> getPedidos() { return new ArrayList<>(pedidos); }
    public String getEstado() { return estado; }
    public Mesa getMesa() { return mesa; }
    public Pagamento getPagamento() { return pagamento; }
}
