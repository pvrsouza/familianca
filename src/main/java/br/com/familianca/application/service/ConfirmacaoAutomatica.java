package br.com.familianca.application.service;

import br.com.familianca.application.port.out.FamiliaRepository;
import br.com.familianca.application.port.out.LancamentoRepository;
import br.com.familianca.application.port.out.PlanoContasRepository;
import br.com.familianca.application.port.out.RecorrenciaRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.Recorrencia;
import br.com.familianca.domain.vo.Competencia;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Confirma as ocorrências das recorrências com confirmação automática que já venceram: cada uma vira lançamento com o
 * valor e a data previstos. Roda no começo das leituras do orçamento da família logada (visão anual, lançamentos,
 * recorrências), numa transação própria, para que a leitura já enxergue o resultado.
 * <p>
 * Ocorrência que não dá para lançar (categoria desativada ou calculada, membro inativo) fica de fora e continua nas
 * pendentes. Duas leituras ao mesmo tempo (o front pede a visão e as pendentes juntas) não duplicam: a família fica
 * travada enquanto grava, e o índice único da ocorrência é a segunda garantia.
 */
@Component
public class ConfirmacaoAutomatica {

    private final RecorrenciaRepository recorrencias;
    private final LancamentoRepository lancamentos;
    private final PlanoContasRepository planoContas;
    private final FamiliaRepository familias;
    private final AcessoFamilia acesso;
    private final Relogio relogio;

    public ConfirmacaoAutomatica(RecorrenciaRepository recorrencias, LancamentoRepository lancamentos,
                                 PlanoContasRepository planoContas, FamiliaRepository familias, AcessoFamilia acesso,
                                 Relogio relogio) {
        this.recorrencias = recorrencias;
        this.lancamentos = lancamentos;
        this.planoContas = planoContas;
        this.familias = familias;
        this.acesso = acesso;
        this.relogio = relogio;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processar() {
        LocalDate hoje = relogio.hoje();
        List<Recorrencia> automaticas = recorrencias.listar().stream().filter(Recorrencia::confirmaSozinha).toList();
        if (automaticas.isEmpty() || vencidas(automaticas, hoje).isEmpty()) {
            return;
        }
        familias.travar(acesso.familia().id());
        // Depois de travar, outra leitura pode ter gravado: confere de novo.
        List<Vencida> aConfirmar = vencidas(automaticas, hoje);
        Map<Long, Categoria> categorias = planoContas.listarCategorias().stream()
                .collect(Collectors.toMap(Categoria::id, Function.identity()));
        Map<Long, Grupo> grupos = planoContas.listarGrupos().stream()
                .collect(Collectors.toMap(Grupo::id, Function.identity()));
        Map<Long, Membro> membros = acesso.membrosPorId();
        for (Vencida o : aConfirmar) {
            Recorrencia r = o.recorrencia();
            Membro membro = membros.get(r.membroId());
            Categoria categoria = categorias.get(r.categoriaId());
            if (membro == null || !membro.ativo() || categoria == null || !lancavel(categoria, grupos)) {
                continue;
            }
            lancamentos.salvar(new Lancamento(null, r.categoriaId(), r.membroId(), r.descricao(), r.valor(),
                    r.dataEm(o.competencia()), r.id(), o.competencia(), null, r.observacao()));
        }
    }

    /** Ocorrências que já venceram (desde que a confirmação foi ligada) e ainda não viraram lançamento. */
    private List<Vencida> vencidas(List<Recorrencia> automaticas, LocalDate hoje) {
        Competencia ate = Competencia.de(hoje);
        Competencia de = automaticas.stream().map(r -> Competencia.de(r.confirmarSozinhaDesde()))
                .reduce((a, b) -> a.antesDe(b) ? a : b).orElse(ate);
        if (de.depoisDe(ate)) {
            return List.of();
        }
        Set<Chave> confirmadas = lancamentos.listarOcorrenciasConfirmadas(de, ate).stream()
                .map(l -> new Chave(l.recorrenciaId(), l.competenciaRecorrencia()))
                .collect(Collectors.toSet());
        List<Vencida> vencidas = new ArrayList<>();
        for (Recorrencia r : automaticas) {
            for (Competencia c = Competencia.de(r.confirmarSozinhaDesde()); !c.depoisDe(ate); c = c.seguinte()) {
                if (r.confirmaSozinhaEm(c, hoje) && !confirmadas.contains(new Chave(r.id(), c))) {
                    vencidas.add(new Vencida(r, c));
                }
            }
        }
        return vencidas;
    }

    private static boolean lancavel(Categoria categoria, Map<Long, Grupo> grupos) {
        try {
            categoria.exigirLancavel(grupos.get(categoria.grupoId()));
            return true;
        } catch (RegraNegocioException e) {
            return false;
        }
    }

    private record Chave(Long recorrenciaId, Competencia competencia) {
    }

    private record Vencida(Recorrencia recorrencia, Competencia competencia) {
    }
}
