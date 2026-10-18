package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;

public record MembroResponse(Long id, Long usuarioId, String nome, String email, PapelFamilia papel, boolean ativo) {

    public static MembroResponse de(Membro m) {
        return new MembroResponse(m.id(), m.usuarioId(), m.nome(), m.email(), m.papel(), m.ativo());
    }
}
