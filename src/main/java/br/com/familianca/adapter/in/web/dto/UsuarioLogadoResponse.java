package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.adapter.in.seguranca.UsuarioAutenticado;

/** papel: plataforma (ADMIN/USER); papelFamilia: DONO/MEMBRO. */
public record UsuarioLogadoResponse(Long id, String nome, String email, String papel, Long familiaId, Long membroId,
                                    String papelFamilia) {

    public static UsuarioLogadoResponse de(UsuarioAutenticado u) {
        return new UsuarioLogadoResponse(u.id(), u.nome(), u.email(), u.papel(), u.familiaId(), u.membroId(),
                u.papelFamilia());
    }
}
