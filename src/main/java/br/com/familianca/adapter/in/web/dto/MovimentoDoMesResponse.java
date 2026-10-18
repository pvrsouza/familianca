package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.MovimentoDoMes;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Lançado do dia 1º do mês até `ate`, inclusive. Saldo do dia 1º = saldo em `ate` − recebido + pago.")
public record MovimentoDoMesResponse(LocalDate ate, BigDecimal recebido, BigDecimal pago) {

    public static MovimentoDoMesResponse de(MovimentoDoMes m) {
        return new MovimentoDoMesResponse(m.ate(), m.recebido().valor(), m.pago().valor());
    }
}
