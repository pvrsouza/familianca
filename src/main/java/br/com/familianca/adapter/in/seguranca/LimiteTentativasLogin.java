package br.com.familianca.adapter.in.seguranca;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

/**
 * Até N tentativas de login por IP + e-mail a cada janela (padrão 5 em 15 min); login certo zera a contagem. O
 * cadastro e o aceite de convite usam a mesma regra, por IP.
 * Buckets em memória (uma instância da aplicação), descartados após uma janela sem uso e limitados em quantidade,
 * para que e-mails aleatórios não esgotem a memória.
 */
@Component
public class LimiteTentativasLogin {

    private final SegurancaProperties.Login config;
    private final Cache<String, Bucket> buckets;

    public LimiteTentativasLogin(SegurancaProperties propriedades) {
        this.config = propriedades.login();
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(config.janela())
                .maximumSize(100_000)
                .build();
    }

    /** Consome uma tentativa; false = limite atingido (responder 429). */
    public boolean tentar(String ip, String email) {
        return buckets.get(chave(ip, email), k -> novoBucket()).tryConsume(1);
    }

    public void liberar(String ip, String email) {
        buckets.invalidate(chave(ip, email));
    }

    /** Zera todos os limites (testes de integração, que compartilham a mesma instância). */
    public void liberarTodos() {
        buckets.invalidateAll();
    }

    public long segundosParaNovaTentativa() {
        return config.janela().toSeconds();
    }

    private Bucket novoBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(config.maxTentativas())
                        .refillIntervally(config.maxTentativas(), config.janela()).build())
                .build();
    }

    private static String chave(String ip, String email) {
        return ip + "|" + email;
    }
}
