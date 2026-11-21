package br.ufg.atlas.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public final class Atendimento extends Activity {
    enum Pagina { MESAS, COMANDA, CARDAPIO, RESUMO, PEDIDOS }
    interface Operacao<T> { T executar() throws Exception; }
    interface Receber<T> { void aceitar(T valor) throws Exception; }

    private final ExecutorService rede = Executors.newSingleThreadExecutor();
    private final Handler principal = new Handler(Looper.getMainLooper());
    private boolean ocupado;
    private boolean eventoPendente;
    private boolean visivel;
    private boolean voltouDoSegundoPlano;
    private Notificacoes notificacoes;
    private Configuracao configuracao;
    private Toolbar barra;
    private TextView vazio;
    private TextView situacao;
    private MenuItem voltar;
    private MenuItem descartar;
    Pagina pagina;
    Api api;
    Rascunho rascunho;
    Salao salao;
    Montagem montagem;
    long mesaId;
    long comandaId;
    int mesaNumero;

    @Override protected void onCreate(Bundle anterior) {
        super.onCreate(anterior);
        setContentView(R.layout.atendimento);
        String servidor = getIntent().getStringExtra("servidor");
        String codigo = getIntent().getStringExtra("codigo");
        String token = getIntent().getStringExtra("token");
        if (servidor == null || codigo == null || token == null) {
            voltarAoLogin();
            return;
        }
        api = new Api(servidor, token);
        configuracao = new Configuracao(this);
        try {
            rascunho = new Rascunho(this, servidor, codigo);
        } catch (IllegalStateException erro) {
            new AlertDialog.Builder(this).setMessage(erro.getMessage())
                    .setPositiveButton("Sair", (d, v) -> voltarAoLogin()).setCancelable(false).show();
            return;
        }
        salao = new Salao(this);
        montagem = new Montagem(this);
        barra = findViewById(R.id.barra);
        vazio = findViewById(R.id.vazio);
        situacao = findViewById(R.id.situacao_rede);
        voltar = barra.getMenu().add("Voltar");
        voltar.setOnMenuItemClickListener(item -> { onBackPressed(); return true; });
        barra.getMenu().add("Meus pedidos").setOnMenuItemClickListener(item -> { if (!ocupado) salao.pedidos(); return true; });
        descartar = barra.getMenu().add("Descartar rascunho");
        descartar.setOnMenuItemClickListener(item -> {
            if (!ocupado && !rascunho.pendente()) confirmar("Descartar o pedido em montagem?",
                    "Os itens selecionados serão removidos deste aparelho.", "Descartar", () -> {
                        try { rascunho.limpar(); salao.mesas(); } catch (IllegalStateException erro) { erro(erro); }
                    });
            return true;
        });
        barra.getMenu().add("Meu rascunho").setOnMenuItemClickListener(item -> {
            if (!ocupado) retomar();
            return true;
        });
        barra.getMenu().add("Atualizar").setOnMenuItemClickListener(item -> { if (!ocupado) atualizar(); return true; });
        barra.getMenu().add("Sair").setOnMenuItemClickListener(item -> { if (!ocupado) sair(); return true; });

        if (rascunho.pendente()) retomar();
        else if (anterior != null) {
            mesaId = anterior.getLong("mesaId");
            comandaId = anterior.getLong("comanda");
            mesaNumero = anterior.getInt("mesa");
            pagina = Pagina.valueOf(anterior.getString("pagina", "MESAS"));
            if ((pagina == Pagina.RESUMO || pagina == Pagina.CARDAPIO) && comandaId == 0) pagina = Pagina.MESAS;
            atualizar();
        } else salao.mesas();
    }

    private void retomar() {
        if (rascunho.itens().length() == 0) {
            aviso("Nenhum pedido em montagem.");
            return;
        }
        comandaId = rascunho.comanda();
        mesaNumero = rascunho.mesa();
        montagem.resumo();
    }

    private void sair() {
        confirmar("Encerrar sessão?", "O rascunho ficará disponível no próximo acesso deste funcionário.", "Sair", () ->
            executar(() -> api.alterar("/sair", new JSONObject()), resposta -> voltarAoLogin(),
                erro -> confirmar("Sem confirmação do servidor", "Deseja sair deste aparelho mesmo assim?", "Sair", this::voltarAoLogin)));
    }

    private void voltarAoLogin() {
        startActivity(new Intent(this, Login.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    void mostrar(Pagina destino, String titulo, String subtitulo) {
        pagina = destino;
        barra.setTitle(titulo);
        voltar.setVisible(destino != Pagina.MESAS);
        descartar.setVisible(destino == Pagina.RESUMO && !rascunho.pendente() && rascunho.itens().length() > 0);
        TextView apoio = findViewById(R.id.subtitulo);
        apoio.setText(subtitulo);
        apoio.setVisibility(subtitulo.isEmpty() ? View.GONE : View.VISIBLE);
        findViewById(R.id.filtros).setVisibility(destino == Pagina.CARDAPIO ? View.VISIBLE : View.GONE);
        acao(R.id.principal, "", null);
        acao(R.id.secundaria, "", null);
        linhas(new ArrayList<>(), "");
    }

    void linhas(ArrayList<Linhas.Linha> dados, String semResultados) {
        boolean mesas = pagina == Pagina.MESAS;
        AbsListView lista = findViewById(mesas ? R.id.grade : R.id.lista);
        findViewById(mesas ? R.id.lista : R.id.grade).setVisibility(View.GONE);
        lista.setVisibility(View.VISIBLE);
        Linhas adaptador = new Linhas(this, dados, mesas);
        lista.setAdapter(adaptador);
        lista.setOnItemClickListener((pai, vista, indice, id) -> {
            Runnable abrir = adaptador.getItem(indice).selecionar;
            if (!ocupado && abrir != null) abrir.run();
        });
        vazio.setText(semResultados);
        vazio.setVisibility(dados.isEmpty() ? View.VISIBLE : View.GONE);
    }

    void acao(int id, String texto, Runnable executar) {
        Button botao = findViewById(id);
        botao.setText(texto);
        botao.setVisibility(executar == null ? View.GONE : View.VISIBLE);
        botao.setOnClickListener(v -> { if (!ocupado && executar != null) executar.run(); });
    }

    void confirmar(String titulo, String descricao, String rotulo, Runnable confirmado) {
        new AlertDialog.Builder(this).setTitle(titulo).setMessage(descricao)
                .setNegativeButton("Voltar", null).setPositiveButton(rotulo, (d, v) -> {
                    if (!ocupado) confirmado.run();
                }).show();
    }

    <T> void executar(Operacao<T> trabalho, Receber<T> sucesso) {
        executar(trabalho, sucesso, this::erro);
    }

    <T> void executar(Operacao<T> trabalho, Receber<T> sucesso, Receber<Exception> falha) {
        if (ocupado || isFinishing() || isDestroyed()) return;
        ocupado = true;
        findViewById(R.id.progresso).setVisibility(View.VISIBLE);
        rede.execute(() -> {
            try {
                T resultado = trabalho.executar();
                principal.post(() -> terminar(() -> sucesso.aceitar(resultado)));
            } catch (Exception problema) {
                principal.post(() -> terminar(() -> {
                    if (problema instanceof Api.Recusa && ((Api.Recusa) problema).status == 401) {
                        aviso("Sessão encerrada. Entre novamente.");
                        voltarAoLogin();
                    } else falha.aceitar(problema);
                }));
            }
        });
    }

    private interface Conclusao { void executar() throws Exception; }
    private void terminar(Conclusao conclusao) {
        if (isFinishing() || isDestroyed()) return;
        ocupado = false;
        findViewById(R.id.progresso).setVisibility(View.GONE);
        try { conclusao.executar(); } catch (Exception problema) { erro(problema); }
        if (!ocupado && eventoPendente && visivel && !isFinishing()) {
            eventoPendente = false;
            atualizarPorEvento();
        }
    }

    void erro(Exception erro) {
        String mensagem = erro instanceof Api.Recusa || erro instanceof IllegalStateException
                ? erro.getMessage() : "Não foi possível consultar o Backend. Confira a rede e tente atualizar.";
        aviso(mensagem);
        if (vazio.getVisibility() == View.VISIBLE) vazio.setText(mensagem);
    }

    void aviso(String mensagem) { Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show(); }

    private void atualizarPorEvento() {
        if (!visivel || isFinishing() || isDestroyed()) return;
        if (ocupado) { eventoPendente = true; return; }
        if (pagina == Pagina.CARDAPIO) montagem.atualizarProdutos();
        else if (pagina != Pagina.RESUMO) atualizar();
    }

    private void atualizar() {
        switch (pagina) {
            case MESAS: salao.mesas(); break;
            case COMANDA: if (comandaId > 0) salao.comanda(); else salao.mesaLivre(); break;
            case CARDAPIO: montagem.cardapio(); break;
            case RESUMO: montagem.resumo(); break;
            case PEDIDOS: salao.pedidos(); break;
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (salao == null) return;
        visivel = true;
        if (voltouDoSegundoPlano) {
            voltouDoSegundoPlano = false;
            atualizarPorEvento();
        }
        notificacoes = new Notificacoes(configuracao, new Notificacoes.Ouvinte() {
            @Override public void atualizar() { principal.post(Atendimento.this::atualizarPorEvento); }
            @Override public void conexao(boolean conectada) {
                principal.post(() -> {
                    if (!visivel || isDestroyed()) return;
                    situacao.setText(conectada ? "" : "Notificações desconectadas. Use Atualizar no menu.");
                    situacao.setVisibility(conectada ? View.GONE : View.VISIBLE);
                });
            }
        });
    }

    @Override protected void onStop() {
        visivel = false;
        voltouDoSegundoPlano = true;
        if (notificacoes != null) { notificacoes.encerrar(); notificacoes = null; }
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle estado) {
        super.onSaveInstanceState(estado);
        if (pagina != null) estado.putString("pagina", pagina.name());
        estado.putLong("mesaId", mesaId);
        estado.putLong("comanda", comandaId);
        estado.putInt("mesa", mesaNumero);
    }

    @Override public void onBackPressed() {
        if (salao == null) { super.onBackPressed(); return; }
        if (ocupado) { aviso("Aguarde a resposta do servidor."); return; }
        switch (pagina) {
            case MESAS: sair(); break;
            case CARDAPIO: salao.comanda(); break;
            case RESUMO: if (rascunho.pendente()) salao.mesas(); else montagem.cardapio(); break;
            default: salao.mesas();
        }
    }

    @Override protected void onDestroy() {
        principal.removeCallbacksAndMessages(null);
        rede.shutdownNow();
        super.onDestroy();
    }
}
