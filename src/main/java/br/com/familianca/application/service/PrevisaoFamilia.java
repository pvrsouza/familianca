package br.com.familianca.application.service;

import br.com.familianca.application.port.out.LancamentoRepository;
import br.com.familianca.application.port.out.PlanoContasRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.domain.model.Recorrencia;
import br.com.familianca.domain.service.PrevisaoRecorrencias;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Monta a {@link PrevisaoRecorrencias} da família logada: a regra de estimativa (da família e das categorias) sobre
 * as confirmações do ano corrente. Usada pela visão anual e pelas recorrências, para mostrarem o mesmo previsto.
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

    PrevisaoRecorrencias calcular(List<Recorrencia> recorrencias) {
        LocalDate hoje = relogio.hoje();
        return PrevisaoRecorrencias.calcular(recorrencias, planoContas.listarCategorias(), planoContas.listarGrupos(),
                lancamentos.listarOcorrenciasConfirmadas(hoje.getYear()), acesso.familia().estimativa(), hoje);
    }
}
