package br.com.familianca.domain.service;

import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Frequencia;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.Natureza;
import br.com.familianca.domain.model.Recorrencia;
import br.com.familianca.domain.model.SaldoMes;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.model.VisaoAnual;
import br.com.familianca.domain.model.VisaoAnual.LinhaCategoria;
import br.com.familianca.domain.model.VisaoAnual.LinhaGrupo;
import br.com.familianca.domain.service.CalculadoraVisaoAnual.Entrada;
import br.com.familianca.domain.service.CalculadoraVisaoAnual.Ocorrencia;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.Percentual;
import br.com.familianca.domain.vo.SerieMensal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Um ano de família pequeno o bastante para conferir à mão: salário recorrente (janeiro confirmado), condomínio
 * recorrente com um mês pulado, supermercado e presentes lançados por duas pessoas, nota fiscal da PJ com ISS de 5%.
 */
class CalculadoraVisaoAnualTest {

    static final long PAI = 100L;
    static final long FILHA = 200L;

    static final Grupo RECEITAS_FIXAS = new Grupo(1L, TipoMovimento.RECEITA, Natureza.FIXA, "Receitas fixas", false, 1, true);
    static final Grupo HABITACAO = new Grupo(2L, TipoMovimento.DESPESA, Natureza.FIXA, "Habitação", false, 1, true);
    static final Grupo VARIAVEIS = new Grupo(3L, TipoMovimento.DESPESA, Natureza.VARIAVEL, "Variáveis", false, 3, true);
    static final Grupo RECEITAS_PJ = new Grupo(4L, TipoMovimento.RECEITA, Natureza.VARIAVEL, "PJ", false, 2, true);
    static final Grupo EMPRESA = new Grupo(5L, TipoMovimento.DESPESA, Natureza.FIXA, "Empresa", true, 2, true);
    static final Grupo INATIVO_VAZIO = new Grupo(6L, TipoMovimento.DESPESA, Natureza.FIXA, "Antigo", false, 4, false);

    static final Categoria SALARIO = categoria(10L, 1L, "Salário");
    static final Categoria SUPERMERCADO = categoria(20L, 2L, "Supermercado");
    static final Categoria CONDOMINIO = categoria(21L, 2L, "Condomínio");
    static final Categoria SEM_USO_INATIVA = new Categoria(22L, 2L, "Jornal", 3, false, false, null);
    static final Categoria PRESENTES = categoria(30L, 3L, "Presentes");
    static final Categoria NOTAS = new Categoria(40L, 4L, "Notas fiscais", 1, true, true, null);
    static final Categoria ISS = new Categoria(50L, 5L, "ISS", 1, true, false, Percentual.of("5"));

    VisaoAnual visao;

    @BeforeEach
    void calcular() {
        Recorrencia salario = new Recorrencia(1L, SALARIO.id(), PAI, "Salário", Dinheiro.of("10000"), 5,
                Frequencia.MENSAL, Competencia.of(2026, 1), null, null, Set.of(), null);
        Recorrencia condominio = new Recorrencia(2L, CONDOMINIO.id(), PAI, "Condomínio", Dinheiro.of("500"), 10,
                Frequencia.MENSAL, Competencia.of(2026, 3), Competencia.of(2026, 12), null,
                Set.of(Competencia.of(2026, 7)), null);
        List<Lancamento> lancamentos = List.of(
                new Lancamento(1L, SALARIO.id(), PAI, "Salário", Dinheiro.of("10000"), LocalDate.of(2026, 1, 5),
                        1L, Competencia.of(2026, 1), null, null),
                lancamento(2L, SUPERMERCADO, PAI, "800", LocalDate.of(2026, 1, 15)),
                lancamento(3L, SUPERMERCADO, FILHA, "1000", LocalDate.of(2026, 2, 15)),
                lancamento(4L, PRESENTES, FILHA, "300", LocalDate.of(2026, 12, 20)),
                lancamento(5L, NOTAS, PAI, "20000", LocalDate.of(2026, 1, 10)),
                // De outro ano: fica de fora.
                lancamento(6L, SUPERMERCADO, PAI, "999", LocalDate.of(2025, 12, 31)));
        visao = new CalculadoraVisaoAnual().calcular(new Entrada(2026,
                List.of(RECEITAS_FIXAS, HABITACAO, VARIAVEIS, RECEITAS_PJ, EMPRESA, INATIVO_VAZIO),
                List.of(SALARIO, SUPERMERCADO, CONDOMINIO, SEM_USO_INATIVA, PRESENTES, NOTAS, ISS),
                lancamentos, List.of(salario, condominio), Set.of(new Ocorrencia(1L, Competencia.of(2026, 1))),
                List.of(new SaldoMes(Competencia.of(2026, 1), Dinheiro.of("5000"), null)),
                SerieMensal.ZERO.comMes(3, Dinheiro.of("1000")), SerieMensal.ZERO, Percentual.of("10")));
    }

