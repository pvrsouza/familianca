package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.Investimento;
import br.com.familianca.domain.model.TipoInvestimento;

public record InvestimentoResponse(Long id, String nome, TipoInvestimento tipo, boolean ativo) {

    public static InvestimentoResponse de(Investimento i) {
        return new InvestimentoResponse(i.id(), i.nome(), i.tipo(), i.ativo());
    }
}
