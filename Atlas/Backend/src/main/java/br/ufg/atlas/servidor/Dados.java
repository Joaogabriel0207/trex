package br.ufg.atlas.servidor;

import br.ufg.atlas.modelo.Cargo;
import br.ufg.atlas.modelo.Funcionario;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;

/** O servidor autentica, verifica o cargo e delimita a transação antes de chamar a operação. */
final class Dados {
    private Dados() { }

    static JsonElement executar(Connection conexao, Funcionario funcionario, String metodo,
            String[] partes, JsonObject corpo) throws SQLException {
        if (partes.length == 0) throw rotaInexistente();
        String recurso = partes[0];
        if (partes.length == 1 && metodo.equals("GET")) {
            switch (recurso) {
                case "mesas": return BancoSalao.mesas(conexao);
                case "produtos": return BancoSalao.produtos(conexao, funcionario.getCargo() == Cargo.GARCOM);
                case "funcionarios": return BancoFuncionarios.listar(conexao);
                case "comandas": return BancoComandas.listar(conexao, funcionario);
                case "pedidos": return ConsultaPedidos.listar(conexao, funcionario);
                case "impressoes": return BancoImpressoes.listar(conexao);
                default: throw rotaInexistente();
            }
        }
        if (partes.length == 1 && metodo.equals("POST")) {
            switch (recurso) {
                case "mesas": return BancoSalao.cadastrarMesa(conexao, corpo);
                case "produtos": return BancoSalao.salvarProduto(conexao, null, corpo);
                case "funcionarios": return BancoFuncionarios.cadastrar(conexao, corpo);
                case "comandas": return BancoComandas.abrir(conexao, funcionario, Campos.positivo(corpo, "mesaId"));
                case "pedidos": return BancoPedidos.registrar(conexao, funcionario, corpo);
                default: throw rotaInexistente();
            }
        }
        if (partes.length == 2 && metodo.equals("PUT")) {
            if (recurso.equals("produtos")) return BancoSalao.salvarProduto(conexao, Campos.identificador(partes[1]), corpo);
            if (recurso.equals("funcionarios")) return BancoFuncionarios.editar(conexao, partes[1], corpo);
        }
        if (partes.length == 2 && metodo.equals("GET") && recurso.equals("comandas")) {
            return BancoComandas.detalhar(conexao, funcionario, Campos.identificador(partes[1]));
        }
        if (partes.length == 3 && metodo.equals("POST")) {
            int id = Campos.identificador(partes[1]);
            switch (recurso + "/" + partes[2]) {
                case "comandas/solicitar": return BancoComandas.solicitar(conexao, id);
                case "comandas/pagar": return BancoComandas.pagar(conexao, funcionario, id, corpo);
                case "pedidos/pronto": return BancoPedidos.marcarPronto(conexao, id);
                case "pedidos/entregar": return BancoPedidos.entregar(conexao, id, funcionario);
                case "impressoes/reservar": return BancoImpressoes.reservar(conexao, funcionario, id, corpo);
                case "impressoes/resultado": return BancoImpressoes.registrarResultado(conexao, funcionario, id, corpo);
                default: throw rotaInexistente();
            }
        }
        throw rotaInexistente();
    }

    private static ErroHttp rotaInexistente() {
        return new ErroHttp(404, "Operação não encontrada.");
    }
}
