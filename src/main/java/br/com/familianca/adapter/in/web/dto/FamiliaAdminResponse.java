package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.AdministrarPlataforma.ResumoFamilia;

public record FamiliaAdminResponse(Long id, String nome, AssinaturaDto assinatura, PlanoResponse planoEmVigor,
                                   long membrosAtivos) {

    public static FamiliaAdminResponse de(ResumoFamilia r) {
        return new FamiliaAdminResponse(r.familia().id(), r.familia().nome(), AssinaturaDto.de(r.familia().assinatura()),
                PlanoResponse.de(r.planoEmVigor()), r.membrosAtivos());
    }
}
