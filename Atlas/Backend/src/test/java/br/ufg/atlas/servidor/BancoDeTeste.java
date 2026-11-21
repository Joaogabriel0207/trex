package br.ufg.atlas.servidor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Um esquema novo por execução. O nome não vem dos argumentos nem das variáveis de ambiente. */
final class BancoDeTeste implements AutoCloseable {
    private final String esquema = "atlas_teste_" + UUID.randomUUID().toString().replace("-", "");
    private final ConexaoBanco administracao;
    private final ConexaoBanco isolado;
    private boolean criado;

    BancoDeTeste(String endereco, String usuario, String senha) {
        administracao = new ConexaoBanco(endereco, usuario, senha);
        String separador = endereco.contains("?") ? "&" : "?";
        isolado = new ConexaoBanco(endereco + separador + "currentSchema=" + esquema, usuario, senha);
    }

    ConexaoBanco preparar(Path arquivo, String senhaGerente) throws Exception {
        String definicao = Files.readString(arquivo);
        String migracao = Files.readString(arquivo.resolveSibling("001_importacao_e_operacoes_em_massa.sql"));
        try (Connection conexao = administracao.abrir()) {
            Consultas.executar(conexao, "CREATE SCHEMA " + esquema);
            criado = true;
        }
        try (Connection conexao = isolado.abrir()) {
            try (PreparedStatement consulta = conexao.prepareStatement("SELECT current_schema()");
                 ResultSet linha = consulta.executeQuery()) {
                if (!linha.next() || !esquema.equals(linha.getString(1))) {
                    throw new IllegalStateException("O JDBC não selecionou o esquema de teste.");
                }
            }
            try (PreparedStatement instalar = conexao.prepareStatement(definicao)) {
                instalar.execute();
            }
            try (PreparedStatement atualizar = conexao.prepareStatement(migracao)) {
                atualizar.execute();
            }
            conexao.setAutoCommit(false);
            BancoFuncionarios.criarPrimeiroGerente(conexao, "gerencia", "Gerente de teste", senhaGerente);
            conexao.commit();
        }
        return isolado;
    }

    @Override
    public void close() throws SQLException {
        if (criado) {
            try (Connection conexao = administracao.abrir()) {
                Consultas.executar(conexao, "DROP SCHEMA " + esquema + " CASCADE");
                criado = false;
            }
        }
    }
}