    @Test
    void receitasRealizadasEPrevistas() {
        LinhaCategoria salario = linha(visao.receitas(), SALARIO.id());
        assertThat(salario.realizado().mes(1)).isEqualTo(Dinheiro.of("10000"));
        assertThat(salario.previsto().mes(1)).isEqualTo(Dinheiro.ZERO);
        assertThat(salario.previsto().mes(2)).isEqualTo(Dinheiro.of("10000"));
        assertThat(salario.total().total()).isEqualTo(Dinheiro.of("120000"));
        assertThat(visao.fluxo().rendaTotal().total()).isEqualTo(Dinheiro.of("140000"));
        assertThat(visao.fluxo().receitasVariaveis().mes(1)).isEqualTo(Dinheiro.of("20000"));
    }

    @Test
    void recorrenciaRespeitaInicioFimEPulos() {
        LinhaCategoria condominio = linha(visao.despesas(), CONDOMINIO.id());
        assertThat(condominio.previsto().mes(2)).isEqualTo(Dinheiro.ZERO);
        assertThat(condominio.previsto().mes(3)).isEqualTo(Dinheiro.of("500"));
        assertThat(condominio.previsto().mes(7)).isEqualTo(Dinheiro.ZERO);
        assertThat(condominio.total().total()).isEqualTo(Dinheiro.of("4500"));
    }

    @Test
    void categoriaCalculadaPelaAliquotaSobreReceitasTributaveis() {
        LinhaCategoria iss = linha(visao.despesas(), ISS.id());
        assertThat(iss.realizado().mes(1)).isEqualTo(Dinheiro.of("1000"));
        assertThat(iss.total().total()).isEqualTo(Dinheiro.of("1000"));
        LinhaGrupo empresa = grupo(visao.despesas(), EMPRESA.id());
        assertThat(empresa.pesoSobreTributaveis().valor()).isEqualByComparingTo("5");
        assertThat(grupo(visao.despesas(), HABITACAO.id()).pesoSobreTributaveis()).isNull();
    }

    @Test
    void participacaoERankingDasCategorias() {
        assertThat(visao.fluxo().despesasTotal().total()).isEqualTo(Dinheiro.of("7600"));
        LinhaCategoria condominio = linha(visao.despesas(), CONDOMINIO.id());
        assertThat(condominio.rank()).isEqualTo(1);
        assertThat(condominio.participacao().valor()).isEqualByComparingTo("59.21");
        assertThat(linha(visao.despesas(), SUPERMERCADO.id()).rank()).isEqualTo(2);
        assertThat(linha(visao.despesas(), ISS.id()).rank()).isEqualTo(3);
        assertThat(linha(visao.despesas(), PRESENTES.id()).rank()).isEqualTo(4);
    }

    @Test
    void desativadosSemValorSaemDaVisao() {
        assertThat(visao.despesas()).extracting(g -> g.grupo().id()).doesNotContain(INATIVO_VAZIO.id());
        assertThat(grupo(visao.despesas(), HABITACAO.id()).categorias())
                .extracting(l -> l.categoria().id()).doesNotContain(SEM_USO_INATIVA.id());
        assertThat(linha(visao.despesas(), SEM_USO_INATIVA.id())).isNull();
    }

