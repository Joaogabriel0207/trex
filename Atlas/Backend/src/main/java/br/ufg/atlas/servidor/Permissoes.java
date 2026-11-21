package br.ufg.atlas.servidor;

import br.ufg.atlas.modelo.Funcionario;

final class Permissoes {
    private Permissoes() {}

    static void verificar(Funcionario pessoa, String metodo, String[] partes) {
        boolean autorizado;
        String rota = metodo + " " + partes[0];
        if (partes.length == 2) rota += metodo.equals("POST") ? "/" + partes[1] : "/id";
        if (partes.length == 3) rota += "/id/" + partes[2];
        if (partes.length > 3) throw new ErroHttp(404, "Operação desconhecida.");
        switch (rota) {
            case "GET mesas", "GET comandas", "GET comandas/id" ->
                    autorizado = pessoa.podeReceber() || pessoa.podeAtender();
            case "GET produtos", "GET pedidos" ->
                    autorizado = pessoa.podeAdministrar() || pessoa.podeAtender();
            case "POST comandas", "POST pedidos", "POST pedidos/id/entregar", "POST comandas/id/solicitar" ->
                    autorizado = pessoa.podeAtender();
            case "POST comandas/id/pagar" -> autorizado = pessoa.podeReceber();
            case "POST mesas", "POST produtos", "PUT produtos/id", "GET funcionarios", "POST funcionarios",
                 "POST produtos/importar", "POST produtos/desativar-todos", "POST produtos/excluir-todos",
                 "POST funcionarios/importar", "POST funcionarios/desativar-todos", "POST funcionarios/excluir-todos",
                 "PUT funcionarios/id", "POST pedidos/id/pronto", "GET impressoes",
                 "POST impressoes/id/reservar", "POST impressoes/id/resultado" ->
                    autorizado = pessoa.podeAdministrar();
            default -> throw new ErroHttp(404, "Operação desconhecida.");
        }
        if (!autorizado) throw new ErroHttp(403, "Seu cargo não permite esta operação.");
    }
}
