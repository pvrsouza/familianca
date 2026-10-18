package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.GerenciarRecorrencias.OcorrenciaPendente;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OcorrenciaPendenteResponse(String competencia, LocalDate data, BigDecimal valorPrevisto,
                                         RecorrenciaResponse recorrencia) {

    public static OcorrenciaPendenteResponse de(OcorrenciaPendente p) {
        return new OcorrenciaPendenteResponse(p.competencia().toString(), p.data(),
                p.recorrencia().recorrencia().valor().valor(), RecorrenciaResponse.de(p.recorrencia()));
    }
}
