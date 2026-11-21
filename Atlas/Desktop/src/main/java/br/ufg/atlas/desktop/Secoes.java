package br.ufg.atlas.desktop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.function.Predicate;

final class Secoes {
    private final ControlePainel painel;
    private final ControleListagem tela;
    private final Operacoes operacoes;
    private final boolean gerente;
    private final String secao;
    private String rota;
    private Predicate<JsonObject> filtro = linha -> true;
    private boolean pedidosHistoricos;

    Secoes(ControlePainel painel, ControleListagem tela, String secao) {
        this.painel = painel;
        this.tela = tela;
        this.secao = secao;
        operacoes = new Operacoes(painel);
        gerente = Dados.texto(painel.ambiente().funcionario, "cargo").equals("GERENTE");
        switch (secao) {
            case "pedidos" -> pedidos(false);
            case "salao" -> salao();
            case "cardapio" -> cardapio();
            case "funcionarios" -> funcionarios();
            case "financeiro" -> contas(false);
            case "producao" -> producao();
            case "historico" -> contas(true);
            default -> throw new IllegalArgumentException("Área do Desktop desconhecida.");
        }
    }

    String rota() { return rota; }
    void preencher(JsonArray dados) { tela.preencher(dados, filtro); }

    void alternarHistorico() {
        if (!secao.equals("historico") || !gerente) return;
        tela.limpar();
        pedidosHistoricos = !pedidosHistoricos;
        if (pedidosHistoricos) pedidos(true);
        else contas(true);
    }

    private void pedidos(boolean historico) {
        rota = "/pedidos";
        filtro = historico ? linha -> estado(linha, "ENTREGUE") : linha -> !estado(linha, "ENTREGUE");
        tela.campo("Pedido", "id");
        tela.campo("Mesa", "mesaNumero");
        tela.campo("Responsável", "funcionarioNome");
        tela.coluna("Data", linha -> Dados.dataLocal(Dados.texto(linha, "criadoEm")));
        tela.codigo("Estado", "estado");
        tela.dinheiro("Total", "totalCentavos");
        tela.acao(0, "Ver pedido", Objects::nonNull, operacoes::pedido);
        if (historico) tela.acao(1, "Ver comandas encerradas", linha -> true, linha -> painel.trocarHistorico());
        else tela.acao(1, "Marcar como pronto", linha -> estado(linha, "REGISTRADO"), operacoes::pronto);
    }

    private void salao() {
        rota = "/mesas";
        tela.campo("Mesa", "numero");
        tela.codigo("Situação", "estado");
        tela.coluna("Comanda", linha -> Dados.texto(linha, "comandaId").isEmpty() ? "—" : Dados.texto(linha, "comandaId"));
        if (gerente) tela.acao(0, "Cadastrar mesa", linha -> true, linha -> operacoes.editar("mesas", null));
        tela.acao(1, "Ver consumo", linha -> linha != null && !Dados.texto(linha, "comandaId").isEmpty(),
                linha -> operacoes.comanda(Dados.texto(linha, "comandaId")));
    }

    private void cardapio() {
        rota = "/produtos";
        tela.campo("Produto", "nome");
        tela.campo("Categoria", "categoria");
        tela.dinheiro("Preço", "precoCentavos");
        tela.codigo("Setor", "setor");
        tela.codigo("Disponível", "disponivel");
        tela.acao(0, "Cadastrar produto", linha -> true, linha -> operacoes.editar("produtos", null));
        tela.acao(1, "Editar", Objects::nonNull, linha -> operacoes.editar("produtos", linha));
    }

    private void funcionarios() {
        rota = "/funcionarios";
        tela.campo("Código", "codigo");
        tela.campo("Nome", "nome");
        tela.codigo("Cargo", "cargo");
        tela.codigo("Ativo", "ativo");
        tela.acao(0, "Cadastrar funcionário", linha -> true, linha -> operacoes.editar("funcionarios", null));
        tela.acao(1, "Editar", Objects::nonNull, linha -> operacoes.editar("funcionarios", linha));
    }

    private void contas(boolean historico) {
        rota = "/comandas";
        filtro = linha -> estado(linha, "ENCERRADA") == historico;
        tela.campo("Comanda", "id");
        tela.campo("Mesa", "mesaNumero");
        tela.codigo("Situação", "estado");
        tela.dinheiro("Consumo", "totalCentavos");
        tela.acao(0, "Ver consumo", Objects::nonNull, linha -> operacoes.comanda(Dados.texto(linha, "id")));
        if (!historico) tela.acao(1, "Receber e fechar", linha -> estado(linha, "CONTA_SOLICITADA"), operacoes::pagar);
        if (historico && gerente) tela.acao(1, "Ver pedidos entregues", linha -> true, linha -> painel.trocarHistorico());
    }

    private void producao() {
        rota = "/impressoes";
        FilaProducao fila = new FilaProducao(painel);
        tela.campo("Pedido", "pedidoId");
        tela.campo("Mesa", "mesaNumero");
        tela.codigo("Setor", "setor");
        tela.codigo("Impressão", "estado");
        tela.campo("Última tentativa", "detalhe");
        tela.acao(0, "Imprimir", linha -> estado(linha, "PENDENTE") || estado(linha, "FALHA"), fila::imprimir);
        tela.textoConformeSelecao(0, linha -> estado(linha, "FALHA") ? "Tentar novamente" : "Imprimir");
        tela.acao(1, "Ver comprovante", Objects::nonNull, operacoes::comprovante);
        tela.acao(2, "Registrar resultado", linha -> estado(linha, "EM_TENTATIVA") || estado(linha, "INCERTA"), fila::conferir);
    }

    private static boolean estado(JsonObject linha, String esperado) {
        return linha != null && esperado.equals(Dados.texto(linha, "estado"));
    }
}
