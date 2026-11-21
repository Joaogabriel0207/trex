package br.ufg.atlas.servidor;

import br.ufg.atlas.modelo.Produto;
import br.ufg.atlas.modelo.Setor;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;

final class BancoSalao {
    private static final String PRODUTOS = "SELECT p.id, p.nome, c.nome AS categoria, p.preco_centavos, "
            + "p.setor, p.disponivel FROM produtos p JOIN categorias c ON c.id = p.categoria_id ";

    private BancoSalao() { }

    static JsonArray mesas(Connection conexao) throws SQLException {
        return Consultas.lista(conexao, "SELECT m.id, m.numero, c.id AS comanda_id, "
                + "CASE WHEN c.id IS NULL THEN 'LIVRE' WHEN c.estado = 'ABERTA' THEN 'OCUPADA' "
                + "ELSE c.estado END AS estado FROM mesas m LEFT JOIN comandas c "
                + "ON c.mesa_id = m.id AND c.estado <> 'ENCERRADA' ORDER BY m.numero");
    }

    static JsonObject cadastrarMesa(Connection conexao, JsonObject dados) throws SQLException {
        return Consultas.um(conexao, "INSERT INTO mesas (numero) VALUES (?) RETURNING id",
                Campos.positivo(dados, "numero"));
    }

    static JsonArray produtos(Connection conexao, boolean somenteDisponiveis) throws SQLException {
        String filtro = somenteDisponiveis ? "WHERE p.disponivel " : "";
        return Consultas.lista(conexao, PRODUTOS + filtro + "ORDER BY c.nome, p.nome, p.id");
    }

    static Produto buscarParaPedido(Connection conexao, int id) throws SQLException {
        JsonObject dados = Consultas.um(conexao, PRODUTOS + "WHERE p.id = ? FOR SHARE OF p", id);
        if (dados == null) throw new ErroHttp(404, "Produto não encontrado.");
        Produto produto = lerProduto(id, dados);
        if (!dados.get("disponivel").getAsBoolean()) throw new ErroHttp(409, "Produto indisponível: " + produto.getNome());
        return produto;
    }

    private static Produto lerProduto(long id, JsonObject dados) {
        Setor setor;
        try {
            setor = Setor.valueOf(Campos.texto(dados, "setor", 10));
        } catch (IllegalArgumentException erro) {
            throw Campos.invalido("setor");
        }
        return new Produto(id, Campos.texto(dados, "nome", 120), Campos.texto(dados, "categoria", 120),
                Campos.inteiro(dados, "precoCentavos"), setor, Campos.logico(dados, "disponivel"));
    }

    static JsonObject salvarProduto(Connection conexao, Integer id, JsonObject dados) throws SQLException {
        // No cadastro, a sequência do PostgreSQL reserva o código antes de construir o produto.
        long produtoId = id != null ? id : Consultas.um(conexao,
                "SELECT nextval(pg_get_serial_sequence('produtos','id')) AS id").get("id").getAsLong();
        Produto produto = lerProduto(produtoId, dados);
        JsonObject categoria = Consultas.um(conexao, "INSERT INTO categorias (nome) VALUES (?) "
                + "ON CONFLICT (nome) DO UPDATE SET nome = EXCLUDED.nome RETURNING id", produto.getCategoria());
        int categoriaId = categoria.get("id").getAsInt();
        if (id == null) {
            return Consultas.um(conexao, "INSERT INTO produtos (id, nome, categoria_id, preco_centavos, setor, disponivel) "
                    + "OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, ?, ?, ?) RETURNING id", produto.getId(), produto.getNome(), categoriaId,
                    produto.getPrecoCentavos(), produto.getSetor().name(), produto.isDisponivel());
        }
        JsonObject linha = Consultas.um(conexao, "UPDATE produtos SET nome = ?, categoria_id = ?, "
                + "preco_centavos = ?, setor = ?, disponivel = ? WHERE id = ? RETURNING id",
                produto.getNome(), categoriaId, produto.getPrecoCentavos(), produto.getSetor().name(),
                produto.isDisponivel(), id);
        if (linha == null) throw new ErroHttp(404, "Produto não encontrado.");
        return linha;
    }
}
