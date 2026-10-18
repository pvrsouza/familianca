package br.com.familianca.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import br.com.familianca.domain.model.Convite;
import br.com.familianca.domain.model.PapelFamilia;
import br.com.familianca.domain.model.StatusConvite;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConviteResponse(Long id, String email, PapelFamilia papel, StatusConvite status, OffsetDateTime expiraEm,
                              @Schema(description = "Só na criação: o token não é guardado e não aparece de novo.")
                              String token,
                              @Schema(description = "Só na criação: link para enviar à pessoa (WhatsApp, e-mail).")
                              String link) {

    public static ConviteResponse de(Convite c) {
        return new ConviteResponse(c.id(), c.email(), c.papel(), c.status(), c.expiraEm(), null, null);
    }

    public static ConviteResponse criado(Convite c, String token, String urlBase) {
        String base = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        return new ConviteResponse(c.id(), c.email(), c.papel(), c.status(), c.expiraEm(), token, base + "/" + token);
    }
}
