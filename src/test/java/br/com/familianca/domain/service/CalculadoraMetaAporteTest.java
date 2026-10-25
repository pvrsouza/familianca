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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraMetaAporteTest {

    private static final CalculadoraMetaAporte CALCULADORA = new CalculadoraMetaAporte();
    private static final Percentual PARTIDA = Percentual.of("50");

    @Test
    void realizadoFicaEntreZeroECemESemSobraFicaDeFora() {
        assertThat(CalculadoraMetaAporte.realizado(Dinheiro.of("1000"), Dinheiro.of("4000")).valor())
                .isEqualByComparingTo("25");
        assertThat(CalculadoraMetaAporte.realizado(Dinheiro.of("-800"), Dinheiro.of("4000")).valor())
                .isEqualByComparingTo("0");
        assertThat(CalculadoraMetaAporte.realizado(Dinheiro.of("9000"), Dinheiro.of("4000")).valor())
                .isEqualByComparingTo("100");
        assertThat(CalculadoraMetaAporte.realizado(Dinheiro.of("1000"), Dinheiro.ZERO)).isNull();
        assertThat(CalculadoraMetaAporte.realizado(Dinheiro.of("1000"), Dinheiro.of("-50"))).isNull();
    }

    @Test
    void mediaDosUltimos3EncadeiaRealizadoEMetasDefinidas() {
        // Jan e fev fechados: 10% e 20% realizados; maio definido em 30%.
        SerieMensal sobra = todoMes("10000");
        SerieMensal aplicacoes = SerieMensal.ZERO.comMes(1, Dinheiro.of("1000")).comMes(2, Dinheiro.of("2000"));
        List<MetaAporteMes> meses = CALCULADORA.calcular(2026, sobra, aplicacoes, 2, PARTIDA,
                new MetasAporte(Estimativa.MEDIA_3M, Map.of(Competencia.of(2026, 5), Percentual.of("30")), List.of(),
                        true));

        assertMeta(meses.get(0), "50", OrigemMeta.PARTIDA); // sem base ainda
        assertThat(meses.get(0).realizado().valor()).isEqualByComparingTo("10");
        assertMeta(meses.get(1), "10", OrigemMeta.ESTIMADA);
        assertMeta(meses.get(2), "15", OrigemMeta.ESTIMADA); // (10 + 20) / 2
        assertThat(meses.get(2).realizado()).isNull(); // ainda não fechou
        assertMeta(meses.get(3), "15", OrigemMeta.ESTIMADA); // (10 + 20 + 15) / 3
        assertMeta(meses.get(4), "30", OrigemMeta.DEFINIDA);
        assertThat(meses.get(4).base()).isEmpty();
        assertMeta(meses.get(5), "20", OrigemMeta.ESTIMADA); // (15 + 15 + 30) / 3
        assertThat(meses.get(5).base()).extracting(BaseMeta::realizado).containsOnly(false);
    }

    @Test
    void mesFechadoUsaORealizadoMesmoComMetaDefinida() {
        List<MetaAporteMes> meses = CALCULADORA.calcular(2026, todoMes("10000"),
                SerieMensal.ZERO.comMes(1, Dinheiro.of("1000")), 1, null,
                new MetasAporte(Estimativa.MEDIA_3M, Map.of(Competencia.of(2026, 1), Percentual.of("40")), List.of(),
                        true));
        assertMeta(meses.get(0), "40", OrigemMeta.DEFINIDA); // o planejado fica no mês
        assertMeta(meses.get(1), "10", OrigemMeta.ESTIMADA); // a base usa o que aconteceu
    }

    @Test
    void mediaDosUltimos3CruzaOAnoEMesSemSobraFicaDeFora() {
        List<BaseMeta> dezembro = List.of(new BaseMeta(Competencia.of(2025, 12), Percentual.of("40"), true));
        SerieMensal sobra = todoMes("10000").comMes(1, Dinheiro.of("-500"));
        List<MetaAporteMes> meses = CALCULADORA.calcular(2026, sobra, SerieMensal.ZERO, 1, PARTIDA,
                new MetasAporte(Estimativa.MEDIA_3M, Map.of(), dezembro, true));
        assertMeta(meses.get(0), "40", OrigemMeta.ESTIMADA);
        assertThat(meses.get(0).realizado()).isNull();
        assertThat(meses.get(0).naBase()).isNull();
        assertMeta(meses.get(1), "40", OrigemMeta.ESTIMADA); // janeiro não entrou: só dezembro
    }

    @Test
    void regrasDoAnoNaoOlhamOAnoAnterior() {
        List<BaseMeta> dezembro = List.of(new BaseMeta(Competencia.of(2025, 12), Percentual.of("90"), true));
        SerieMensal aplicacoes = SerieMensal.ZERO.comMes(1, Dinheiro.of("1000")).comMes(2, Dinheiro.of("3000"));
        List<MetaAporteMes> meses = CALCULADORA.calcular(2026, todoMes("10000"), aplicacoes, 2, PARTIDA,
                new MetasAporte(Estimativa.MAXIMO, Map.of(), dezembro, true));
        assertMeta(meses.get(0), "50", OrigemMeta.PARTIDA);
        assertMeta(meses.get(2), "30", OrigemMeta.ESTIMADA);
    }

    @Test
    void naoEstimarUsaAPartidaESemPartidaNaoHaMeta() {
        List<MetaAporteMes> comPartida = CALCULADORA.calcular(2026, todoMes("10000"), SerieMensal.ZERO, 12, PARTIDA,
                new MetasAporte(Estimativa.NENHUMA, Map.of(), List.of(), true));
        assertThat(comPartida).allSatisfy(m -> assertMeta(m, "50", OrigemMeta.PARTIDA));
        List<MetaAporteMes> sem = CALCULADORA.calcular(2026, todoMes("10000"), SerieMensal.ZERO, 12, null,
                new MetasAporte(Estimativa.NENHUMA, Map.of(), List.of(), true));
        assertThat(sem).allSatisfy(m -> {
            assertThat(m.meta()).isNull();
            assertThat(m.origem()).isNull();
        });
    }

    @Test
    void semAportesRegistradosOMesFechadoEntraComASuaMeta() {
        // Sem o recurso de investimentos o realizado seria sempre 0% e derrubaria a meta.
        List<MetaAporteMes> meses = CALCULADORA.calcular(2026, todoMes("10000"), SerieMensal.ZERO, 9, PARTIDA,
                new MetasAporte(Estimativa.MEDIA_3M, Map.of(), List.of(), false));
        assertThat(meses).allSatisfy(m -> assertThat(m.meta().valor()).isEqualByComparingTo("50"));
        assertThat(meses.get(0).realizado()).isNull();
    }

    private static SerieMensal todoMes(String valor) {
        SerieMensal s = SerieMensal.ZERO;
        for (int mes = 1; mes <= SerieMensal.MESES; mes++) {
            s = s.comMes(mes, Dinheiro.of(valor));
        }
        return s;
    }

    private static void assertMeta(MetaAporteMes m, String meta, OrigemMeta origem) {
        assertThat(m.meta().valor()).isEqualByComparingTo(meta);
        assertThat(m.origem()).isEqualTo(origem);
    }
}
