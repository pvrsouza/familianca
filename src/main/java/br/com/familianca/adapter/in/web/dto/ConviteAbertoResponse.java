package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.AceitarConvite.ConviteAberto;
import br.com.familianca.domain.model.PapelFamilia;

import java.time.OffsetDateTime;

public record ConviteAbertoResponse(String familia, String email, PapelFamilia papel, OffsetDateTime expiraEm) {

    public static ConviteAbertoResponse de(ConviteAberto c) {
        return new ConviteAbertoResponse(c.familia(), c.email(), c.papel(), c.expiraEm());
    }
}