    @Test
    void inflacaoDoGrupoMesAMesEAcumulada() {
        // Habitação: jan 800, fev 1000, mar–jun 500, jul 0, ago–dez 500.
        var inflacao = grupo(visao.despesas(), HABITACAO.id()).inflacao();
        assertThat(inflacao.mensal().get(0)).isNull();
        assertThat(inflacao.mensal().get(1).valor()).isEqualByComparingTo("25");
        assertThat(inflacao.mensal().get(2).valor()).isEqualByComparingTo("-50");
        assertThat(inflacao.mensal().get(3).valor()).isEqualByComparingTo("0");
        assertThat(inflacao.mensal().get(6)).isNull();
        assertThat(inflacao.mensal().get(7)).isNull();
        // 1,25 × 0,5 = 0,625 → −37,5%; os meses sem base ficam de fora.
        assertThat(inflacao.acumulada().valor()).isEqualByComparingTo("-37.5");
    }

    @Test
    void fluxoDeCaixaComoNaPlanilha() {
        var fluxo = visao.fluxo();
        assertThat(fluxo.sobraMesAnterior().mes(1)).isEqualTo(Dinheiro.of("5000"));
        assertThat(fluxo.sobraInformada().get(0)).isTrue();
        // jan: 5000 + 30000 − 0 − 1800 = 33200, que vira a sobra de fevereiro.
        assertThat(fluxo.fluxoLiquido().mes(1)).isEqualTo(Dinheiro.of("33200"));
        assertThat(fluxo.sobraMesAnterior().mes(2)).isEqualTo(Dinheiro.of("33200"));
        assertThat(fluxo.sobraInformada().get(1)).isFalse();
        // mar: 42200 + 10000 − 1000 (aplicação) − 500 = 50700.
        assertThat(fluxo.saldoDisponivel().mes(3)).isEqualTo(Dinheiro.of("51200"));
        assertThat(fluxo.fluxoLiquido().mes(3)).isEqualTo(Dinheiro.of("50700"));
        // No ano: sobra de janeiro + renda − aplicações − despesas; bate com dezembro sem outros saldos informados.
        assertThat(fluxo.saldoDisponivelAno()).isEqualTo(Dinheiro.of("144000"));
        assertThat(fluxo.fluxoLiquidoAno()).isEqualTo(Dinheiro.of("136400"));
        assertThat(fluxo.fluxoLiquido().mes(12)).isEqualTo(Dinheiro.of("136400"));
        // Meta de 10% sobre (30000 − 1800).
        assertThat(fluxo.aportePrevisto().mes(1)).isEqualTo(Dinheiro.of("2820"));
        // Sem renda de investimentos: grau zero onde há despesa fixa.
        assertThat(fluxo.grauIndependencia().get(0).valor()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void pesoDeCadaPessoa() {
        assertThat(visao.membros()).extracting(VisaoAnual.LinhaMembro::membroId).containsExactly(PAI, FILHA, null);
        VisaoAnual.LinhaMembro pai = visao.membros().get(0);
        assertThat(pai.receitas().total()).isEqualTo(Dinheiro.of("140000"));
        assertThat(pai.despesas().total()).isEqualTo(Dinheiro.of("5300"));
        assertThat(pai.participacaoDespesas().valor()).isEqualByComparingTo("69.74");
        VisaoAnual.LinhaMembro filha = visao.membros().get(1);
        assertThat(filha.despesas().total()).isEqualTo(Dinheiro.of("1300"));
        assertThat(filha.participacaoReceitas().valor()).isEqualByComparingTo("0");
        assertThat(visao.membros().get(2).despesas().total()).isEqualTo(Dinheiro.of("1000"));
    }

    @Test
    void inflacaoFamiliarSobreAsDespesasTotais() {
        // jan 1800 → fev 1000: −44,44%.
        assertThat(visao.inflacaoFamiliar().mensal().get(1).valor()).isEqualByComparingTo("-44.44");
    }

    @Test
    void categoriaAbreEmItensPorRecorrenciaEPorDescricao() {
        Categoria telefone = categoria(23L, 2L, "Telefone e celular");
        Recorrencia pessoal = new Recorrencia(3L, telefone.id(), PAI, "Vivo - Pessoal", Dinheiro.of("117.83"), 1,
                Frequencia.MENSAL, Competencia.of(2026, 10), null, null, Set.of(), null);
        Recorrencia moto = new Recorrencia(4L, telefone.id(), PAI, "Vivo - Moto", Dinheiro.of("35"), 1,
                Frequencia.MENSAL, Competencia.of(2026, 10), null, null, Set.of(), null);
        List<Lancamento> lancamentos = List.of(
                // Outubro do pessoal confirmado com outro nome: continua no item da recorrência.
                new Lancamento(10L, telefone.id(), PAI, "Celular", Dinheiro.of("120"), LocalDate.of(2026, 10, 1),
                        3L, Competencia.of(2026, 10), null, null),
                new Lancamento(11L, SUPERMERCADO.id(), PAI, "Feira", Dinheiro.of("80"), LocalDate.of(2026, 9, 1),
                        null, null, null, null),
                new Lancamento(12L, SUPERMERCADO.id(), PAI, " feira ", Dinheiro.of("90"), LocalDate.of(2026, 9, 8),
                        null, null, null, null),
                new Lancamento(13L, SUPERMERCADO.id(), PAI, "Açougue", Dinheiro.of("200"), LocalDate.of(2026, 9, 9),
                        null, null, null, null));
        VisaoAnual v = new CalculadoraVisaoAnual().calcular(new Entrada(2026, List.of(HABITACAO, EMPRESA, RECEITAS_PJ),
                List.of(telefone, SUPERMERCADO, NOTAS, ISS), lancamentos, List.of(pessoal, moto),
                Set.of(new Ocorrencia(3L, Competencia.of(2026, 10))), List.of(), SerieMensal.ZERO, SerieMensal.ZERO,
                Percentual.of("10")));

        List<VisaoAnual.LinhaItem> telefones = linha(v.despesas(), telefone.id()).itens();
        assertThat(telefones).extracting(VisaoAnual.LinhaItem::descricao).containsExactly("Vivo - Pessoal", "Vivo - Moto");
        assertThat(telefones.get(0).realizado().mes(10)).isEqualTo(Dinheiro.of("120"));
        assertThat(telefones.get(0).previsto().mes(11)).isEqualTo(Dinheiro.of("117.83"));
        assertThat(telefones.get(1).previsto().total()).isEqualTo(Dinheiro.of("105"));
        assertThat(telefones).extracting(VisaoAnual.LinhaItem::recorrenciaId).containsExactly(3L, 4L);

        // Avulsos: a mesma descrição com outra caixa ou espaço é um item só, com o nome do primeiro.
        List<VisaoAnual.LinhaItem> mercado = linha(v.despesas(), SUPERMERCADO.id()).itens();
        assertThat(mercado).extracting(VisaoAnual.LinhaItem::descricao).containsExactly("Açougue", "Feira");
        assertThat(mercado.get(1).realizado().mes(9)).isEqualTo(Dinheiro.of("170"));
        assertThat(mercado.get(1).recorrenciaId()).isNull();

        // Calculada por alíquota não tem itens.
        assertThat(linha(v.despesas(), ISS.id()).itens()).isEmpty();
    }

    private static Categoria categoria(Long id, Long grupoId, String nome) {
        return new Categoria(id, grupoId, nome, id.intValue(), true, false, null);
    }

    private static Lancamento lancamento(Long id, Categoria c, long membro, String valor, LocalDate data) {
        return new Lancamento(id, c.id(), membro, c.nome(), Dinheiro.of(valor), data, null, null, null, null);
    }

    private static LinhaGrupo grupo(List<LinhaGrupo> grupos, Long id) {
        return grupos.stream().filter(g -> g.grupo().id().equals(id)).findFirst().orElseThrow();
    }

    private static LinhaCategoria linha(List<LinhaGrupo> grupos, Long categoriaId) {
        return grupos.stream().flatMap(g -> g.categorias().stream())
                .filter(l -> l.categoria().id().equals(categoriaId)).findFirst().orElse(null);
    }
}
