package br.com.familianca.adapter.in.seguranca;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Até N tentativas de login por IP + e-mail a cada janela (padrão 5 em 15 min); login certo zera a contagem. O
 * cadastro e o aceite de convite usam a mesma regra, por IP. O login tem ainda um limite só por IP (padrão 30 em
 * 15 min, para qualquer e-mail), que nem o login certo devolve: {@link #tentarPorIp}.
 * Buckets em memória (uma instância da aplicação), descartados após uma janela sem uso e limitados em quantidade,
 * para que e-mails aleatórios não esgotem a memória.
 */
@Component
public class LimiteTentativasLogin {

    private final SegurancaProperties.Login config;
    private final Cache<String, Bucket> buckets;
    private final Cache<String, LimiteIp> bucketsIp;

    /** Bucket do IP e se o estouro atual já foi avisado (para auditar uma vez, não a cada tentativa do robô). */
    private record LimiteIp(Bucket bucket, AtomicBoolean avisado) {
    }

    /** Resultado do limite por IP. */
    public enum ResultadoIp {
        PERMITIDO,
        /** Primeira recusa desta rodada de estouro: o chamador audita. */
        BLOQUEADO_AGORA,
        BLOQUEADO
    }

    public LimiteTentativasLogin(SegurancaProperties propriedades) {
        this.config = propriedades.login();
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(config.janela())
                .maximumSize(100_000)
                .build();
        this.bucketsIp = Caffeine.newBuilder()
                .expireAfterAccess(config.janela())
                .maximumSize(100_000)
                .build();
    }

    /** Consome uma tentativa do IP, qualquer que seja o e-mail; nunca é devolvida (nem no login certo). */
    public ResultadoIp tentarPorIp(String ip) {
        LimiteIp limite = bucketsIp.get(ip, k -> new LimiteIp(novoBucket(config.maxTentativasIp()), new AtomicBoolean()));
        if (limite.bucket().tryConsume(1)) {
            limite.avisado().set(false);
            return ResultadoIp.PERMITIDO;
        }
        return limite.avisado().compareAndSet(false, true) ? ResultadoIp.BLOQUEADO_AGORA : ResultadoIp.BLOQUEADO;
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
        bucketsIp.invalidateAll();
    }

    public long segundosParaNovaTentativa() {
        return config.janela().toSeconds();
    }

    private Bucket novoBucket() {
        return novoBucket(config.maxTentativas());
    }

    private Bucket novoBucket(int maximo) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(maximo)
                        .refillIntervally(maximo, config.janela()).build())
                .build();
    }

    private static String chave(String ip, String email) {
        return ip + "|" + email;
    }
}
