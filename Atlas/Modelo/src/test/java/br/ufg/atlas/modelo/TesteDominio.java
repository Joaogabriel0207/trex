package br.ufg.atlas.modelo;

import java.util.ArrayList;
import java.util.List;

/** Pode ser executado diretamente pelo main no BlueJ ou pelo JDK. */
public final class TesteDominio {
    private static int verificacoes;

    private static void conferir(boolean condicao, String caso) {
        if (!condicao) throw new AssertionError(caso);
        verificacoes++;
    }

    private static void exigirRecusa(Runnable operacao) {
        try {
            operacao.run();
        } catch (IllegalArgumentException | IllegalStateException esperada) {
            verificacoes++;
            return;
        }
        throw new AssertionError("A operação deveria ter sido recusada.");
    }

    public static void main(String[] argumentos) {
        Restaurante restaurante = new Restaurante();
        Mesa mesa = restaurante.cadastrarMesa(7);
        conferir(restaurante.buscarMesa(7) == mesa && mesa.getComandaAberta() == null, "Mesa livre");
        exigirRecusa(() -> restaurante.cadastrarMesa(7));
        exigirRecusa(() -> new Mesa(0));
        Produto suco = new Produto(1, "Suco", "Bebidas", 750, Setor.BAR, true);
        Produto sopa = new Produto(2, "Sopa", "Entradas", 1450, Setor.COZINHA, true);
        restaurante.getCardapio().adicionar(suco);
        restaurante.getCardapio().adicionar(sopa);
        conferir(restaurante.getCardapio().buscarProduto(1) == suco, "Referência ao produto");
        exigirRecusa(() -> restaurante.getCardapio().adicionar(suco));
        exigirRecusa(() -> suco.alterarPreco(-1));
        exigirRecusa(() -> new ItemPedido(suco, 0, ""));
        exigirRecusa(() -> new ItemPedido(suco, 1000, ""));
        ItemPedido bebida = new ItemPedido(suco, 2, "Sem gelo");
        ItemPedido prato = new ItemPedido(sopa, 1, "");
        Funcionario garcom = new Funcionario("G1", "Garçom de teste", Cargo.GARCOM, true);
        Funcionario outro = new Funcionario("G2", "Outro garçom", Cargo.GARCOM, true);
        Funcionario gerente = new Funcionario("M1", "Gerência", Cargo.GERENTE, true);
        Funcionario caixa = new Funcionario("C1", "Caixa", Cargo.CAIXA, true);
        Funcionario inativo = new Funcionario("I1", "Inativo", Cargo.GERENTE, false);
        conferir(!inativo.podeReceber() && !inativo.podeAdministrar(), "Inativo");
        for (Cargo cargo : Cargo.values()) {
            Funcionario pessoa = new Funcionario("F", "Teste", cargo, true);
            conferir(pessoa.podeAtender() == (cargo == Cargo.GARCOM), "Permissão de atendimento");
            conferir(pessoa.podeReceber() == (cargo == Cargo.CAIXA || cargo == Cargo.GERENTE), "Caixa");
        }
        exigirRecusa(() -> new Pedido(caixa, List.of(bebida)));
        exigirRecusa(() -> new Pedido(garcom, List.of()));
        Pedido bar = new Pedido(garcom, List.of(bebida));
        Pedido cozinha = new Pedido(garcom, List.of(prato));
        ArrayList<ItemPedido> selecao = new ArrayList<>(List.of(prato, bebida));
        Pedido misto = new Pedido(garcom, selecao);
        selecao.clear();
        misto.getItens().clear();
        suco.alterarPreco(900);
        suco.definirDisponibilidade(false);
        exigirRecusa(() -> new ItemPedido(suco, 1, ""));
        conferir(bar.calcularTotal() == 1500 && cozinha.calcularTotal() == 1450, "Pedidos por setor");
        conferir(misto.calcularTotal() == 2950 && bebida.getPrecoCentavos() == 750, "Preço congelado");
        Comanda comanda = mesa.abrirComanda();
        conferir(mesa.abrirComanda() == comanda, "Comanda compartilhada pela mesa");
        comanda.incluirPedido(bar);
        comanda.incluirPedido(cozinha);
        comanda.incluirPedido(misto);
        exigirRecusa(() -> comanda.incluirPedido(misto));
        conferir(misto.getComanda() == comanda, "Comanda do pedido");
        exigirRecusa(() -> restaurante.cadastrarMesa(8).abrirComanda().incluirPedido(misto));
        conferir(comanda.calcularTotal() == 5900, "Soma exata");
        exigirRecusa(() -> misto.registrarEntrega(garcom));
        exigirRecusa(() -> misto.marcarPronto(garcom));
        misto.marcarPronto(gerente);
        exigirRecusa(() -> misto.registrarEntrega(outro));
        misto.registrarEntrega(garcom);
        conferir(misto.getEstado().equals("ENTREGUE"), "Entrega");
        Pagamento integral = new Pagamento(5900, FormaPagamento.PIX);
        exigirRecusa(() -> comanda.fecharConta(caixa, integral));
        comanda.solicitarConta();
        exigirRecusa(() -> comanda.incluirPedido(bar));
        exigirRecusa(() -> comanda.fecharConta(garcom, integral));
        exigirRecusa(() -> comanda.fecharConta(caixa, new Pagamento(5899, FormaPagamento.PIX)));
        comanda.fecharConta(caixa, integral);
        exigirRecusa(() -> comanda.incluirPedido(bar));
        conferir(comanda.getPagamento() == integral && mesa.getComandaAberta() == null, "Mesa liberada");
        conferir(mesa.abrirComanda() != comanda && comanda.calcularTotal() == 5900, "Próximo atendimento");
        System.out.println(verificacoes + " verificações de domínio aprovadas.");
    }
}
