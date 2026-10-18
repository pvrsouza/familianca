package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.Plano;
import br.com.familianca.domain.model.Recurso;

import java.util.List;

public record PlanoResponse(Plano codigo, String rotulo, int limiteMembros, List<Recurso> recursos) {

    public static PlanoResponse de(Plano p) {
        return new PlanoResponse(p, p.rotulo(), p.limiteMembros(), p.recursos().stream().sorted().toList());
    }
}
