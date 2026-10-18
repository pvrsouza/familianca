package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static br.com.familianca.domain.Validacoes.obrigatorio;
import static br.com.familianca.domain.Validacoes.opcional;

/**
 * Saldo em conta somando onde está o dinheiro da família, como ela conferiu. Pode ser negativo. No fluxo vale sempre
 * como o saldo do dia 1º do mês (a "sobra do mês anterior").
 * - dataReferencia nula: o valor já é o do dia 1º, antes de qualquer conta ou receita do mês.
 * - com data (ex.: hoje): o valor é o saldo no fim daquele dia, e o do dia 1º sai desfazendo o que foi lançado de 1º
 *   até a data ({@link #noDiaPrimeiro}). Conta paga nesse período e lançada depois entra na conta sozinha.
 * - porLocal: o saldo item a item (conta, poupança, cofrinho...); com itens, o valor é a soma deles ({@link #porLocal}).
 */
public record SaldoMes(Competencia competencia, Dinheiro valor, String observacao, LocalDate dataReferencia,
                       List<PorLocal> porLocal) {

    /** Saldo de um lugar no mesmo dia do saldo do mês. */
    public record PorLocal(Long localId, Dinheiro valor) {

        public PorLocal {
            obrigatorio(localId, "local");
            obrigatorio(valor, "valor");
        }
    }

    public SaldoMes {
        obrigatorio(competencia, "competência");
        obrigatorio(valor, "valor");
        observacao = opcional(observacao);
        if (dataReferencia != null && !Competencia.de(dataReferencia).equals(competencia)) {
            throw new RegraNegocioException("O dia do saldo precisa ser de " + competencia);
        }
        porLocal = porLocal == null ? List.of() : List.copyOf(porLocal);
        Set<Long> vistos = new HashSet<>();
        for (PorLocal p : porLocal) {
            if (!vistos.add(p.localId())) {
                throw new RegraNegocioException("Cada lugar entra uma vez só no saldo do mês");
            }
        }
        if (!porLocal.isEmpty() && valor.compareTo(soma(porLocal)) != 0) {
            throw new RegraNegocioException("O saldo do mês precisa ser a soma dos lugares");
        }
    }

    /** Saldo já do dia 1º, sem itens. */
    public SaldoMes(Competencia competencia, Dinheiro valor, String observacao) {
        this(competencia, valor, observacao, null, List.of());
    }

    public SaldoMes(Competencia competencia, Dinheiro valor, String observacao, LocalDate dataReferencia) {
        this(competencia, valor, observacao, dataReferencia, List.of());
    }

    /** Saldo item a item: o total é a soma dos lugares. */
    public static SaldoMes porLocal(Competencia competencia, List<PorLocal> itens, String observacao,
                                    LocalDate dataReferencia) {
        if (itens == null || itens.isEmpty()) {
            throw new RegraNegocioException("Informe o saldo de pelo menos um lugar");
        }
        return new SaldoMes(competencia, soma(itens), observacao, dataReferencia, itens);
    }

    /** O saldo do dia 1º: o informado ou, se for de outro dia, ele sem o que entrou e saiu de 1º até esse dia. */
    public Dinheiro noDiaPrimeiro(MovimentoDoMes movimento) {
        return dataReferencia == null ? valor : valor.subtrair(movimento.recebido()).somar(movimento.pago());
    }

    private static Dinheiro soma(List<PorLocal> itens) {
        return itens.stream().map(PorLocal::valor).reduce(Dinheiro.ZERO, Dinheiro::somar);
    }
}
