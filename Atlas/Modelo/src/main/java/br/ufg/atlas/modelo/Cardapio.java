package br.ufg.atlas.modelo;

import java.util.ArrayList;
import java.util.List;

public final class Cardapio {
    private final ArrayList<Produto> produtos = new ArrayList<>();

    public void adicionar(Produto produto) {
        if (produto == null || buscarProduto(produto.getId()) != null) {
            throw new IllegalArgumentException("Produto ausente ou já cadastrado.");
        }
        produtos.add(produto);
    }

    public Produto buscarProduto(long id) {
        for (Produto produto : produtos) {
            if (produto.getId() == id) return produto;
        }
        return null;
    }

    public List<Produto> getProdutos() { return new ArrayList<>(produtos); }
}
