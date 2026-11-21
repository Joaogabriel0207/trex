package br.ufg.atlas.servidor;

import br.ufg.atlas.modelo.Produto;
import br.ufg.atlas.modelo.Setor;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

final class BancoSalao {
    private static final String PRODUTOS = "SELECT p.id, p.nome, c.nome AS categoria, p.preco_centavos, "
            + "p.setor, p.disponivel";
    private static final String DE_PRODUTOS = " FROM produtos p JOIN categorias c ON c.id = p.categoria_id ";

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
        String detalhes = somenteDisponiveis ? "" : ", p.descricao, p.imagem";
        return Consultas.lista(conexao, PRODUTOS + detalhes + DE_PRODUTOS + filtro + "ORDER BY c.nome, p.nome, p.id");
    }

    static Produto buscarParaPedido(Connection conexao, int id) throws SQLException {
        JsonObject dados = Consultas.um(conexao, PRODUTOS + DE_PRODUTOS + "WHERE p.id = ? FOR SHARE OF p", id);
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
        Produto produto = lerProduto(id != null ? id : proximoProduto(conexao), dados);
        JsonObject categoria = Consultas.um(conexao, "INSERT INTO categorias (nome) VALUES (?) "
                + "ON CONFLICT (nome) DO UPDATE SET nome = EXCLUDED.nome RETURNING id", produto.getCategoria());
        int categoriaId = categoria.get("id").getAsInt();
        if (id == null) {
            return inserirProduto(conexao, produto, categoriaId, "", "");
        }
        JsonObject linha = Consultas.um(conexao, "UPDATE produtos SET nome = ?, categoria_id = ?, "
                + "preco_centavos = ?, setor = ?, disponivel = ? WHERE id = ? RETURNING id",
                produto.getNome(), categoriaId, produto.getPrecoCentavos(), produto.getSetor().name(),
                produto.isDisponivel(), id);
        if (linha == null) throw new ErroHttp(404, "Produto não encontrado.");
        return linha;
    }

    private static long proximoProduto(Connection conexao) throws SQLException {
        return Consultas.um(conexao,
                "SELECT nextval(pg_get_serial_sequence('produtos','id')) AS id").get("id").getAsLong();
    }

    private static JsonObject inserirProduto(Connection conexao, Produto produto, int categoriaId,
            String descricao, String imagem) throws SQLException {
        return Consultas.um(conexao, "INSERT INTO produtos "
                + "(id, nome, categoria_id, preco_centavos, setor, disponivel, descricao, imagem) "
                + "OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                produto.getId(), produto.getNome(), categoriaId, produto.getPrecoCentavos(),
                produto.getSetor().name(), produto.isDisponivel(), descricao, imagem);
    }

    static int importarProdutos(Connection conexao, String csv, boolean validar) throws SQLException {
        List<Csv.Linha> linhas = Csv.ler(csv, 1000, "nome", "descricao", "preco", "categoria",
                "setor", "imagem", "disponivel");
        for (Csv.Linha linha : linhas) {
            try {
                JsonObject dados = new JsonObject();
                dados.addProperty("nome", linha.campo(0));
                dados.addProperty("descricao", linha.campo(1));
                dados.addProperty("precoCentavos", lerPreco(linha.campo(2)));
                dados.addProperty("categoria", linha.campo(3));
                dados.addProperty("setor", linha.campo(4).strip().toUpperCase(Locale.ROOT));
                dados.addProperty("imagem", linha.campo(5));
                dados.addProperty("disponivel", linha.logico(6, "disponivel"));
                // A prévia valida o modelo sem reservar um ID no banco.
                Produto produto = lerProduto(validar ? 1 : proximoProduto(conexao), dados);
                String descricao = Campos.opcional(dados, "descricao", 500);
                String imagem = validarImagem(Campos.opcional(dados, "imagem", 2048));
                JsonObject categoria = Consultas.um(conexao, "SELECT id FROM categorias WHERE nome = ?",
                        produto.getCategoria());
                if (categoria == null) throw new ErroHttp(400, "Campo categoria inexistente no cardápio.");
                if (!validar) inserirProduto(conexao, produto, categoria.get("id").getAsInt(), descricao, imagem);
            } catch (ErroHttp erro) {
                throw linha.erro(erro.getStatus(), erro.getMessage());
            } catch (IllegalArgumentException erro) {
                throw linha.erro(400, erro.getMessage());
            }
        }
        return linhas.size();
    }

    private static long lerPreco(String valor) {
        String decimal = valor.strip().replace(',', '.');
        if (!decimal.matches("[0-9]{1,12}([.][0-9]{1,2})?")) throw Campos.invalido("preco");
        return new BigDecimal(decimal).movePointRight(2).longValueExact();
    }

    private static String validarImagem(String imagem) {
        if (imagem.isEmpty()) return imagem;
        try {
            URI url = URI.create(imagem);
            if ("https".equalsIgnoreCase(url.getScheme()) && url.getHost() != null) return imagem;
        } catch (IllegalArgumentException erro) {
            throw new ErroHttp(400, "Campo imagem deve conter uma URL HTTPS válida.");
        }
        throw new ErroHttp(400, "Campo imagem deve conter uma URL HTTPS válida.");
    }

    static int desativarProdutos(Connection conexao) throws SQLException {
        bloquearProdutos(conexao);
        return Consultas.executar(conexao, "UPDATE produtos SET disponivel = FALSE WHERE disponivel");
    }

    static int excluirProdutos(Connection conexao) throws SQLException {
        bloquearProdutos(conexao);
        return Consultas.executar(conexao, "DELETE FROM produtos");
    }

    private static void bloquearProdutos(Connection conexao) throws SQLException {
        // Espera os pedidos que já consultaram produtos, inclusive pedidos com vários itens.
        Consultas.executar(conexao, "LOCK TABLE produtos IN EXCLUSIVE MODE");
    }
}
