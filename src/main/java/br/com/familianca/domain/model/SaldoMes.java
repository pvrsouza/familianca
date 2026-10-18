package br.com.familianca.domain.model;

import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;

import static br.com.familianca.domain.Validacoes.obrigatorio;
import static br.com.familianca.domain.Validacoes.opcional;

/** Saldo em conta no início do mês ("sobra do mês anterior"), como a família conferiu nos bancos. Pode ser negativo. */
public record SaldoMes(Competencia competencia, Dinheiro valor, String observacao) {

    public SaldoMes {
        obrigatorio(competencia, "competência");
        obrigatorio(valor, "valor");
        observacao = opcional(observacao);
    }
}
