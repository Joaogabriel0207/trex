package br.ufg.atlas.servidor;

import br.ufg.atlas.modelo.Cargo;
import br.ufg.atlas.modelo.Funcionario;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;

final class BancoFuncionarios {
    private BancoFuncionarios() { }

    static Funcionario autenticar(Connection conexao, String codigo, String senha) throws SQLException {
        JsonObject linha = Consultas.um(conexao,
                "SELECT codigo, nome, cargo, ativo, senha_hash FROM funcionarios WHERE codigo = ? AND ativo",
                codigo);
        if (linha == null || !Senhas.conferir(senha, linha.get("senhaHash").getAsString())) return null;
        return funcionario(linha);
    }

    static Funcionario buscarAtivo(Connection conexao, String codigo) throws SQLException {
        JsonObject linha = Consultas.um(conexao,
                "SELECT codigo, nome, cargo, ativo FROM funcionarios WHERE codigo = ? AND ativo", codigo);
        return linha == null ? null : funcionario(linha);
    }

    private static Funcionario funcionario(JsonObject linha) {
        return new Funcionario(linha.get("codigo").getAsString(), linha.get("nome").getAsString(),
                Cargo.valueOf(linha.get("cargo").getAsString()), linha.get("ativo").getAsBoolean());
    }

    static void criarPrimeiroGerente(Connection conexao, String codigo, String nome, String senha)
            throws SQLException {
        Consultas.executar(conexao, "LOCK TABLE funcionarios IN SHARE ROW EXCLUSIVE MODE");
        if (Consultas.um(conexao, "SELECT codigo FROM funcionarios LIMIT 1") != null) {
            throw new ErroHttp(409, "Já existem funcionários cadastrados.");
        }
        JsonObject dados = new JsonObject();
        dados.addProperty("codigo", codigo);
        dados.addProperty("nome", nome);
        dados.addProperty("cargo", "GERENTE");
        dados.addProperty("ativo", true);
        dados.addProperty("senha", senha);
        cadastrar(conexao, dados);
    }

    static JsonArray listar(Connection conexao) throws SQLException {
        return Consultas.lista(conexao,
                "SELECT codigo, nome, cargo, ativo FROM funcionarios ORDER BY nome, codigo");
    }

    static JsonObject cadastrar(Connection conexao, JsonObject dados) throws SQLException {
        String codigo = Campos.texto(dados, "codigo", 32);
        Funcionario novo = lerCadastro(codigo, dados);
        return Consultas.um(conexao, "INSERT INTO funcionarios (codigo, nome, cargo, ativo, senha_hash) "
                + "VALUES (?, ?, ?, ?, ?) RETURNING codigo", codigo, novo.getNome(), novo.getCargo().name(),
                novo.isAtivo(), Senhas.gerar(Campos.senha(dados)));
    }

    static JsonObject editar(Connection conexao, String codigo, JsonObject dados) throws SQLException {
        Funcionario novo = lerCadastro(codigo, dados);
        Consultas.executar(conexao, "LOCK TABLE funcionarios IN SHARE ROW EXCLUSIVE MODE");
        JsonObject anterior = Consultas.um(conexao, "SELECT ativo, cargo FROM funcionarios WHERE codigo = ?", codigo);
        if (anterior == null) throw new ErroHttp(404, "Funcionário não encontrado.");
        boolean gerente = anterior.get("ativo").getAsBoolean() && anterior.get("cargo").getAsString().equals("GERENTE");
        if (gerente && (!novo.isAtivo() || novo.getCargo() != Cargo.GERENTE)
                && Consultas.um(conexao, "SELECT codigo FROM funcionarios WHERE ativo AND cargo = 'GERENTE' "
                    + "AND codigo <> ? LIMIT 1", codigo) == null) {
            throw new ErroHttp(409, "Mantenha ao menos um gerente ativo.");
        }
        if (dados.has("senha")) {
            Consultas.executar(conexao, "UPDATE funcionarios SET senha_hash = ? WHERE codigo = ?",
                    Senhas.gerar(Campos.senha(dados)), codigo);
        }
        return Consultas.um(conexao, "UPDATE funcionarios SET nome = ?, cargo = ?, ativo = ? "
                + "WHERE codigo = ? RETURNING codigo", novo.getNome(), novo.getCargo().name(), novo.isAtivo(), codigo);
    }

    private static Funcionario lerCadastro(String codigo, JsonObject dados) {
        Cargo cargo;
        try {
            cargo = Cargo.valueOf(Campos.texto(dados, "cargo", 24));
        } catch (IllegalArgumentException erro) {
            throw Campos.invalido("cargo");
        }
        return new Funcionario(codigo, Campos.texto(dados, "nome", 120), cargo, Campos.logico(dados, "ativo"));
    }
}
