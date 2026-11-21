package br.ufg.atlas.servidor;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;

/** Os DAOs escrevem o SQL e escolhem os campos públicos de cada resposta. */
final class Consultas {
    private Consultas() { }

    static PreparedStatement preparar(Connection conexao, String sql, Object... parametros)
            throws SQLException {
        PreparedStatement comando = conexao.prepareStatement(sql);
        try {
            for (int i = 0; i < parametros.length; i++) {
                comando.setObject(i + 1, parametros[i]);
            }
            return comando;
        } catch (SQLException erro) {
            comando.close();
            throw erro;
        }
    }

    static int executar(Connection conexao, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement comando = preparar(conexao, sql, parametros)) {
            return comando.executeUpdate();
        }
    }

    static JsonObject um(Connection conexao, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement comando = preparar(conexao, sql, parametros);
             ResultSet linhas = comando.executeQuery()) {
            if (linhas.next()) {
                return lerLinha(linhas);
            }
            return null;
        }
    }

    static JsonArray lista(Connection conexao, String sql, Object... parametros) throws SQLException {
        JsonArray valores = new JsonArray();
        try (PreparedStatement comando = preparar(conexao, sql, parametros);
             ResultSet linhas = comando.executeQuery()) {
            while (linhas.next()) {
                valores.add(lerLinha(linhas));
            }
        }
        return valores;
    }

    private static JsonObject lerLinha(ResultSet linha) throws SQLException {
        ResultSetMetaData colunas = linha.getMetaData();
        JsonObject valor = new JsonObject();
        for (int i = 1; i <= colunas.getColumnCount(); i++) {
            String campo = nomeJson(colunas.getColumnLabel(i));
            Object dado = linha.getObject(i);
            if (dado == null) {
                valor.add(campo, JsonNull.INSTANCE);
            } else if (dado instanceof Number) {
                valor.addProperty(campo, (Number) dado);
            } else if (dado instanceof Boolean) {
                valor.addProperty(campo, (Boolean) dado);
            } else if (colunas.getColumnType(i) == Types.TIMESTAMP_WITH_TIMEZONE
                    || "timestamptz".equals(colunas.getColumnTypeName(i))) {
                valor.addProperty(campo, linha.getObject(i, OffsetDateTime.class).toString());
            } else {
                valor.addProperty(campo, dado.toString());
            }
        }
        return valor;
    }

    private static String nomeJson(String coluna) {
        StringBuilder nome = new StringBuilder();
        boolean novaPalavra = false;
        for (char letra : coluna.toCharArray()) {
            if (letra == '_') {
                novaPalavra = true;
            } else {
                nome.append(novaPalavra ? Character.toUpperCase(letra) : letra);
                novaPalavra = false;
            }
        }
        return nome.toString();
    }
}
