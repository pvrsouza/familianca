package br.com.familianca.adapter.in.seguranca;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Teto de requisições por IP em toda a /api (padrão 300 por minuto), contra robôs e varreduras; o limite fino de
 * tentativas de login fica em {@link LimiteTentativasLogin}. Buckets em memória, como lá (uma instância da aplicação).
 * O filtro entra na cadeia do Spring Security (SecurityConfig); o componente existe para os testes zerarem a contagem.
 */
@Component
public class LimiteRequisicoesApi {

    private final SegurancaProperties.LimiteApi config;
    private final Cache<String, Bucket> buckets;

    @Autowired
    public LimiteRequisicoesApi(SegurancaProperties propriedades) {
        this(propriedades.limiteApi());
    }

    LimiteRequisicoesApi(SegurancaProperties.LimiteApi config) {
        this.config = config;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(config.janela())
                .maximumSize(100_000)
                .build();
    }

    /** Consome uma requisição do IP; false = limite atingido. */
    public boolean tentar(String ip) {
        return buckets.get(ip, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(config.maxRequisicoes())
                        .refillGreedy(config.maxRequisicoes(), config.janela()).build())
                .build()).tryConsume(1);
    }

    /** Zera todos os limites (testes de integração, que compartilham a mesma instância). */
    public void liberarTodos() {
        buckets.invalidateAll();
    }

    Filter filtro() {
        return new OncePerRequestFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                return !request.getRequestURI().startsWith("/api/");
            }

            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                if (!tentar(request.getRemoteAddr())) {
                    RespostasSeguranca.muitasRequisicoes(response, config.janela().toSeconds());
                    return;
                }
                chain.doFilter(request, response);
            }
        };
    }
}
