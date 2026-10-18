package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.LocalSaldo;
import br.com.familianca.domain.model.TipoLocalSaldo;

public record LocalSaldoResponse(Long id, String nome, TipoLocalSaldo tipo, Long membroId, boolean ativo) {

    public static LocalSaldoResponse de(LocalSaldo l) {
        return new LocalSaldoResponse(l.id(), l.nome(), l.tipo(), l.membroId(), l.ativo());
    }
}
