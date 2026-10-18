package br.com.familianca.application.service;

import br.com.familianca.application.port.in.GerenciarLancamentos.LancamentoDetalhado;
import br.com.familianca.application.port.in.GerenciarRecorrencias;
import br.com.familianca.application.port.out.LancamentoRepository;
import br.com.familianca.application.port.out.PlanoContasRepository;
import br.com.familianca.application.port.out.RecorrenciaRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.application.service.LancamentoService.Detalhador;
import br.com.familianca.domain.exception.ConflitoException;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.Recorrencia;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.service.CalculadoraVisaoAnual.Ocorrencia;
import br.com.familianca.domain.service.PrevisaoRecorrencias;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static br.com.familianca.domain.Validacoes.obrigatorio;

@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class RecorrenciaService implements GerenciarRecorrencias {

    private final RecorrenciaRepository recorrencias;
    private final LancamentoRepository lancamentos;
    private final PlanoContasRepository planoContas;
    private final AcessoFamilia acesso;
    private final Relogio relogio;
    private final ConfirmacaoAutomatica confirmacaoAutomatica;
    private final PrevisaoFamilia previsaoFamilia;

    public RecorrenciaService(RecorrenciaRepository recorrencias, LancamentoRepository lancamentos,
                              PlanoContasRepository planoContas, AcessoFamilia acesso, Relogio relogio,
                              ConfirmacaoAutomatica confirmacaoAutomatica, PrevisaoFamilia previsaoFamilia) {
        this.recorrencias = recorrencias;
        this.lancamentos = lancamentos;
        this.planoContas = planoContas;
        this.acesso = acesso;
        this.relogio = relogio;
        this.confirmacaoAutomatica = confirmacaoAutomatica;
        this.previsaoFamilia = previsaoFamilia;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecorrenciaDetalhada> listar() {
        confirmacaoAutomatica.processar();
        Detalhador d = detalhador();
        List<Recorrencia> lista = recorrencias.listar();
        PrevisaoRecorrencias previsao = previsaoFamilia.calcular(lista);
        return lista.stream()
                .sorted(Comparator.comparing(Recorrencia::descricao, String.CASE_INSENSITIVE_ORDER))
                .map(r -> detalhar(r, d, previsao)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RecorrenciaDetalhada buscar(Long id) {
        return detalhar(exigir(id));
    }

    @Override
    public RecorrenciaDetalhada criar(DadosRecorrencia dados) {
        Membro membro = validar(dados);
        Recorrencia nova = new Recorrencia(null, dados.categoriaId(), membro.id(), dados.descricao(), dados.valor(),
                dados.dia(), dados.frequencia(), dados.inicio(), dados.fim(), dados.observacao(), Set.of(),
                dados.confirmarSozinha() ? relogio.hoje() : null);
        return detalhar(recorrencias.salvar(nova));
    }

    @Override
    public RecorrenciaDetalhada atualizar(Long id, DadosRecorrencia dados) {
        Recorrencia atual = exigir(id);
        acesso.exigirPodeAlterar(atual.membroId());
        Membro membro = validar(dados);
        // Ligada continua valendo desde quando foi ligada; ligar agora vale a partir de hoje.
        LocalDate desde = !dados.confirmarSozinha() ? null
                : atual.confirmaSozinha() ? atual.confirmarSozinhaDesde() : relogio.hoje();
        // Mudou o valor (reajuste): a estimativa passa a considerar só as confirmações daqui em diante.
        Competencia valorDesde = dados.valor().equals(atual.valor()) ? atual.valorDesde()
                : Competencia.de(relogio.hoje());
        Recorrencia editada = new Recorrencia(id, dados.categoriaId(), membro.id(), dados.descricao(), dados.valor(),
                dados.dia(), dados.frequencia(), dados.inicio(), dados.fim(), dados.observacao(), atual.puladas(),
                desde, valorDesde);
        return detalhar(recorrencias.salvar(editada));
    }

    @Override
    public void remover(Long id) {
        Recorrencia atual = exigir(id);
        acesso.exigirPodeAlterar(atual.membroId());
        if (lancamentos.existeDaRecorrencia(id)) {
            throw new ConflitoException("A recorrência já tem ocorrências confirmadas: defina o fim em vez de excluir");
        }
        recorrencias.remover(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OcorrenciaPendente> pendentes() {
        confirmacaoAutomatica.processar();
        LocalDate hoje = relogio.hoje();
        Competencia ate = Competencia.de(hoje);
        Competencia de = ate.mais(-11);
        Set<Ocorrencia> confirmadas = lancamentos.listarOcorrenciasConfirmadas(de, ate).stream()
                .map(l -> new Ocorrencia(l.recorrenciaId(), l.competenciaRecorrencia()))
                .collect(Collectors.toCollection(HashSet::new));
        Detalhador d = detalhador();
        List<Recorrencia> lista = recorrencias.listar();
        PrevisaoRecorrencias previsao = previsaoFamilia.calcular(lista);
        List<OcorrenciaPendente> pendentes = new ArrayList<>();
        for (Recorrencia r : lista) {
            RecorrenciaDetalhada detalhada = null;
            for (Competencia c = de; !c.depoisDe(ate); c = c.seguinte()) {
                if (r.ocorreEm(c) && !r.dataEm(c).isAfter(hoje) && !confirmadas.contains(new Ocorrencia(r.id(), c))) {
                    detalhada = detalhada != null ? detalhada : detalhar(r, d, previsao);
                    pendentes.add(new OcorrenciaPendente(detalhada, c, r.dataEm(c), previsao.valorEm(r, c)));
                }
            }
        }
        pendentes.sort(Comparator.comparing(OcorrenciaPendente::data)
                .thenComparing(p -> p.recorrencia().recorrencia().id()));
        return pendentes;
    }

    @Override
    public LancamentoDetalhado confirmar(Long id, Competencia competencia, Dinheiro valor, LocalDate data) {
        Recorrencia r = exigirOcorrencia(id, competencia);
        if (r.puladas().contains(competencia)) {
            throw new RegraNegocioException("A ocorrência de " + competencia + " foi pulada: desfaça o pulo antes");
        }
        Categoria categoria = planoContas.buscarCategoria(r.categoriaId()).orElseThrow();
        categoria.exigirLancavel(planoContas.buscarGrupo(categoria.grupoId()).orElseThrow());
        acesso.responsavel(r.membroId());
        // Sem valor informado, vale o previsto (a estimativa pela regra, do mês atual em diante, ou o cadastrado).
        Dinheiro previsto = valor != null ? valor : previsaoFamilia.calcular(List.of(r)).valorEm(r, competencia);
        Lancamento lancamento = new Lancamento(null, r.categoriaId(), r.membroId(), r.descricao(),
                previsto, data != null ? data : r.dataEm(competencia), r.id(), competencia,
                null, null);
        return detalhador().detalhar(lancamentos.salvar(lancamento));
    }

    @Override
    public RecorrenciaDetalhada pular(Long id, Competencia competencia) {
        Recorrencia r = exigirOcorrencia(id, competencia);
        Set<Competencia> puladas = new HashSet<>(r.puladas());
        puladas.add(competencia);
        return detalhar(recorrencias.salvar(r.comPuladas(puladas)));
    }

    @Override
    public RecorrenciaDetalhada desfazerPulo(Long id, Competencia competencia) {
        Recorrencia r = exigir(id);
        acesso.exigirPodeAlterar(r.membroId());
        Set<Competencia> puladas = new HashSet<>(r.puladas());
        puladas.remove(competencia);
        return detalhar(recorrencias.salvar(r.comPuladas(puladas)));
    }

    /** Recorrência com ocorrência agendada na competência e ainda não confirmada, que o usuário pode alterar. */
    private Recorrencia exigirOcorrencia(Long id, Competencia competencia) {
        obrigatorio(competencia, "competência");
        Recorrencia r = exigir(id);
        acesso.exigirPodeAlterar(r.membroId());
        if (!r.agendadaEm(competencia)) {
            throw new RegraNegocioException("A recorrência não tem ocorrência em " + competencia);
        }
        if (lancamentos.existeOcorrencia(id, competencia)) {
            throw new ConflitoException("A ocorrência de " + competencia + " já foi confirmada");
        }
        return r;
    }

    private Membro validar(DadosRecorrencia dados) {
        Categoria categoria = planoContas.buscarCategoria(obrigatorio(dados.categoriaId(), "categoria"))
                .orElseThrow(() -> new NaoEncontradoException("Categoria não encontrada: " + dados.categoriaId()));
        Grupo grupo = planoContas.buscarGrupo(categoria.grupoId()).orElseThrow();
        categoria.exigirLancavel(grupo);
        if (dados.confirmarSozinha() && grupo.tipo() != TipoMovimento.DESPESA) {
            throw new RegraNegocioException("Só despesa confirma sozinha: receita que se repete precisa ser conferida");
        }
        Membro membro = acesso.responsavel(dados.membroId());
        acesso.exigirPodeAlterar(membro.id());
        return membro;
    }

    private Recorrencia exigir(Long id) {
        return recorrencias.buscar(id).orElseThrow(() -> new NaoEncontradoException("Recorrência não encontrada: " + id));
    }

    private Detalhador detalhador() {
        return new Detalhador(planoContas.listarCategorias(), planoContas.listarGrupos(), acesso.membrosPorId());
    }

    private RecorrenciaDetalhada detalhar(Recorrencia r) {
        return detalhar(r, detalhador(), previsaoFamilia.calcular(List.of(r)));
    }

    private RecorrenciaDetalhada detalhar(Recorrencia r, Detalhador d, PrevisaoRecorrencias previsao) {
        int ano = relogio.hoje().getYear();
        Categoria c = d.categoria(r.categoriaId());
        Dinheiro noAno = r.ocorrenciasNoAno(ano).stream().map(comp -> previsao.valorEm(r, comp))
                .reduce(Dinheiro.ZERO, Dinheiro::somar);
        return new RecorrenciaDetalhada(r, c, d.grupo(c.grupoId()), d.membro(r.membroId()), ano, noAno,
                r.valorAnualizado(), previsao.de(r));
    }
}
