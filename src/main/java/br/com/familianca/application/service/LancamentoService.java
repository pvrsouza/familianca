package br.com.familianca.application.service;

import br.com.familianca.application.port.in.GerenciarLancamentos;
import br.com.familianca.application.port.out.DescricaoUsada;
import br.com.familianca.application.port.out.LancamentoRepository;
import br.com.familianca.application.port.out.PlanoContasRepository;
import br.com.familianca.application.port.out.RecorrenciaRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.domain.Validacoes;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.Parcela;
import br.com.familianca.domain.model.Recorrencia;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.service.GeradorParcelas;
import br.com.familianca.domain.vo.Competencia;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static br.com.familianca.domain.Validacoes.obrigatorio;

@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class LancamentoService implements GerenciarLancamentos {

    private static final GeradorParcelas GERADOR = new GeradorParcelas();
    /** Sem nada digitado: só as mais comuns, como sugestão direta. */
    private static final int SUGESTOES_DIRETAS = 3;
    /** Digitando: o que bate com o termo, das mais parecidas para as menos. */
    private static final int MAXIMO_ENCONTRADAS = 8;
    private static final int TERMO_MAXIMO = 200;

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
        acesso.exigirSaldoInicial();
        Lancamento novo = new Lancamento(null, dados.categoriaId(), membro.id(), dados.descricao(), dados.valor(),
                dados.data(), null, null, null, dados.observacao());
        return detalhador().detalhar(lancamentos.salvar(novo));
    }

    @Override
    public List<LancamentoDetalhado> lancarParcelado(DadosLancamento dados, int parcelas) {
        Membro membro = validar(dados);
        acesso.exigirSaldoInicial();
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

    @Override
    @Transactional(readOnly = true)
    public List<String> descricoesUsadas(Long categoriaId, String termo) {
        planoContas.buscarCategoria(obrigatorio(categoriaId, "categoria"))
                .orElseThrow(() -> new NaoEncontradoException("Categoria não encontrada: " + categoriaId));
        String busca = termo == null ? "" : chaveDescricao(Validacoes.limpar(termo));
        if (busca.length() > TERMO_MAXIMO) {
            throw new RegraNegocioException("A busca pode ter no máximo " + TERMO_MAXIMO + " caracteres");
        }
        return busca.isEmpty() ? maisComuns(categoriaId) : buscar(categoriaId, busca);
    }

    /** As mais lançadas; recorrência ainda sem lançamento completa a lista. */
    private List<String> maisComuns(Long categoriaId) {
        Map<String, String> unicas = new LinkedHashMap<>();
        lancamentos.descricoesMaisUsadas(categoriaId, SUGESTOES_DIRETAS * 2)
                .forEach(d -> unicas.putIfAbsent(chaveDescricao(d), d));
        recorrencias.listar().stream()
                .filter(r -> r.categoriaId().equals(categoriaId))
                .map(Recorrencia::descricao)
                .sorted()
                .forEach(d -> unicas.putIfAbsent(chaveDescricao(d), d));
        return unicas.values().stream().limit(SUGESTOES_DIRETAS).toList();
    }

    /**
     * Começa com o termo primeiro, depois a mais parecida (pg_trgm) e, no empate, a mais usada. Variações da mesma
     * descrição (maiúsculas, acento, espaço) viram uma só: fica a mais usada.
     */
    private List<String> buscar(Long categoriaId, String busca) {
        Map<String, DescricaoUsada> unicas = new LinkedHashMap<>();
        Stream.concat(lancamentos.buscarDescricoes(categoriaId, busca, MAXIMO_ENCONTRADAS * 2).stream(),
                        recorrencias.buscarDescricoes(categoriaId, busca, MAXIMO_ENCONTRADAS * 2).stream())
                .forEach(d -> unicas.merge(chaveDescricao(d.descricao()), d,
                        (a, b) -> a.usos() >= b.usos() ? a : b));
        Comparator<DescricaoUsada> relevancia = Comparator
                .comparing((DescricaoUsada d) -> !chaveDescricao(d.descricao()).startsWith(busca))
                .thenComparing(DescricaoUsada::semelhanca, Comparator.reverseOrder())
                .thenComparing(DescricaoUsada::usos, Comparator.reverseOrder());
        return unicas.values().stream().sorted(relevancia).limit(MAXIMO_ENCONTRADAS)
                .map(DescricaoUsada::descricao).toList();
    }

    /** "Mercado", "mercado " e "Mércado" são a mesma descrição. */
    private static String chaveDescricao(String descricao) {
        return Normalizer.normalize(descricao.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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
        return new Detalhador(planoContas.listarCategorias(), planoContas.listarGrupos(), acesso.membrosPorId(),
                recorrencias.listar());
    }

    /** Junta categoria, grupo e membro carregados uma vez por chamada. */
    static final class Detalhador {

        private final Map<Long, Categoria> categorias;
        private final Map<Long, Grupo> grupos;
        private final Map<Long, Membro> membros;
        private final Map<Long, Recorrencia> recorrencias;

        Detalhador(List<Categoria> categorias, List<Grupo> grupos, Map<Long, Membro> membros,
                   List<Recorrencia> recorrencias) {
            this.categorias = categorias.stream().collect(Collectors.toMap(Categoria::id, Function.identity()));
            this.grupos = grupos.stream().collect(Collectors.toMap(Grupo::id, Function.identity()));
            this.membros = membros;
            this.recorrencias = recorrencias.stream().collect(Collectors.toMap(Recorrencia::id, Function.identity()));
        }

        LancamentoDetalhado detalhar(Lancamento l) {
            Categoria c = categorias.get(l.categoriaId());
            Grupo g = grupos.get(c.grupoId());
            return new LancamentoDetalhado(l, c, g, membros.get(l.membroId()), vencimento(l, g));
        }

        /** Vencimento da conta que o lançamento pagou: só confirmação de despesa que se repete e tem vencimento. */
        private LocalDate vencimento(Lancamento l, Grupo g) {
            Recorrencia r = l.recorrenciaId() != null ? recorrencias.get(l.recorrenciaId()) : null;
            return r != null && g.tipo() == TipoMovimento.DESPESA ? r.vencimentoEm(l.competenciaRecorrencia()) : null;
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
