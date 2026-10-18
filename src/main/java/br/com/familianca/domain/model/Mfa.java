package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.service.Totp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;

import static br.com.familianca.domain.Validacoes.obrigatorio;

/**
 * Segundo fator do usuário: aplicativo autenticador (TOTP). Nasce pendente na configuração e só passa a valer no
 * login depois de ativado com um código lido do aplicativo (prova de que o QR code foi escaneado).
 * ultimoPasso: passo do último código aceito, para o mesmo código não servir duas vezes.
 */
public class Mfa {

    private final Long usuarioId;
    private final String segredo;
    private boolean ativo;
    private Long ultimoPasso;

    private Mfa(Long usuarioId, String segredo, boolean ativo, Long ultimoPasso) {
        this.usuarioId = obrigatorio(usuarioId, "usuário");
        this.segredo = obrigatorio(segredo, "segredo");
        this.ativo = ativo;
        this.ultimoPasso = ultimoPasso;
    }

    public static Mfa novo(Long usuarioId) {
        return new Mfa(usuarioId, Totp.novoSegredo(), false, null);
    }

    public static Mfa reconstituir(Long usuarioId, String segredo, boolean ativo, Long ultimoPasso) {
        return new Mfa(usuarioId, segredo, ativo, ultimoPasso);
    }

    /** Confere o código do aplicativo; aceito, ele fica gasto. */
    public boolean verificar(String codigo, Instant agora) {
        OptionalLong passo = Totp.verificar(segredo, codigo == null ? null : codigo.trim(), agora.getEpochSecond(),
                ultimoPasso);
        passo.ifPresent(p -> ultimoPasso = p);
        return passo.isPresent();
    }

    public void ativar(String codigo, Instant agora) {
        if (ativo) {
            throw new RegraNegocioException("A verificação em duas etapas já está ativa");
        }
        if (!verificar(codigo, agora)) {
            throw new RegraNegocioException("Código inválido. Confira o horário do celular e tente o código atual.");
        }
        ativo = true;
    }

    /** Link otpauth:// que vira o QR code lido pelo aplicativo autenticador. */
    public String uri(String emissor, String conta) {
        String rotulo = codificar(emissor) + ":" + codificar(conta);
        return "otpauth://totp/" + rotulo + "?secret=" + segredo + "&issuer=" + codificar(emissor)
                + "&algorithm=SHA1&digits=" + Totp.DIGITOS + "&period=" + Totp.PASSO_SEGUNDOS;
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public Long usuarioId() { return usuarioId; }
    public String segredo() { return segredo; }
    public boolean ativo() { return ativo; }
    public Long ultimoPasso() { return ultimoPasso; }
}
