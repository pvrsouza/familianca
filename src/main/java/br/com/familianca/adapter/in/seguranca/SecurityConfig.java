package br.com.familianca.adapter.in.seguranca;

import br.com.familianca.adapter.in.web.SpaController;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * Negar por padrão: só o login, o token CSRF, o cadastro, o link de convite, o health e os arquivos do front são
 * públicos. Sessão de servidor
 * (Spring Session JDBC) identificada por cookie; detalhes da sessão em {@link SessaoConfig}.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SegurancaProperties.class)
public class SecurityConfig {

    /** Front servido pelo próprio Spring Boot (estilos inline do Vue/Swagger; ícone em data: URI). */
    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; font-src 'self' data:; connect-src 'self'; object-src 'none'; "
            + "base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    /** Recursos do navegador que o app não usa ficam bloqueados (inclusive para conteúdo embutido). */
    private static final String PERMISSOES = "camera=(), microphone=(), geolocation=(), payment=(), usb=(), "
            + "interest-cohort=()";

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, SegurancaProperties propriedades, CsrfTokenRepository csrf,
                                SecurityContextRepository contextos, RequestMatcher autenticadaPorHeader,
                                LimiteRequisicoesApi limiteApi) throws Exception {
        http
                // Antes de carregar o usuário da sessão: requisição acima do limite nem chega ao banco.
                .addFilterBefore(limiteApi.filtro(), SecurityContextHolderFilter.class)
                .addFilterBefore(new DuracaoMaximaSessao(propriedades.sessao().duracaoMaxima(), autenticadaPorHeader),
                        SecurityContextHolderFilter.class)
                .authorizeHttpRequests(a -> a
                        // Página de erro e forwards internos não são entrada nova: a requisição original já passou aqui.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/cadastro").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/convites/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/convites/*/aceite").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/favicon.ico", "/favicon.svg")
                        .permitAll()
                        // Rotas do front: devolvem o index.html (SpaController); os dados continuam na /api.
                        .requestMatchers(HttpMethod.GET, SpaController.ROTAS).permitAll()
                        .anyRequest().denyAll())
                .csrf(c -> c
                        .spa() // antes do repositório: spa() troca o repositório pelo padrão
                        .csrfTokenRepository(csrf)
                        .ignoringRequestMatchers(autenticadaPorHeader))
                .cors(c -> c.configurationSource(cors(propriedades.corsOrigens())))
                .securityContext(c -> c.securityContextRepository(contextos))
                // Sessão só nasce no login; requisição anônima não cria sessão no banco.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(c -> c.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(RespostasSeguranca.naoAutenticado())
                        .accessDeniedHandler(RespostasSeguranca.acessoNegado()))
                .logout(l -> l
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies(propriedades.sessao().cookieNome())
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .headers(h -> {
                    h.contentSecurityPolicy(c -> c.policyDirectives(CSP));
                    h.frameOptions(f -> f.deny());
                    h.contentTypeOptions(c -> { });
                    h.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN));
                    h.permissionsPolicyHeader(p -> p.policy(PERMISSOES));
                    if (propriedades.hsts()) {
                        h.httpStrictTransportSecurity(s -> s.includeSubDomains(true).maxAgeInSeconds(
                                Duration.ofDays(365).toSeconds()));
                    } else {
                        h.httpStrictTransportSecurity(s -> s.disable());
                    }
                });
        return http.build();
    }

    /** Cookie XSRF-TOKEN legível pelo JavaScript do front, que devolve o valor no header X-XSRF-TOKEN. */
    @Bean
    CsrfTokenRepository csrfTokenRepository(SegurancaProperties propriedades) {
        CookieCsrfTokenRepository repositorio = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repositorio.setCookieCustomizer(c -> c.secure(propriedades.sessao().cookieSeguro()).sameSite("Strict").path("/"));
        return repositorio;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Aplicada no login: novo id de sessão (contra session fixation) e novo token CSRF. */
    @Bean
    SessionAuthenticationStrategy estrategiaSessaoLogin(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Publica os eventos de login (ok/falha) que a {@link AuditoriaSeguranca} registra. */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService usuarios, PasswordEncoder senhas,
                                                ApplicationEventPublisher eventos) {
        DaoAuthenticationProvider provedor = new DaoAuthenticationProvider(usuarios);
        provedor.setPasswordEncoder(senhas);
        ProviderManager gerenciador = new ProviderManager(provedor);
        gerenciador.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(eventos));
        return gerenciador;
    }

    /** Publica os acessos negados (filtros e @PreAuthorize) para a auditoria. */
    @Bean
    AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher eventos) {
        return new SpringAuthorizationEventPublisher(eventos);
    }

    /** Sem origens configuradas (front na mesma origem ou proxy do Vite), nenhuma chamada de outra origem passa. */
    private static CorsConfigurationSource cors(List<String> configuradas) {
        List<String> origens = configuradas.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        if (origens.isEmpty()) {
            return fonte;
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origens);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));
        fonte.registerCorsConfiguration("/api/**", config);
        return fonte;
    }
}
