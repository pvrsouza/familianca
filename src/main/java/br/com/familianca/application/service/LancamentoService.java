package br.com.familianca.application.service;

import br.com.familianca.application.port.in.GerenciarLancamentos;
import br.com.familianca.application.port.out.LancamentoRepository;
import br.com.familianca.application.port.out.PlanoContasRepository;
import br.com.familianca.application.port.out.RecorrenciaRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.Parcela;
import br.com.familianca.domain.service.GeradorParcelas;
import br.com.familianca.domain.vo.Competencia;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static br.com.familianca.domain.Validacoes.obrigatorio;

@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class LancamentoService implements GerenciarLancamentos {

    private static final GeradorParcelas GERADOR = new GeradorParcelas();

    private final LancamentoRepository lancamentos;
    private final PlanoContasRepository planoContas;
    private final AcessoFamilia acesso;
    private final Relogio relogio;
    private final RecorrenciaRepository recorrencias;
    private final ConfirmacaoAutomatica confirmacaoAutomatica;

    public LancamentoService(LancamentoRepository lancamentos, PlanoContasRepository planoContas,
                             AcessoFamilia acesso, Relogio relogio, RecorrenciaRepository recorrencias,
                             ConfirmacaoAutomatica confirmacaoAutomatica) {
        this.lancamentos = lancamentos;
        this.planoContas = planoContas;
        this.acesso = acesso;
        this.relogio = relogio;
        this.recorrencias = recorrencias;
        this.confirmacaoAutomatica = confirmacaoAutomatica;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LancamentoDetalhado> listar(Filtro f) {
        confirmacaoAutomatica.processar();
        int ano = anoAtual();
        LocalDate de = f.de() != null ? f.de() : LocalDate.of(ano, 1, 1);
        LocalDate ate = f.ate() != null ? f.ate() : LocalDate.of(ano, 12, 31);
        if (ate.isBefore(de)) {
            throw new RegraNegocioException("Período inválido: " + de + " a " + ate);
        }
        Detalhador d = detalhador();
        return lancamentos.listar(de, ate).stream()
                .map(d::detalhar)
                .filter(l -> f.categoriaId() == null || l.categoria().id().equals(f.categoriaId()))
                .filter(l -> f.grupoId() == null || l.grupo().id().equals(f.grupoId()))
                .filter(l -> f.membroId() == null || l.lancamento().membroId().equals(f.membroId()))
                .filter(l -> f.tipo() == null || l.grupo().tipo() == f.tipo())
                .sorted(Comparator.comparing((LancamentoDetalhado l) -> l.lancamento().data())
                        .thenComparing(l -> l.lancamento().id()).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LancamentoDetalhado buscar(Long id) {
        return detalhador().detalhar(exigir(id));
    }

    @Override
    public LancamentoDetalhado lancar(DadosLancamento dados) {
        Membro membro = validar(dados);
        Lancamento novo = new Lancamento(null, dados.categoriaId(), membro.id(), dados.descricao(), dados.valor(),
                dados.data(), null, null, null, dados.observacao());
        return detalhador().detalhar(lancamentos.salvar(novo));
    }

    @Override
    public List<LancamentoDetalhado> lancarParcelado(DadosLancamento dados, int parcelas) {
        Membro membro = validar(dados);
        UUID parcelamento = UUID.randomUUID();
        List<Lancamento> novos = GERADOR.gerar(obrigatorio(dados.valor(), "valor"), parcelas,
                        obrigatorio(dados.data(), "data")).stream()
                .map(p -> new Lancamento(null, dados.categoriaId(), membro.id(), dados.descricao(), p.valor(),
                        p.data(), null, null, new Parcela(parcelamento, p.numero(), parcelas), dados.observacao()))
                .toList();
        Detalhador d = detalhador();
        return lancamentos.salvarTodos(novos).stream()
                .sorted(Comparator.comparingInt(l -> l.parcela().numero()))
                .map(d::detalhar).toList();
    }

    @Override
    public LancamentoDetalhado atualizar(Long id, DadosLancamento dados) {
        Lancamento atual = exigir(id);
        acesso.exigirPodeAlterar(atual.membroId());
        Membro membro = validar(dados);
        Lancamento editado = atual.editado(dados.categoriaId(), membro.id(), dados.descricao(), dados.valor(),
                dados.data(), dados.observacao());
        return detalhador().detalhar(lancamentos.salvar(editado));
    }

    @Override
    public void remover(Long id, boolean todasAsParcelas) {
        Lancamento atual = exigir(id);
        acesso.exigirPodeAlterar(atual.membroId());
        if (todasAsParcelas && atual.parcela() != null) {
            lancamentos.listarParcelamento(atual.parcela().parcelamentoId())
                    .forEach(p -> acesso.exigirPodeAlterar(p.membroId()));
            lancamentos.removerParcelamento(atual.parcela().parcelamentoId());
        } else {
            lancamentos.remover(id);
            marcarNaoHouve(atual);
        }
    }

    /**
     * Excluir a confirmação de uma recorrência que confirma sozinha marca o mês como "não houve" (pulado); sem isso,
     * a ocorrência voltaria a ser confirmada sozinha na próxima leitura.
     */
    private void marcarNaoHouve(Lancamento excluido) {
        if (excluido.recorrenciaId() == null) {
            return;
        }
        recorrencias.buscar(excluido.recorrenciaId())
                .filter(r -> r.confirmaSozinhaEm(excluido.competenciaRecorrencia(), relogio.hoje()))
                .ifPresent(r -> {
                    Set<Competencia> puladas = new HashSet<>(r.puladas());
                    puladas.add(excluido.competenciaRecorrencia());
                    recorrencias.salvar(r.comPuladas(puladas));
                });
    }

    @Override
    public int anoAtual() {
        return relogio.hoje().getYear();
    }

    /** Categoria lançável e membro ativo; MEMBRO só lança em nome próprio. */
    private Membro validar(DadosLancamento dados) {
        Categoria categoria = planoContas.buscarCategoria(obrigatorio(dados.categoriaId(), "categoria"))
                .orElseThrow(() -> new NaoEncontradoException("Categoria não encontrada: " + dados.categoriaId()));
        categoria.exigirLancavel(planoContas.buscarGrupo(categoria.grupoId()).orElseThrow());
        Membro membro = acesso.responsavel(dados.membroId());
        acesso.exigirPodeAlterar(membro.id());
        return membro;
    }

    private Lancamento exigir(Long id) {
        return lancamentos.buscar(id).orElseThrow(() -> new NaoEncontradoException("Lançamento", id));
    }

    private Detalhador detalhador() {
        return new Detalhador(planoContas.listarCategorias(), planoContas.listarGrupos(), acesso.membrosPorId());
    }

    /** Junta categoria, grupo e membro carregados uma vez por chamada. */
    static final class Detalhador {

        private final Map<Long, Categoria> categorias;
        private final Map<Long, Grupo> grupos;
        private final Map<Long, Membro> membros;

        Detalhador(List<Categoria> categorias, List<Grupo> grupos, Map<Long, Membro> membros) {
            this.categorias = categorias.stream().collect(Collectors.toMap(Categoria::id, Function.identity()));
            this.grupos = grupos.stream().collect(Collectors.toMap(Grupo::id, Function.identity()));
            this.membros = membros;
        }

        LancamentoDetalhado detalhar(Lancamento l) {
            Categoria c = categorias.get(l.categoriaId());
            return new LancamentoDetalhado(l, c, grupos.get(c.grupoId()), membros.get(l.membroId()));
        }

        Categoria categoria(Long id) {
            return categorias.get(id);
        }

        Grupo grupo(Long id) {
            return grupos.get(id);
        }

        Membro membro(Long id) {
            return membros.get(id);
        }
    }
}
