package br.ufg.atlas.servidor;

final class ErroHttp extends RuntimeException {
    private final int status;

    ErroHttp(int status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    int getStatus() { return status; }
}
