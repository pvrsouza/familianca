package br.com.familianca.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SaldoRequest(
        @Schema(description = "Saldo somado. Pode ser negativo. Obrigatório sem `porLocal`; com ele, é ignorado.")
        BigDecimal valor,
        @Schema(description = "Saldo de cada lugar (conta, poupança, cofrinho...) no mesmo dia; o total é a soma.")
        @Valid @Size(max = 50) List<PorLocal> porLocal,
        @Schema(description = "Ex.: quais contas entraram na soma.") @Size(max = 200) String observacao,
        @Schema(description = """
                Dia a que o saldo se refere (ex.: hoje), dentro do mês e não no futuro. Sem ele, o valor é o do dia 1º.
                Com ele, o fluxo usa como dia 1º o valor sem o que foi lançado de 1º até esse dia.""")
        LocalDate dataReferencia) {

    public record PorLocal(@NotNull Long localId, @Schema(description = "Pode ser negativo.") @NotNull BigDecimal valor) {
    }
}
