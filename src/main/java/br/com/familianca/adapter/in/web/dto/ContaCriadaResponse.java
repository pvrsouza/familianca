package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;

/** Conta criada (cadastro ou convite). O login vem depois, em POST /api/auth/login. */
public record ContaCriadaResponse(Long usuarioId, Long familiaId, Long membroId, String nome, String email,
                                  PapelFamilia papel) {

    public static ContaCriadaResponse de(Membro m) {
        return new ContaCriadaResponse(m.usuarioId(), m.familiaId(), m.id(), m.nome(), m.email(), m.papel());
    }
}
