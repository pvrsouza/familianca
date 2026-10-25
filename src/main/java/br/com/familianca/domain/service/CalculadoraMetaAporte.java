package br.com.familianca.domain.service;

import br.com.familianca.domain.model.Estimativa;
import br.com.familianca.domain.model.MetasAporte;
import br.com.familianca.domain.model.VisaoAnual.BaseMeta;
import br.com.familianca.domain.model.VisaoAnual.MetaAporteMes;
import br.com.familianca.domain.model.VisaoAnual.OrigemMeta;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.Percentual;
import br.com.familianca.domain.vo.SerieMensal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A meta de aporte de cada mês do ano: a definida à mão; senão a estimada pela regra a partir dos meses anteriores;
 * senão a meta de partida. Na base, mês fechado entra com o percentual realizado (aplicado ÷ sobra) e mês atual ou
 * futuro com a sua meta, então mudar um mês muda a estimativa dos seguintes.
 */
public final class CalculadoraMetaAporte {

    private static final Percentual CEM = Percentual.of("100");

    /**
     * @param sobra      renda − despesas de cada mês
     * @param aplicacoes aportes líquidos nos investimentos de cada mês (negativo = resgate)
     * @param fechadoAte último mês fechado do ano (0 = nenhum, 12 = todos)
     * @param partida    meta de partida da família; nula = sem meta
     */
    public List<MetaAporteMes> calcular(int ano, SerieMensal sobra, SerieMensal aplicacoes, int fechadoAte,
                                        Percentual partida, MetasAporte metas) {
        List<BaseMeta> doAno = new ArrayList<>();
        List<MetaAporteMes> meses = new ArrayList<>();
        for (int mes = 1; mes <= SerieMensal.MESES; mes++) {
            Competencia competencia = Competencia.of(ano, mes);
            Percentual definida = metas.doMes().get(competencia);
            List<BaseMeta> base = definida != null ? List.of() : base(metas, doAno);
            Percentual estimada = metas.regra().aplicarPercentuais(base.stream().map(BaseMeta::valor).toList());
            Percentual meta;
            OrigemMeta origem;
            if (definida != null) {
                meta = definida;
                origem = OrigemMeta.DEFINIDA;
            } else if (estimada != null) {
                meta = estimada;
                origem = OrigemMeta.ESTIMADA;
            } else {
                meta = partida;
                origem = partida != null ? OrigemMeta.PARTIDA : null;
                base = List.of();
            }
            boolean fechado = metas.conheceAportes() && mes <= fechadoAte;
            Percentual realizado = fechado ? realizado(aplicacoes.mes(mes), sobra.mes(mes)) : null;
            Percentual entra = fechado ? realizado : meta;
            BaseMeta naBase = entra != null ? new BaseMeta(competencia, entra, fechado) : null;
            if (naBase != null) {
                doAno.add(naBase);
            }
            meses.add(new MetaAporteMes(meta, origem, realizado, base, naBase));
        }
        return meses;
    }

    /** Média dos últimos 3 meses cruza o ano; as outras regras olham só os meses anteriores do mesmo ano. */
    private static List<BaseMeta> base(MetasAporte metas, List<BaseMeta> doAno) {
        if (metas.regra() == Estimativa.NENHUMA) {
            return List.of();
        }
        if (metas.regra().doAno()) {
            return List.copyOf(doAno);
        }
        List<BaseMeta> todos = new ArrayList<>(metas.anteriores());
        todos.addAll(doAno);
        return List.copyOf(todos.subList(Math.max(0, todos.size() - Estimativa.MESES_RECENTES), todos.size()));
    }

    /**
     * Quanto da sobra foi aplicado, de 0 a 100%. Sobra zero ou negativa fica de fora (nulo): a meta é sobre o que
     * sobra. Resgate maior que o aplicado conta 0%; aplicar mais que a sobra (do saldo em conta) conta 100%.
     */
    static Percentual realizado(Dinheiro aplicado, Dinheiro sobra) {
        if (!sobra.ehPositivo()) {
            return null;
        }
        Percentual p = Percentual.razao(aplicado.maximo(Dinheiro.ZERO), sobra);
        return p.valor().compareTo(BigDecimal.valueOf(100)) > 0 ? CEM : p;
    }
}
