package br.ufg.atlas.servidor;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class Senhas {
    private static final int ITERACOES = 600_000;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Senhas() {}

    static String gerar(String senha) {
        if (senha == null || senha.length() < 8 || senha.length() > 128) {
            throw new IllegalArgumentException("A senha deve ter entre 8 e 128 caracteres.");
        }
        byte[] sal = new byte[16];
        ALEATORIO.nextBytes(sal);
        Base64.Encoder base64 = Base64.getEncoder();
        return ITERACOES + ":" + base64.encodeToString(sal) + ":" +
                base64.encodeToString(derivar(senha, sal, ITERACOES));
    }

    static boolean conferir(String senha, String registro) {
        if (senha == null || senha.length() > 128 || registro == null) return false;
        String[] campos = registro.split(":", -1);
        if (campos.length != 3) return false;
        try {
            int iteracoes = Integer.parseInt(campos[0]);
            if (iteracoes < 1 || iteracoes > 2_000_000) return false;
            byte[] esperado = Base64.getDecoder().decode(campos[2]);
            byte[] sal = Base64.getDecoder().decode(campos[1]);
            return MessageDigest.isEqual(esperado, derivar(senha, sal, iteracoes));
        } catch (IllegalArgumentException erro) {
            return false;
        }
    }

    private static byte[] derivar(String senha, byte[] sal, int iteracoes) {
        char[] caracteres = senha.toCharArray();
        PBEKeySpec chave = new PBEKeySpec(caracteres, sal, iteracoes, 256);
        Arrays.fill(caracteres, '\0');
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(chave).getEncoded();
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("Falha no provedor de criptografia do Java.", erro);
        } finally {
            chave.clearPassword();
        }
    }
}
