package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.Percentual;
import br.com.familianca.domain.vo.SerieMensal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Conversões entre os VOs do domínio e os números do JSON. */
public final class Valores {

    private Valores() {
    }

    public static BigDecimal valor(Dinheiro d) {
        return d != null ? d.valor() : null;
    }

    public static BigDecimal valor(Percentual p) {
        return p != null ? p.valor() : null;
    }

    public static Dinheiro dinheiro(BigDecimal v) {
        return v != null ? Dinheiro.of(v) : null;
    }

    public static Percentual percentual(BigDecimal v) {
        return v != null ? Percentual.of(v) : null;
    }

    public static List<BigDecimal> meses(SerieMensal serie) {
        return serie.meses().stream().map(Dinheiro::valor).toList();
    }

    /** Lista com nulos (meses sem percentual). */
    public static List<BigDecimal> percentuais(List<Percentual> lista) {
        List<BigDecimal> valores = new ArrayList<>(lista.size());
        lista.forEach(p -> valores.add(valor(p)));
        return valores;
    }
}
