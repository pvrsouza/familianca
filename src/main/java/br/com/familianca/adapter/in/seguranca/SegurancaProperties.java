package br.com.familianca.adapter.in.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Configuração de segurança (`familianca.seguranca.*` no application.yaml; segredos por variável de ambiente).
 *
 * @param corsOrigens origens do front autorizadas a chamar a API com cookies; vazio = só a mesma origem
 * @param hsts        envia Strict-Transport-Security (perfil prod, atrás de HTTPS)
 * @param limiteApi   requisições por IP em toda a /api (além do limite de tentativas de login)
 */
@ConfigurationProperties("familianca.seguranca")
public record SegurancaProperties(
        @DefaultValue Sessao sessao,
        @DefaultValue Login login,
        @DefaultValue AdminInicial adminInicial,
        @DefaultValue List<String> corsOrigens,
        @DefaultValue("false") boolean hsts,
        @DefaultValue LimiteApi limiteApi) {

    /**
     * @param cookieSeguro         cookie de sessão só por HTTPS (true em produção)
     * @param timeoutWeb           inatividade até a sessão do navegador expirar
     * @param timeoutMobile        idem para o app mobile (sessão por header)
     * @param tokenHeaderHabilitado aceita o id de sessão no header X-Auth-Token (ver {@link SessaoConfig})
     * @param duracaoMaxima        tempo máximo desde o login, mesmo com uso contínuo (sessão do navegador)
     */
    public record Sessao(
            @DefaultValue("SESSION") String cookieNome,
            @DefaultValue("true") boolean cookieSeguro,
            @DefaultValue("30m") Duration timeoutWeb,
            @DefaultValue("30d") Duration timeoutMobile,
            @DefaultValue("false") boolean tokenHeaderHabilitado,
            @DefaultValue("12h") Duration duracaoMaxima) {
    }

    /** Limite de tentativas de login por IP + e-mail. */
    public record Login(
            @DefaultValue("5") int maxTentativas,
            @DefaultValue("15m") Duration janela) {
    }

    /** Teto de requisições por IP em toda a /api, contra abuso e varredura (429 com Retry-After). */
    public record LimiteApi(
            @DefaultValue("300") int maxRequisicoes,
            @DefaultValue("1m") Duration janela) {
    }

    /** ADMIN da plataforma criado no startup quando não existe nenhum usuário (opcional). */
    public record AdminInicial(
            @DefaultValue("Administrador") String nome,
            String email,
            String senha) {

        /** Nunca imprime a senha (ex.: em log de configuração). */
        @Override
        public String toString() {
            return "AdminInicial[nome=" + nome + "]";
        }
    }
}
