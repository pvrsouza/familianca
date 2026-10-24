package br.com.familianca.domain.service;

import br.com.familianca.domain.model.CarteiraAnual;
import br.com.familianca.domain.model.CarteiraAnual.Abertura;
import br.com.familianca.domain.model.CarteiraAnual.Posicao;
import br.com.familianca.domain.model.Investimento;
import br.com.familianca.domain.model.MovimentoInvestimento;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.Percentual;
import br.com.familianca.domain.vo.SerieMensal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reconstrói o saldo de cada investimento mês a mês, desde o primeiro movimento, até dezembro do ano pedido. */
public final class CalculadoraInvestimentos {

    /** @param movimentos todos os movimentos dos investimentos até dezembro do ano (anos anteriores inclusive) */
    public CarteiraAnual calcular(int ano, List<Investimento> investimentos, List<MovimentoInvestimento> movimentos) {
        Map<Long, List<MovimentoInvestimento>> porInvestimento = movimentos.stream()
                .collect(Collectors.groupingBy(MovimentoInvestimento::investimentoId));
        List<Posicao> posicoes = investimentos.stream()
                .map(i -> posicao(ano, i, porInvestimento.getOrDefault(i.id(), List.of())))
                .filter(p -> p.investimento().ativo() || !p.saldos().ehZero() || !p.aportes().ehZero()
                        || !p.saldoInicial().ehZero())
                .toList();

        Dinheiro saldoInicial = posicoes.stream().map(Posicao::saldoInicial).reduce(Dinheiro.ZERO, Dinheiro::somar);
        SerieMensal aportes = soma(posicoes, Posicao::aportes);
        SerieMensal rendimentos = soma(posicoes, Posicao::rendimentos);
        SerieMensal saldos = soma(posicoes, Posicao::saldos);
        List<Percentual> rentabilidade = rentabilidade(saldoInicial, rendimentos, saldos);
        return new CarteiraAnual(ano, posicoes, saldoInicial, aportes, rendimentos, saldos, rentabilidade,
                composta(rentabilidade, saldoInicial, rendimentos, saldos));
    }

    private static Posicao posicao(int ano, Investimento investimento, List<MovimentoInvestimento> movimentos) {
        Map<Competencia, MovimentoInvestimento> porMes = movimentos.stream()
                .filter(m -> m.competencia().ano() <= ano)
                .collect(Collectors.toMap(MovimentoInvestimento::competencia, Function.identity()));
        Competencia janeiro = Competencia.of(ano, 1);
        Competencia inicio = porMes.keySet().stream().min(Competencia::compareTo).orElse(janeiro);
        if (inicio.depoisDe(janeiro)) {
            inicio = janeiro;
        }

        Dinheiro saldo = Dinheiro.ZERO;
        Dinheiro saldoInicial = Dinheiro.ZERO;
        SerieMensal aportes = SerieMensal.ZERO;
        SerieMensal rendimentos = SerieMensal.ZERO;
        SerieMensal saldos = SerieMensal.ZERO;
        Abertura abertura = null;
        for (Competencia c = inicio; c.ano() <= ano; c = c.seguinte()) {
            if (c.equals(janeiro)) {
                saldoInicial = saldo;
            }
            MovimentoInvestimento m = porMes.get(c);
            Dinheiro aporte = m != null ? m.aporte() : Dinheiro.ZERO;
            Dinheiro anterior = saldo;
            Dinheiro rendimento;
            if (m != null && m.saldoInformado() != null && anterior.ehZero() && !m.saldoInformado().ehZero()) {
                // Saldo do extrato num investimento zerado (o primeiro registro, ou a volta depois de resgatar tudo):
                // o dinheiro já existia. Não rendeu nem saiu da conta neste mês.
                saldo = m.saldoInformado();
                rendimento = Dinheiro.ZERO;
                abertura = new Abertura(c, saldo.subtrair(aporte));
            } else if (m != null && m.saldoInformado() != null) {
                saldo = m.saldoInformado();
                rendimento = saldo.subtrair(anterior).subtrair(aporte);
            } else {
                rendimento = m != null && m.rentabilidade() != null ? m.rentabilidade().aplicarSobre(anterior)
                        : Dinheiro.ZERO;
                saldo = anterior.somar(rendimento).somar(aporte);
            }
            if (c.ano() == ano) {
                aportes = aportes.comMes(c.numeroMes(), aporte);
                rendimentos = rendimentos.comMes(c.numeroMes(), rendimento);
                saldos = saldos.comMes(c.numeroMes(), saldo);
            }
        }
        List<Percentual> rentabilidade = rentabilidade(saldoInicial, rendimentos, saldos);
        return new Posicao(investimento, saldoInicial, aportes, rendimentos, saldos, rentabilidade,
                composta(rentabilidade, saldoInicial, rendimentos, saldos), abertura);
    }

    private static SerieMensal soma(List<Posicao> posicoes, Function<Posicao, SerieMensal> serie) {
        return posicoes.stream().map(serie).reduce(SerieMensal.ZERO, SerieMensal::somar);
    }

    private static List<Percentual> rentabilidade(Dinheiro saldoInicial, SerieMensal rendimentos, SerieMensal saldos) {
        List<Percentual> lista = new ArrayList<>();
        for (int mes = 1; mes <= SerieMensal.MESES; mes++) {
            Dinheiro anterior = mes == 1 ? saldoInicial : saldos.mes(mes - 1);
            lista.add(anterior.ehPositivo() ? Percentual.razao(rendimentos.mes(mes), anterior) : null);
        }
        return lista;
    }

    /** Composta com as razões exatas (não com os percentuais já arredondados). */
    private static Percentual composta(List<Percentual> mensal, Dinheiro saldoInicial, SerieMensal rendimentos,
                                       SerieMensal saldos) {
        BigDecimal fator = BigDecimal.ONE;
        boolean algum = false;
        for (int mes = 1; mes <= SerieMensal.MESES; mes++) {
            if (mensal.get(mes - 1) == null) {
                continue;
            }
            Dinheiro anterior = mes == 1 ? saldoInicial : saldos.mes(mes - 1);
            fator = fator.multiply(BigDecimal.ONE.add(
                    rendimentos.mes(mes).valor().divide(anterior.valor(), MathContext.DECIMAL64)));
            algum = true;
        }
        return algum ? Percentual.deFracao(fator.subtract(BigDecimal.ONE)) : null;
    }
}
