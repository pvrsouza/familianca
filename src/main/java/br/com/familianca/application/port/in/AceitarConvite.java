package br.com.familianca.application.port.in;

import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;

import java.time.OffsetDateTime;

/** Fluxo público do link de convite (sem login): quem tem o token vê o convite e cria a conta na família. */
public interface AceitarConvite {

    /** @throws br.com.familianca.domain.exception.NaoEncontradoException token desconhecido, usado ou expirado */
    ConviteAberto consultar(String token);

    /** Cria o usuário com o e-mail do convite e o coloca na família com o papel convidado. */
    Membro aceitar(String token, String nome, String senha);

    record ConviteAberto(String familia, String email, PapelFamilia papel, OffsetDateTime expiraEm) {
    }
}
