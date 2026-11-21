package br.ufg.atlas.servidor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

final class ConexaoBanco {
    static {
        // No exec:java, o pgJDBC está no classloader da aplicação executada pelo Maven.
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException erro) {
            throw new IllegalStateException("Driver JDBC do PostgreSQL ausente no classpath do Backend.", erro);
        }
    }

    private final String endereco;
    private final Properties acesso = new Properties();

    ConexaoBanco() {
        this(System.getenv().getOrDefault("ATLAS_BANCO_URL", "jdbc:postgresql://localhost:5432/atlas"),
                System.getenv().getOrDefault("ATLAS_BANCO_USUARIO", "atlas"), System.getenv("ATLAS_BANCO_SENHA"));
    }

    ConexaoBanco(String endereco, String usuario, String senha) {
        this.endereco = endereco;
        acesso.setProperty("user", usuario);
        if (senha != null) acesso.setProperty("password", senha);
        acesso.setProperty("connectTimeout", "5");
        acesso.setProperty("socketTimeout", "20");
    }

    Connection abrir() throws SQLException {
        return DriverManager.getConnection(endereco, acesso);
    }
}
