package br.com.familianca.application.service;

import br.com.familianca.application.port.in.GerenciarImportacoes;
import br.com.familianca.application.port.out.CategoriaPorDescricao;
import br.com.familianca.application.port.out.ImportacaoRepository;
import br.com.familianca.application.port.out.LancamentoRepository;
import br.com.familianca.application.port.out.LocalSaldoRepository;
import br.com.familianca.application.port.out.PlanoContasRepository;
import br.com.familianca.application.port.out.UsuarioAtual;
import br.com.familianca.domain.exception.AcessoNegadoException;
import br.com.familianca.domain.exception.ArquivoInvalidoException;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.LocalSaldo;
import br.com.familianca.domain.model.LoteImportacao;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.Natureza;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.service.LeitorOfx;
import br.com.familianca.domain.service.LeitorOfx.Extrato;
import br.com.familianca.domain.service.LeitorOfx.Transacao;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static br.com.familianca.domain.Validacoes.obrigatorio;

/** Nada aqui registra descrição nem valor das linhas do extrato em log: só índices e contagens. */
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class ImportacaoService implements GerenciarImportacoes {

    public static final int TAMANHO_MAXIMO_BYTES = 2 * 1024 * 1024;
    public static final String A_CLASSIFICAR = "A classificar";
    private static final int TOLERANCIA_DIAS = 2;
    /** Já tem outro caminho no Familiança (fatura do cartão, entre contas, aplicações e resgates): vem para ignorar. */
    private static final Pattern IGNORAVEL = Pattern.compile(
            "(pagamento|pagto|pgto)\\.? (de |da )?(fatura|cartao)|transferencia (entre contas|propria|mesma titularidade)"
                    + "|transf\\.? (entre contas|propria)|\\baplic(?!ativo)|\\bresg");

    private final ImportacaoRepository importacoes;
    private final LancamentoRepository lancamentos;
    private final LocalSaldoRepository locais;
    private final PlanoContasRepository planoContas;
    private final AcessoFamilia acesso;
    private final UsuarioAtual usuario;
    private final CasamentoRecorrencia casamento;

    public ImportacaoService(ImportacaoRepository importacoes, LancamentoRepository lancamentos,
                             LocalSaldoRepository locais, PlanoContasRepository planoContas, AcessoFamilia acesso,
                             UsuarioAtual usuario, CasamentoRecorrencia casamento) {
        this.importacoes = importacoes;
        this.lancamentos = lancamentos;
        this.locais = locais;
        this.planoContas = planoContas;
        this.acesso = acesso;
        this.usuario = usuario;
        this.casamento = casamento;
    }

    // Sem readOnly: listar as ocorrências pendentes pode gravar confirmações automáticas.
    @Override
    public Analise analisar(byte[] conteudo, Long localSaldoId) {
        exigirLocal(localSaldoId);
        if (conteudo == null || conteudo.length == 0) {
            throw new ArquivoInvalidoException("O arquivo está vazio");
        }
        if (conteudo.length > TAMANHO_MAXIMO_BYTES) {
            throw new ArquivoInvalidoException("O arquivo é grande demais: o máximo é 2 MB");
        }
        Extrato extrato = LeitorOfx.ler(conteudo);
        List<Transacao> transacoes = extrato.transacoes();

        Set<String> existentes = importacoes.chavesExistentes(localSaldoId,
                transacoes.stream().map(Transacao::chave).toList());
        Map<Long, Categoria> categorias = planoContas.listarCategorias().stream()
                .collect(Collectors.toMap(Categoria::id, Function.identity()));
        Map<Long, Grupo> grupos = planoContas.listarGrupos().stream()
                .collect(Collectors.toMap(Grupo::id, Function.identity()));
        Map<String, Long> sugestoes = sugestoesDeCategoria(transacoes, categorias, grupos);
        Map<BigDecimal, List<Lancamento>> digitados = digitadosPerto(extrato);

        List<LinhaAnalise> linhas = new ArrayList<>();
        int novas = 0;
        int jaImportadas = 0;
        int duplicatas = 0;
        for (Transacao t : transacoes) {
            TipoMovimento tipo = t.valor().signum() > 0 ? TipoMovimento.RECEITA : TipoMovimento.DESPESA;
            BigDecimal valor = t.valor().abs();
            LancamentoParecido parecido = null;
            Status status;
            if (existentes.contains(t.chave())) {
                status = Status.JA_IMPORTADA;
                jaImportadas++;
            } else if ((parecido = parecido(t, tipo, valor, digitados, categorias, grupos)) != null) {
                status = Status.POSSIVEL_DUPLICATA;
                duplicatas++;
            } else {
                status = Status.NOVA;
                novas++;
            }
            Acao acao = status == Status.JA_IMPORTADA || IGNORAVEL.matcher(LeitorOfx.normalizar(t.descricao())).find()
                    ? Acao.IGNORAR : Acao.IMPORTAR;
            linhas.add(new LinhaAnalise(linhas.size(), t.chave(), t.data(), t.descricao(), valor, tipo, status, acao,
                    sugestoes.get(tipo + "|" + LeitorOfx.normalizar(t.descricao())), parecido, null));
        }
        casarComRecorrencias(linhas);
        return new Analise(extrato.banco(), mascarar(extrato.conta()), null, extrato.periodoDe(),
                extrato.periodoAte(), new Resumo(linhas.size(), novas, jaImportadas, duplicatas), linhas);
    }

    @Override
    public Resultado importar(Pedido p) {
        LocalSaldo local = exigirLocal(p.localSaldoId());
        List<LinhaPedido> linhas = p.linhas();
        if (linhas == null || linhas.isEmpty() || linhas.size() > LeitorOfx.MAXIMO_LINHAS) {
            throw new RegraNegocioException("Envie de 1 a " + LeitorOfx.MAXIMO_LINHAS + " linhas");
        }
        if (p.periodoDe() == null || p.periodoAte() == null || p.periodoAte().isBefore(p.periodoDe())) {
            throw new RegraNegocioException("Período do extrato inválido");
        }
        acesso.exigirSaldoInicial();

        // A conferência é refeita aqui: a análise pode ter ficado aberta enquanto outra importação gravava.
        Set<String> gravadas = new HashSet<>(importacoes.chavesExistentes(local.id(),
                linhas.stream().map(LinhaPedido::chave).filter(c -> c != null && !c.isBlank()).toList()));
        LoteImportacao lote = importacoes.criarLote(local.id(), p.periodoDe(), p.periodoAte(), usuario.membroId());
        Map<Long, Categoria> categorias = planoContas.listarCategorias().stream()
                .collect(Collectors.toMap(Categoria::id, Function.identity(), (a, b) -> a, HashMap::new));
        Map<Long, Grupo> grupos = planoContas.listarGrupos().stream()
                .collect(Collectors.toMap(Grupo::id, Function.identity(), (a, b) -> a, HashMap::new));
        Map<Long, Membro> membros = new HashMap<>();
        Map<TipoMovimento, Categoria> aClassificar = new HashMap<>();

        int importadas = 0;
        int ignoradas = 0;
        int confirmadas = 0;
        int jaExistiam = 0;
        for (int i = 0; i < linhas.size(); i++) {
            LinhaPedido l = linhas.get(i);
            try {
                String chave = obrigatorio(l.chave(), "chave");
                obrigatorio(l.acao(), "ação");
                if (!gravadas.add(chave)) {
                    jaExistiam++;
                    continue;
                }
                switch (l.acao()) {
                    case IGNORAR -> {
                        importacoes.registrarIgnorada(lote.id(), local.id(), chave);
                        ignoradas++;
                    }
                    case IMPORTAR -> {
                        Categoria categoria = categoriaDaLinha(l, categorias, grupos, aClassificar);
                        Membro membro = membros.computeIfAbsent(l.membroId() != null ? l.membroId() : -1L,
                                id -> responsavel(l.membroId()));
                        lancamentos.salvarImportado(novoLancamento(l, categoria, membro), lote.id(), local.id(), chave);
                        importadas++;
                    }
                    case CONFIRMAR_RECORRENCIA -> {
                        confirmarRecorrencia(l, lote, local, chave);
                        confirmadas++;
                    }
                }
            } catch (RegraNegocioException e) {
                throw new RegraNegocioException("Linha " + (i + 1) + ": " + e.getMessage());
            }
        }
        importacoes.atualizarContagens(lote.id(), importadas, ignoradas, confirmadas);
        return new Resultado(lote.id(), importadas, ignoradas, confirmadas, jaExistiam);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Lote> listar() {
        Map<Long, String> nomesLocais = locais.listar().stream()
                .collect(Collectors.toMap(LocalSaldo::id, LocalSaldo::nome));
        Map<Long, Membro> membros = acesso.membrosPorId();
        return importacoes.listarLotes().stream()
                .map(l -> new Lote(l.id(), l.localSaldoId(), nomesLocais.get(l.localSaldoId()), l.periodoDe(),
                        l.periodoAte(), l.importadas(), l.ignoradas(), l.confirmadas(),
                        membros.containsKey(l.membroId()) ? membros.get(l.membroId()).nome() : null, l.criadoEm(),
                        podeDesfazer(l)))
                .toList();
    }

    @Override
    public Desfeito desfazer(Long loteId) {
        LoteImportacao lote = importacoes.buscarLote(loteId)
                .orElseThrow(() -> new NaoEncontradoException("Importação", loteId));
        if (!podeDesfazer(lote)) {
            throw new AcessoNegadoException("Só o dono da família ou quem fez a importação pode desfazê-la");
        }
        ImportacaoRepository.Desfeito d = importacoes.desfazer(loteId);
        return new Desfeito(d.removidos(), d.mantidosPorEdicao());
    }

    /** "Pagar = confirmar": confirma a ocorrência com o valor e a data do extrato e guarda a chave nela. */
    private void confirmarRecorrencia(LinhaPedido l, LoteImportacao lote, LocalSaldo local, String chave) {
        Lancamento confirmado = casamento.confirmar(obrigatorio(l.recorrenciaId(), "recorrência"),
                Competencia.parse(obrigatorio(l.competencia(), "competência")), obrigatorio(l.data(), "data"),
                Dinheiro.of(obrigatorio(l.valor(), "valor")));
        lancamentos.salvarImportado(confirmado, lote.id(), local.id(), chave);
    }

    /** Só linhas novas e que não parecem transferência ou fatura são oferecidas a uma conta pendente. */
    private void casarComRecorrencias(List<LinhaAnalise> linhas) {
        List<CasamentoRecorrencia.LinhaCasavel> candidatas = linhas.stream()
                .filter(l -> l.status() == Status.NOVA && l.acaoSugerida() == Acao.IMPORTAR)
                .map(l -> new CasamentoRecorrencia.LinhaCasavel(l.indice(), l.data(), Dinheiro.of(l.valor()), l.tipo(),
                        l.descricao()))
                .toList();
        casamento.casar(candidatas).forEach((indice, o) -> {
            LinhaAnalise l = linhas.get(indice);
            linhas.set(indice, new LinhaAnalise(l.indice(), l.chave(), l.data(), l.descricao(), l.valor(), l.tipo(),
                    l.status(), Acao.CONFIRMAR_RECORRENCIA, l.categoriaIdSugerida(), l.lancamentoParecido(),
                    new OcorrenciaPendente(o.recorrenciaId(), o.competencia().toString(), o.descricao(),
                            o.valor().valor(), o.vencimento())));
        });
    }

    private boolean podeDesfazer(LoteImportacao lote) {
        return usuario.dono() || usuario.membroId().equals(lote.membroId());
    }

    /** Lugar da família; MEMBRO não mexe em lugar que é de outra pessoa. */
    private LocalSaldo exigirLocal(Long id) {
        LocalSaldo local = locais.buscar(obrigatorio(id, "lugar do saldo"))
                .orElseThrow(() -> new NaoEncontradoException("Lugar do saldo", id));
        if (local.membroId() != null && !usuario.dono() && !local.membroId().equals(usuario.membroId())) {
            throw new AcessoNegadoException("Só o dono da família importa extrato num lugar de outra pessoa");
        }
        return local;
    }

    private Membro responsavel(Long membroId) {
        Membro membro = acesso.responsavel(membroId);
        acesso.exigirPodeAlterar(membro.id());
        return membro;
    }

    private Lancamento novoLancamento(LinhaPedido l, Categoria categoria, Membro membro) {
        LocalDate data = obrigatorio(l.data(), "data");
        if (data.getYear() < LeitorOfx.ANO_MIN || data.getYear() > LeitorOfx.ANO_MAX) {
            throw new RegraNegocioException("Data fora da faixa de " + LeitorOfx.ANO_MIN + " a " + LeitorOfx.ANO_MAX);
        }
        String descricao = l.descricao() == null ? "" : l.descricao().replaceAll("\\s+", " ").strip();
        if (descricao.isEmpty()) {
            descricao = "Sem descrição";
        }
        if (descricao.length() > Lancamento.DESCRICAO_MAXIMO) {
            descricao = descricao.substring(0, Lancamento.DESCRICAO_MAXIMO).strip();
        }
        return new Lancamento(null, categoria.id(), membro.id(), descricao,
                Dinheiro.of(obrigatorio(l.valor(), "valor")), data, null, null, null, null);
    }

    /** Categoria escolhida (ativa, lançável e do mesmo tipo da linha) ou, sem escolha, a "A classificar". */
    private Categoria categoriaDaLinha(LinhaPedido l, Map<Long, Categoria> categorias, Map<Long, Grupo> grupos,
                                       Map<TipoMovimento, Categoria> aClassificar) {
        TipoMovimento tipo = obrigatorio(l.tipo(), "tipo");
        Categoria categoria;
        if (l.categoriaId() == null) {
            categoria = aClassificar.computeIfAbsent(tipo, t -> criarOuBuscarAClassificar(t, categorias, grupos));
        } else {
            categoria = categorias.get(l.categoriaId());
            if (categoria == null) {
                throw new NaoEncontradoException("Categoria não encontrada: " + l.categoriaId());
            }
        }
        Grupo grupo = grupos.get(categoria.grupoId());
        categoria.exigirLancavel(grupo);
        if (grupo.tipo() != tipo) {
            throw new RegraNegocioException("A categoria " + categoria.nome() + " é de "
                    + (grupo.tipo() == TipoMovimento.RECEITA ? "receita" : "despesa") + " e a linha é de "
                    + (tipo == TipoMovimento.RECEITA ? "receita" : "despesa"));
        }
        return categoria;
    }

    /** "A classificar" de receita ou de despesa: a existente ou, se faltar, grupo e categoria criados na família. */
    private Categoria criarOuBuscarAClassificar(TipoMovimento tipo, Map<Long, Categoria> categorias,
                                                Map<Long, Grupo> grupos) {
        var existente = categorias.values().stream()
                .filter(c -> c.aClassificar() && grupos.get(c.grupoId()).tipo() == tipo)
                .findFirst();
        if (existente.isPresent()) {
            return existente.get();
        }
        Grupo grupo = grupos.values().stream()
                .filter(g -> g.tipo() == tipo && A_CLASSIFICAR.equalsIgnoreCase(g.nome())).findFirst()
                .orElseGet(() -> {
                    int ordem = grupos.values().stream().filter(g -> g.tipo() == tipo).mapToInt(Grupo::ordem)
                            .max().orElse(-1) + 1;
                    Grupo novo = planoContas.salvarGrupo(
                            new Grupo(null, tipo, Natureza.VARIAVEL, A_CLASSIFICAR, false, ordem, true));
                    grupos.put(novo.id(), novo);
                    return novo;
                });
        Categoria nova = planoContas.salvarCategoria(new Categoria(null, grupo.id(), A_CLASSIFICAR, 0, true, false, null, false,
                null, null, null, true));
        categorias.put(nova.id(), nova);
        return nova;
    }

    /** Última categoria usada pela família na mesma descrição e tipo; chave "TIPO|descrição normalizada". */
    private Map<String, Long> sugestoesDeCategoria(List<Transacao> transacoes, Map<Long, Categoria> categorias,
                                                   Map<Long, Grupo> grupos) {
        Set<String> descricoes = transacoes.stream().map(t -> LeitorOfx.normalizar(t.descricao()))
                .collect(Collectors.toSet());
        Map<String, Long> sugestoes = new HashMap<>();
        for (CategoriaPorDescricao c : lancamentos.categoriasPorDescricao(descricoes)) {
            Categoria categoria = categorias.get(c.categoriaId());
            Grupo grupo = categoria != null ? grupos.get(categoria.grupoId()) : null;
            if (grupo != null && categoria.ativa() && grupo.ativo() && !categoria.calculada()) {
                sugestoes.putIfAbsent(grupo.tipo() + "|" + c.descricao(), categoria.id());
            }
        }
        return sugestoes;
    }

    /** Lançamentos digitados à mão perto do período do extrato, por valor. */
    private Map<BigDecimal, List<Lancamento>> digitadosPerto(Extrato extrato) {
        LocalDate de = extrato.transacoes().stream().map(Transacao::data).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate ate = extrato.transacoes().stream().map(Transacao::data).max(Comparator.naturalOrder()).orElseThrow();
        return lancamentos.listarDigitados(de.minusDays(TOLERANCIA_DIAS), ate.plusDays(TOLERANCIA_DIAS)).stream()
                .collect(Collectors.groupingBy(l -> l.valor().valor()));
    }

    private LancamentoParecido parecido(Transacao t, TipoMovimento tipo, BigDecimal valor,
                                        Map<BigDecimal, List<Lancamento>> digitados, Map<Long, Categoria> categorias,
                                        Map<Long, Grupo> grupos) {
        return digitados.getOrDefault(valor, List.of()).stream()
                .filter(l -> Math.abs(ChronoUnit.DAYS.between(l.data(), t.data())) <= TOLERANCIA_DIAS)
                .filter(l -> grupos.get(categorias.get(l.categoriaId()).grupoId()).tipo() == tipo)
                .min(Comparator.comparingLong((Lancamento l) -> Math.abs(ChronoUnit.DAYS.between(l.data(), t.data())))
                        .thenComparing(Lancamento::id))
                .map(l -> new LancamentoParecido(l.id(), l.data(), l.descricao(), l.valor().valor(),
                        categorias.get(l.categoriaId()).nome()))
                .orElse(null);
    }

    /** Só os 4 últimos caracteres da conta do arquivo. */
    private static String mascarar(String conta) {
        if (conta == null) {
            return null;
        }
        String limpa = conta.replaceAll("[^A-Za-z0-9]", "");
        return limpa.isEmpty() ? null : "****" + limpa.substring(Math.max(0, limpa.length() - 4));
    }
}
