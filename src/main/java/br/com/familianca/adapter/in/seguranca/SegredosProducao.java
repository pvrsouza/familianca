package br.com.familianca.adapter.in.seguranca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Em produção, avisa no log (WARN, no startup) os segredos ausentes, curtos ou com o valor de desenvolvimento. Só
 * avisa, não impede a subida: enquanto não há gerenciador de segredos, o deploy não pode depender disso
 * (docs/seguranca.md, requisito 12).
 */
@Component
@Profile("prod")
class SegredosProducao implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger("auditoria");

    static final String CHAVE_MFA_DESENVOLVIMENTO = "chave-local-de-desenvolvimento-nao-use-em-producao";
    private static final String SENHA_BANCO_DESENVOLVIMENTO = "familianca";
    private static final int TAMANHO_MINIMO = 32;

    private final String chaveMfa;
    private final String senhaBanco;
    private final String senhaFlyway;

    SegredosProducao(@Value("${familianca.seguranca.mfa.chave:}") String chaveMfa,
                     @Value("${spring.datasource.password:}") String senhaBanco,
                     @Value("${spring.flyway.password:}") String senhaFlyway) {
        this.chaveMfa = chaveMfa;
        this.senhaBanco = senhaBanco;
        this.senhaFlyway = senhaFlyway;
    }

    @Override
    public void afterPropertiesSet() {
        problemas().forEach(p -> log.warn("evento=SEGREDO_FRACO {}", p));
    }

    List<String> problemas() {
        List<String> problemas = new ArrayList<>();
        if (chaveMfa == null || chaveMfa.isBlank()) {
            problemas.add("MFA_CHAVE ausente: verificação em duas etapas desligada");
        } else if (chaveMfa.length() < TAMANHO_MINIMO || chaveMfa.equals(CHAVE_MFA_DESENVOLVIMENTO)) {
            problemas.add("MFA_CHAVE fraca (mínimo " + TAMANHO_MINIMO + " caracteres; gere com: openssl rand -hex 32)");
        }
        if (senhaBanco.isBlank() || senhaBanco.equals(SENHA_BANCO_DESENVOLVIMENTO)) {
            problemas.add("DB_PASSWORD ausente ou igual à de desenvolvimento");
        }
        if (senhaFlyway.isBlank() || senhaFlyway.equals(SENHA_BANCO_DESENVOLVIMENTO)) {
            problemas.add("FLYWAY_PASSWORD ausente ou igual à de desenvolvimento");
        }
        return problemas;
    }
}
