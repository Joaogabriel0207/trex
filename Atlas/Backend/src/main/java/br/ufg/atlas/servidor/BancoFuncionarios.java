package br.ufg.atlas.servidor;

import br.ufg.atlas.modelo.Cargo;
import br.ufg.atlas.modelo.Funcionario;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

final class BancoFuncionarios {
    private BancoFuncionarios() { }

    static Funcionario autenticar(Connection conexao, String codigo, String senha) throws SQLException {
        JsonObject linha = Consultas.um(conexao,
                "SELECT codigo, nome, cargo, ativo, senha_hash FROM funcionarios WHERE codigo = ? AND ativo FOR SHARE",
                codigo);
        if (linha == null || !Senhas.conferir(senha, linha.get("senhaHash").getAsString())) return null;
        return funcionario(linha);
    }

    static Funcionario buscarAtivo(Connection conexao, String codigo) throws SQLException {
        JsonObject linha = Consultas.um(conexao,
                "SELECT codigo, nome, cargo, ativo FROM funcionarios WHERE codigo = ? AND ativo FOR SHARE", codigo);
        return linha == null ? null : funcionario(linha);
    }

    private static Funcionario funcionario(JsonObject linha) {
        return new Funcionario(linha.get("codigo").getAsString(), linha.get("nome").getAsString(),
                Cargo.valueOf(linha.get("cargo").getAsString()), linha.get("ativo").getAsBoolean());
    }

    static void criarPrimeiroGerente(Connection conexao, String codigo, String nome, String senha)
            throws SQLException {
        bloquearCadastro(conexao);
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
                "SELECT codigo, nome, cargo, ativo, expediente FROM funcionarios ORDER BY nome, codigo");
    }

    static JsonObject cadastrar(Connection conexao, JsonObject dados) throws SQLException {
        String codigo = Campos.texto(dados, "codigo", 32);
        Funcionario novo = lerCadastro(codigo, dados);
        return inserirFuncionario(conexao, novo, Campos.senha(dados), "");
    }

    private static JsonObject inserirFuncionario(Connection conexao, Funcionario novo, String senha,
            String expediente) throws SQLException {
        return Consultas.um(conexao, "INSERT INTO funcionarios (codigo, nome, cargo, ativo, senha_hash, expediente) "
                + "VALUES (?, ?, ?, ?, ?, ?) RETURNING codigo", novo.getCodigo(), novo.getNome(),
                novo.getCargo().name(), novo.isAtivo(), Senhas.gerar(senha), expediente);
    }

    static JsonObject editar(Connection conexao, String codigo, JsonObject dados) throws SQLException {
        Funcionario novo = lerCadastro(codigo, dados);
        bloquearCadastro(conexao);
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

    static int importarFuncionarios(Connection conexao, String csv, boolean validar) throws SQLException {
        List<Csv.Linha> linhas = Csv.ler(csv, 100, "username", "name", "role", "schedule", "password", "active");
        Set<String> codigos = new HashSet<>();
        for (Csv.Linha linha : linhas) {
            try {
                JsonObject dados = new JsonObject();
                dados.addProperty("codigo", linha.campo(0));
                dados.addProperty("nome", linha.campo(1));
                String cargo = linha.campo(2).strip().toUpperCase(Locale.ROOT);
                dados.addProperty("cargo", switch (cargo) {
                    case "BAR" -> "BARMAN";
                    case "COZINHA" -> "COZINHEIRO";
                    default -> cargo;
                });
                dados.addProperty("expediente", linha.campo(3));
                dados.addProperty("senha", linha.campo(4));
                dados.addProperty("ativo", linha.logico(5, "active"));
                String codigo = Campos.texto(dados, "codigo", 32);
                Funcionario novo = lerCadastro(codigo, dados);
                String senha = Senhas.validar(Campos.senha(dados));
                String expediente = Campos.opcional(dados, "expediente", 120);
                if (!codigos.add(codigo) || Consultas.um(conexao,
                        "SELECT codigo FROM funcionarios WHERE codigo = ?", codigo) != null) {
                    throw new ErroHttp(409, "Código de acesso (username) duplicado.");
                }
                if (!validar) inserirFuncionario(conexao, novo, senha, expediente);
            } catch (ErroHttp erro) {
                throw linha.erro(erro.getStatus(), erro.getMessage());
            } catch (IllegalArgumentException erro) {
                throw linha.erro(400, erro.getMessage());
            }
        }
        return linhas.size();
    }

    static void bloquearCadastro(Connection conexao) throws SQLException {
        Consultas.executar(conexao, "LOCK TABLE funcionarios IN SHARE ROW EXCLUSIVE MODE");
    }

    static JsonArray desativarFuncionarios(Connection conexao, String gerente) throws SQLException {
        return Consultas.lista(conexao,
                "UPDATE funcionarios SET ativo = FALSE WHERE ativo AND codigo <> ? RETURNING codigo", gerente);
    }

    static JsonArray excluirFuncionarios(Connection conexao, String gerente) throws SQLException {
        // Fecha a janela para novos vínculos enquanto verificamos as operações e excluímos.
        Consultas.lista(conexao, "SELECT codigo FROM funcionarios WHERE codigo <> ? ORDER BY codigo FOR UPDATE",
                gerente);
        JsonArray impedidos = Consultas.lista(conexao, """
                SELECT f.codigo, f.nome FROM funcionarios f WHERE f.codigo <> ? AND (
                    EXISTS (SELECT 1 FROM comandas c WHERE c.funcionario_codigo = f.codigo AND c.estado <> 'ENCERRADA')
                    OR EXISTS (SELECT 1 FROM pedidos p JOIN comandas c ON c.id = p.comanda_id
                        WHERE p.funcionario_codigo = f.codigo AND (p.estado <> 'ENTREGUE' OR c.estado <> 'ENCERRADA'))
                    OR EXISTS (SELECT 1 FROM tarefas_impressao t
                        WHERE (t.responsavel_codigo = f.codigo OR t.confirmado_por = f.codigo)
                        AND t.estado IN ('EM_TENTATIVA', 'INCERTA'))
                ) ORDER BY f.codigo
                """, gerente);
        if (!impedidos.isEmpty()) {
            StringJoiner nomes = new StringJoiner(", ");
            impedidos.forEach(item -> {
                JsonObject pessoa = item.getAsJsonObject();
                nomes.add(pessoa.get("nome").getAsString() + " (" + pessoa.get("codigo").getAsString() + ")");
            });
            throw new ErroHttp(409, "Resolva as comandas ou operações em andamento de: " + nomes + ".");
        }
        return Consultas.lista(conexao, "DELETE FROM funcionarios WHERE codigo <> ? RETURNING codigo", gerente);
    }
}
