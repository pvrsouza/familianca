package br.com.familianca.domain.model;

import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.Percentual;
import br.com.familianca.domain.vo.SerieMensal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Carteira de investimentos no ano. Saldo do mês = saldo anterior + rendimento + aporte, ou o saldo do extrato
 * quando informado (o rendimento então é a diferença). Calculada por
 * {@link br.com.familianca.domain.service.CalculadoraInvestimentos}.
 *
 * @param rentabilidadeMensal rendimento ÷ saldo do fim do mês anterior (nulo sem saldo anterior)
 * @param rentabilidadeAno    composta: (1 + r₁) × … × (1 + r₁₂) − 1
 */
public record CarteiraAnual(int ano, List<Posicao> investimentos, Dinheiro saldoInicial, SerieMensal aportes,
                            SerieMensal rendimentos, SerieMensal saldos, List<Percentual> rentabilidadeMensal,
                            Percentual rentabilidadeAno) {

    public CarteiraAnual {
        rentabilidadeMensal = Collections.unmodifiableList(new ArrayList<>(rentabilidadeMensal));
    }

    /**
     * @param abertura saldo que já existia quando o investimento começou a ser acompanhado (primeiro saldo informado,
     *                 menos o aporte do mês): nem rendimento nem aporte; nula = começou do zero
     */
    public record Posicao(Investimento investimento, Dinheiro saldoInicial, SerieMensal aportes,
                          SerieMensal rendimentos, SerieMensal saldos, List<Percentual> rentabilidadeMensal,
                          Percentual rentabilidadeAno, Abertura abertura) {

        public Posicao {
            rentabilidadeMensal = Collections.unmodifiableList(new ArrayList<>(rentabilidadeMensal));
        }
    }

    public record Abertura(Competencia competencia, Dinheiro valor) {
    }
}
