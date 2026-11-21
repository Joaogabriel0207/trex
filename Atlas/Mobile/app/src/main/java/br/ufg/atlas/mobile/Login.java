package br.ufg.atlas.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toolbar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public final class Login extends Activity {
    private final ExecutorService rede = Executors.newSingleThreadExecutor();
    private Configuracao configuracao;
    private Button entrar;
    private EditText codigo;
    private EditText senha;
    private TextView aviso;

    @Override protected void onCreate(Bundle anterior) {
        super.onCreate(anterior);
        getWindow().setStatusBarColor(getColor(R.color.login));
        getWindow().setNavigationBarColor(getColor(R.color.login));
        getWindow().getDecorView().setSystemUiVisibility(0);
        setContentView(R.layout.login);
        configuracao = new Configuracao(this);
        codigo = findViewById(R.id.codigo);
        senha = findViewById(R.id.senha);
        aviso = findViewById(R.id.aviso);
        entrar = findViewById(R.id.entrar);
        entrar.setOnClickListener(v -> entrar());
        senha.setOnEditorActionListener((v, acao, evento) -> {
            if (acao != EditorInfo.IME_ACTION_DONE) return false;
            entrar();
            return true;
        });
        Toolbar barra = findViewById(R.id.barra);
        barra.getMenu().add("Configurações de rede").setOnMenuItemClickListener(item -> {
            if (entrar.isEnabled()) configuracao.editar(this);
            return true;
        });
    }

    private void entrar() {
        if (!entrar.isEnabled()) return;
        String base = configuracao.servidor();
        String identificacao = codigo.getText().toString().trim();
        String segredo = senha.getText().toString();
        if (base.isEmpty()) {
            aviso.setText("Configure a rede no menu antes do primeiro acesso.");
            return;
        }
        if (identificacao.isEmpty() || segredo.isEmpty()) {
            aviso.setText("Informe o código e a senha.");
            return;
        }
        entrar.setEnabled(false);
        aviso.setText("Entrando…");
        rede.execute(() -> {
            try {
                JSONObject credenciais = new JSONObject();
                credenciais.put("codigo", identificacao);
                credenciais.put("senha", segredo);
                JSONObject sessao = new Api(base, "").alterar("/login", credenciais);
                String token = sessao.getString("token");
                if (!"GARCOM".equals(sessao.getString("cargo"))) {
                    new Api(base, token).alterar("/sair", new JSONObject());
                    resposta("Este aplicativo requer um funcionário com cargo de garçom.");
                    return;
                }
                String funcionario = sessao.getString("codigo");
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    senha.setText("");
                    Intent proxima = new Intent(this, Atendimento.class);
                    proxima.putExtra("servidor", base).putExtra("codigo", funcionario).putExtra("token", token);
                    startActivity(proxima);
                    finish();
                });
            } catch (Exception erro) {
                resposta(erro instanceof Api.Recusa ? erro.getMessage() : "Não foi possível entrar. Confira a rede e o endereço configurado.");
            }
        });
    }

    private void resposta(String mensagem) {
        runOnUiThread(() -> {
            if (isDestroyed() || isFinishing()) return;
            entrar.setEnabled(true);
            aviso.setText(mensagem);
        });
    }

    @Override protected void onDestroy() {
        rede.shutdownNow();
        super.onDestroy();
    }
}
