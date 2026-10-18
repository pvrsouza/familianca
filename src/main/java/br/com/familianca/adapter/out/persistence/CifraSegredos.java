package br.com.familianca.adapter.out.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * Cifra o que não pode ficar legível num dump do banco (segredo do MFA): AES-256-GCM, IV aleatório por valor, chave
 * derivada (PBKDF2) de {@code familianca.seguranca.mfa.chave} (env MFA_CHAVE). Trocar a chave invalida os segredos
 * já gravados: quem tinha MFA precisa configurar de novo.
 *
 * <p>Sem chave (produção sem MFA_CHAVE), a cifra fica indisponível e o MFA desligado: a aplicação sobe normalmente.
 */
@Component
public class CifraSegredos {

    /** Sal fixo da derivação da chave (hex); a aleatoriedade por valor vem do IV do GCM. */
    private static final String SAL = "a3f1c94e5b7d20e8";

    private final TextEncryptor cifra;

    public CifraSegredos(@Value("${familianca.seguranca.mfa.chave:}") String chave) {
        this.cifra = chave == null || chave.isBlank() ? null : Encryptors.delux(chave, SAL);
    }

    public boolean disponivel() {
        return cifra != null;
    }

    public String cifrar(String texto) {
        return exigir().encrypt(texto);
    }

    public String decifrar(String cifrado) {
        return exigir().decrypt(cifrado);
    }

    /** Há MFA gravado mas a chave sumiu do ambiente: falha fechada (não deixa entrar sem o segundo fator). */
    private TextEncryptor exigir() {
        if (cifra == null) {
            throw new IllegalStateException("MFA_CHAVE não configurada: não é possível ler nem gravar segredos do MFA");
        }
        return cifra;
    }
}
