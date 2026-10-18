package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.Assinatura;
import br.com.familianca.domain.model.Plano;
import br.com.familianca.domain.model.StatusAssinatura;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AssinaturaDto(
        @NotNull Plano plano,
        @NotNull StatusAssinatura status,
        @Schema(description = "Fim do teste ou do período pago. Nulo = sem vencimento.") LocalDate validaAte) {

    public static AssinaturaDto de(Assinatura a) {
        return new AssinaturaDto(a.plano(), a.status(), a.validaAte());
    }

    public Assinatura paraDominio() {
        return new Assinatura(plano, status, validaAte);
    }
}
