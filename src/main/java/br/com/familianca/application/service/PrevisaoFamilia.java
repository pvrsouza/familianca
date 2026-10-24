package br.com.familianca.application.service;

import br.com.familianca.application.port.out.LancamentoRepository;
import br.com.familianca.application.port.out.PlanoContasRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.domain.model.Recorrencia;
import br.com.familianca.domain.service.PrevisaoRecorrencias;
import br.com.familianca.domain.vo.Competencia;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Monta a {@link PrevisaoRecorrencias} da família logada: a regra de estimativa (da família e das categorias) sobre
 * as confirmações dos últimos 12 meses (a média recente cruza o ano; as regras do ano filtram o ano corrente). Usada pela visão anual e pelas recorrências, para mostrarem o mesmo previsto.
 */
@Component
class PrevisaoFamilia {

    private final PlanoContasRepository planoContas;
    private final LancamentoRepository lancamentos;
    private final AcessoFamilia acesso;
    private final Relogio relogio;

    PrevisaoFamilia(PlanoContasRepository planoContas, LancamentoRepository lancamentos, AcessoFamilia acesso,
                    Relogio relogio) {
        this.planoContas = planoContas;
        this.lancamentos = lancamentos;
        this.acesso = acesso;
        this.relogio = relogio;
    }

    /** Quantos meses de confirmações olhar para trás: cobre o ano corrente e a média recente em janeiro. */
    private static final int HISTORICO_MESES = 12;
    /** Contas pagas adiantado também entram na base: confirmações até este número de meses à frente. */
    private static final int ADIANTADAS_MESES = 24;

    PrevisaoRecorrencias calcular(List<Recorrencia> recorrencias) {
        LocalDate hoje = relogio.hoje();
        return PrevisaoRecorrencias.calcular(recorrencias, planoContas.listarCategorias(), planoContas.listarGrupos(),
                lancamentos.listarOcorrenciasConfirmadas(Competencia.de(hoje).mais(-HISTORICO_MESES),
                        Competencia.de(hoje).mais(ADIANTADAS_MESES)),
                acesso.familia().estimativa(), hoje);
    }
}
