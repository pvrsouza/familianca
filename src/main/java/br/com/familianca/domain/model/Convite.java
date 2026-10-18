package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;

import java.time.OffsetDateTime;

import static br.com.familianca.domain.Validacoes.obrigatorio;

/**
 * Convite para entrar na família, aceito por link. Só o hash do token é guardado. Vale uma vez, até expirar, e só
 * para o e-mail convidado.
 */
public class Convite {

    private final Long id;
    private final Long familiaId;
    private final String email;
    private final PapelFamilia papel;
    private final String tokenHash;
    private StatusConvite status;
    private final OffsetDateTime expiraEm;
    private final Long convidadoPor;

    private Convite(Long id, Long familiaId, String email, PapelFamilia papel, String tokenHash, StatusConvite status,
                    OffsetDateTime expiraEm, Long convidadoPor) {
        this.id = id;
        this.familiaId = obrigatorio(familiaId, "família");
        this.email = Usuario.normalizarEmail(obrigatorio(email, "email"));
        Usuario.validarEmail(this.email);
        this.papel = obrigatorio(papel, "papel");
        this.tokenHash = obrigatorio(tokenHash, "token");
        this.status = obrigatorio(status, "status");
        this.expiraEm = obrigatorio(expiraEm, "expiração");
        this.convidadoPor = obrigatorio(convidadoPor, "quem convidou");
    }

    public static Convite novo(Long familiaId, String email, PapelFamilia papel, String tokenHash,
                               OffsetDateTime expiraEm, Long convidadoPor) {
        return new Convite(null, familiaId, email, papel, tokenHash, StatusConvite.PENDENTE, expiraEm, convidadoPor);
    }

    public static Convite reconstituir(Long id, Long familiaId, String email, PapelFamilia papel, String tokenHash,
                                       StatusConvite status, OffsetDateTime expiraEm, Long convidadoPor) {
        return new Convite(id, familiaId, email, papel, tokenHash, status, expiraEm, convidadoPor);
    }

    public boolean valido(OffsetDateTime agora) {
        return status == StatusConvite.PENDENTE && agora.isBefore(expiraEm);
    }

    /** A conta criada no aceite usa o e-mail do convite: quem tem o link entra como a pessoa convidada. */
    public void aceitar(OffsetDateTime agora) {
        if (!valido(agora)) {
            throw new RegraNegocioException("Convite expirado ou já utilizado");
        }
        status = StatusConvite.ACEITO;
    }

    public void cancelar() {
        if (status != StatusConvite.PENDENTE) {
            throw new RegraNegocioException("Só convites pendentes podem ser cancelados");
        }
        status = StatusConvite.CANCELADO;
    }

    public Long id() { return id; }
    public Long familiaId() { return familiaId; }
    public String email() { return email; }
    public PapelFamilia papel() { return papel; }
    public String tokenHash() { return tokenHash; }
    public StatusConvite status() { return status; }
    public OffsetDateTime expiraEm() { return expiraEm; }
    public Long convidadoPor() { return convidadoPor; }
}
