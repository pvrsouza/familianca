package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.Percentual;

import static br.com.familianca.domain.Validacoes.obrigatorio;

/**
 * O que aconteceu com um investimento no mês.
 * - aporte: dinheiro colocado (negativo = resgate). Entra no fluxo de caixa como aplicação do mês.
 * - rentabilidade: % do mês sobre o saldo do mês anterior. Opcional.
 * - saldoInformado: saldo do extrato no fim do mês; prevalece sobre o calculado. Opcional.
 */
public record MovimentoInvestimento(Long investimentoId, Competencia competencia, Dinheiro aporte,
                                    Percentual rentabilidade, Dinheiro saldoInformado) {

    public MovimentoInvestimento {
        obrigatorio(investimentoId, "investimento");
        obrigatorio(competencia, "competência");
        aporte = aporte != null ? aporte : Dinheiro.ZERO;
        if (saldoInformado != null && saldoInformado.ehNegativo()) {
            throw new RegraNegocioException("O saldo informado não pode ser negativo");
        }
    }
}
