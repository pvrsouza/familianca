package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.GerenciarRecorrencias.ContaDoMes;
import br.com.familianca.application.port.in.GerenciarRecorrencias.ContasDoMes;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ContasDoMesResponse(String competencia,
                                  @Schema(description = "Contas que alguém precisa pagar (boleto/Pix ou forma não "
                                          + "informada com vencimento).")
                                  List<Conta> aPagar,
                                  @Schema(description = "Débito automático e cartão: saem sozinhas, só conferir.")
                                  List<Conta> paraConferir,
                                  @Schema(description = "Só no mês atual: meses dos últimos 12 com conta a pagar vencida "
                                          + "e não paga, mais antigos primeiro.")
                                  List<MesEmAberto> anterioresEmAberto) {

    public record Conta(String competencia, BigDecimal valorPrevisto,
                        @Schema(description = "Vencimento desta ocorrência; nulo = sem vencimento.")
                        LocalDate vencimento,
                        RecorrenciaResponse recorrencia,
                        @Schema(description = "Lançamento que confirmou a conta; nulo = ainda não paga.")
                        LancamentoResponse pagamento) {

        static Conta de(ContaDoMes c) {
            return new Conta(c.competencia().toString(), c.valorPrevisto().valor(), c.vencimento(),
                    RecorrenciaResponse.de(c.recorrencia()),
                    c.pagamento() != null ? LancamentoResponse.de(c.pagamento()) : null);
        }
    }

    public record MesEmAberto(String competencia, int contas) {
    }

    public static ContasDoMesResponse de(ContasDoMes c) {
        return new ContasDoMesResponse(c.competencia().toString(),
                c.aPagar().stream().map(Conta::de).toList(),
                c.paraConferir().stream().map(Conta::de).toList(),
                c.anterioresEmAberto().stream()
                        .map(m -> new MesEmAberto(m.competencia().toString(), m.contas())).toList());
    }
}
