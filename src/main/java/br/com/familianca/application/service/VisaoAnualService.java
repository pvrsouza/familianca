package br.com.familianca.application.service;

import br.com.familianca.application.port.in.ConsultarVisaoAnual;
import br.com.familianca.application.port.out.InvestimentoRepository;
import br.com.familianca.application.port.out.LancamentoRepository;
import br.com.familianca.application.port.out.PlanoContasRepository;
import br.com.familianca.application.port.out.RecorrenciaRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.application.port.out.SaldoRepository;
import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.CarteiraAnual;
import br.com.familianca.domain.model.Familia;
import br.com.familianca.domain.model.Periodicidade;
import br.com.familianca.domain.model.Recorrencia;
import br.com.familianca.domain.model.Recurso;
import br.com.familianca.domain.model.VisaoAnual;
import br.com.familianca.domain.service.CalculadoraImpacto;
import br.com.familianca.domain.service.CalculadoraImpacto.Impacto;
import br.com.familianca.domain.service.CalculadoraInvestimentos;
import br.com.familianca.domain.service.CalculadoraVisaoAnual;
import br.com.familianca.domain.service.CalculadoraVisaoAnual.Entrada;
import br.com.familianca.domain.service.CalculadoraVisaoAnual.Ocorrencia;
import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.SerieMensal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class VisaoAnualService implements ConsultarVisaoAnual {

    private static final CalculadoraVisaoAnual CALCULADORA = new CalculadoraVisaoAnual();
    private static final CalculadoraInvestimentos INVESTIMENTOS = new CalculadoraInvestimentos();
    private static final CalculadoraImpacto IMPACTO = new CalculadoraImpacto();

    private final PlanoContasRepository planoContas;
    private final LancamentoRepository lancamentos;
    private final RecorrenciaRepository recorrencias;
    private final SaldoRepository saldos;
    private final InvestimentoRepository investimentos;
    private final AcessoFamilia acesso;
    private final Relogio relogio;
    private final PrevisaoFamilia previsaoFamilia;
    private final ConfirmacaoAutomatica confirmacaoAutomatica;

    public VisaoAnualService(PlanoContasRepository planoContas, LancamentoRepository lancamentos,
                             RecorrenciaRepository recorrencias, SaldoRepository saldos,
                             InvestimentoRepository investimentos, AcessoFamilia acesso, Relogio relogio,
                             ConfirmacaoAutomatica confirmacaoAutomatica, PrevisaoFamilia previsaoFamilia) {
        this.planoContas = planoContas;
        this.lancamentos = lancamentos;
        this.recorrencias = recorrencias;
        this.saldos = saldos;
        this.investimentos = investimentos;
        this.acesso = acesso;
        this.relogio = relogio;
        this.confirmacaoAutomatica = confirmacaoAutomatica;
        this.previsaoFamilia = previsaoFamilia;
    }

    @Override
    public VisaoAnualDetalhada visaoAnual(int ano) {
        return new VisaoAnualDetalhada(calcular(ano), acesso.membrosPorId());
    }

    @Override
    public Impacto simularImpacto(Dinheiro valor, Periodicidade periodicidade, Integer ano) {
        VisaoAnual visao = calcular(ano != null ? ano : anoAtual());
        return IMPACTO.calcular(valor, periodicidade, visao.fluxo().rendaTotal().total(),
                visao.fluxo().despesasTotal().total());
    }

    @Override
    public int anoAtual() {
        return relogio.hoje().getYear();
    }

    private VisaoAnual calcular(int ano) {
        if (ano < 2000 || ano > 2100) {
            throw new RegraNegocioException("Ano inválido: " + ano);
        }
        confirmacaoAutomatica.processar();
        Familia familia = acesso.familia();
        Set<Ocorrencia> confirmadas = lancamentos.listarOcorrenciasConfirmadas(ano).stream()
                .map(l -> new Ocorrencia(l.recorrenciaId(), l.competenciaRecorrencia()))
                .collect(Collectors.toSet());
        SerieMensal aplicacoes = SerieMensal.ZERO;
        SerieMensal rendaInvestimentos = SerieMensal.ZERO;
        if (familia.assinatura().planoEmVigor(relogio.hoje()).permite(Recurso.INVESTIMENTOS)) {
            CarteiraAnual carteira = INVESTIMENTOS.calcular(ano, investimentos.listar(),
                    investimentos.listarMovimentosAte(ano));
            aplicacoes = carteira.aportes();
            rendaInvestimentos = carteira.rendimentos();
        }
        List<Recorrencia> lista = recorrencias.listar();
        return CALCULADORA.calcular(new Entrada(ano, planoContas.listarGrupos(), planoContas.listarCategorias(),
                lancamentos.listar(LocalDate.of(ano, 1, 1), LocalDate.of(ano, 12, 31)), lista,
                confirmadas, saldos.listar(ano), aplicacoes, rendaInvestimentos, familia.metaAporte(),
                familia.estimativa(), relogio.hoje(), previsaoFamilia.calcular(lista)));
    }
}
