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
import br.com.familianca.domain.model.Estimativa;
import br.com.familianca.domain.model.FormaPagamento;
import br.com.familianca.domain.model.Frequencia;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.NotaFiscal;
import br.com.familianca.domain.model.Recorrencia;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.model.VisaoAnual;
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
import java.util.Map;
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
        acesso.exigirSaldoInicial();
        // Despesa nova já nasce prevista pela média dos últimos 3 meses (regra própria, que dá para trocar depois).
        boolean estimavel = !dados.confirmarSozinha() && planoContas.buscarCategoria(dados.categoriaId())
                .flatMap(c -> planoContas.buscarGrupo(c.grupoId()))
                .map(g -> g.tipo() == TipoMovimento.DESPESA).orElse(false);
        Recorrencia nova = new Recorrencia(null, dados.categoriaId(), membro.id(), dados.descricao(), dados.valor(),
                dados.dia(), dados.frequencia(), dados.inicio(), dados.fim(), dados.observacao(), Set.of(),
                dados.confirmarSozinha() ? relogio.hoje() : null, null, dados.semVencimento(),
                dados.formaPagamento(), Map.of(), Map.of(), estimavel ? Estimativa.MEDIA_3M : null);
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
        // Mudou o valor (reajuste): a estimativa passa a considerar só as confirmações daqui em diante. Se o mês atual
        // já foi confirmado (com o valor antigo), o reajuste vale a partir do mês seguinte.
        Competencia mes = Competencia.de(relogio.hoje());
        Competencia valorDesde = dados.valor().equals(atual.valor()) ? atual.valorDesde()
                : lancamentos.existeOcorrencia(id, mes) ? mes.seguinte() : mes;
        Recorrencia editada = new Recorrencia(id, dados.categoriaId(), membro.id(), dados.descricao(), dados.valor(),
                dados.dia(), dados.frequencia(), dados.inicio(), dados.fim(), dados.observacao(), atual.puladas(),
                desde, valorDesde, dados.semVencimento(), dados.formaPagamento(), atual.vencimentosAjustados(),
                atual.valoresAjustados(), atual.estimativa(), atual.estimativaDesde(), atual.estimativaAntes());
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
                    pendentes.add(new OcorrenciaPendente(detalhada, c, r.dataEm(c), previsao.valorEm(r, c),
                            detalhada.grupo().tipo() == TipoMovimento.DESPESA ? r.vencimentoEm(c) : null));
                }
            }
        }
        pendentes.sort(Comparator.comparing(OcorrenciaPendente::data)
                .thenComparing(p -> p.recorrencia().recorrencia().id()));
        return pendentes;
    }

    @Override
    @Transactional(readOnly = true)
    public ContasDoMes contasAPagar(Competencia competencia) {
        obrigatorio(competencia, "competência");
        exigirNoHorizonte(competencia);
        confirmacaoAutomatica.processar();
        LocalDate hoje = relogio.hoje();
        Competencia mesAtual = Competencia.de(hoje);
        Detalhador d = detalhador();
        List<Recorrencia> todas = recorrencias.listar();
        List<Recorrencia> despesas = todas.stream()
                .filter(r -> d.grupo(d.categoria(r.categoriaId()).grupoId()).tipo() == TipoMovimento.DESPESA)
                .filter(r -> r.pedePagamento() || r.saiSozinha())
                .toList();
        PrevisaoRecorrencias previsao = previsaoFamilia.calcular(todas);
        Map<Ocorrencia, Lancamento> pagas = lancamentos.listarOcorrenciasConfirmadas(competencia, competencia).stream()
                .collect(Collectors.toMap(l -> new Ocorrencia(l.recorrenciaId(), l.competenciaRecorrencia()), l -> l,
                        (a, b) -> a));
        List<ContaDoMes> aPagar = new ArrayList<>();
        List<ContaDoMes> paraConferir = new ArrayList<>();
        for (Recorrencia r : despesas) {
            if (r.ocorreEm(competencia)) {
                Lancamento pago = pagas.get(new Ocorrencia(r.id(), competencia));
                ContaDoMes conta = new ContaDoMes(detalhar(r, d, previsao), competencia,
                        previsao.valorEm(r, competencia), r.vencimentoEm(competencia),
                        pago != null ? d.detalhar(pago) : null);
                (r.pedePagamento() ? aPagar : paraConferir).add(conta);
            }
        }
        Comparator<ContaDoMes> porData = Comparator.comparing((ContaDoMes c) -> c.recorrencia().recorrencia()
                        .dataEm(competencia))
                .thenComparing(c -> c.recorrencia().recorrencia().descricao(), String.CASE_INSENSITIVE_ORDER);
        aPagar.sort(porData);
        paraConferir.sort(porData);
        return new ContasDoMes(competencia, aPagar, paraConferir,
                competencia.equals(mesAtual) ? anterioresEmAberto(despesas, mesAtual, hoje) : List.of());
    }

    /** Meses dos últimos 12 (antes do atual) com conta a pagar vencida e não paga, mais antigos primeiro. */
    private List<MesEmAberto> anterioresEmAberto(List<Recorrencia> despesas, Competencia mesAtual, LocalDate hoje) {
        Competencia de = mesAtual.mais(-11);
        Competencia ate = mesAtual.mais(-1);
        Set<Ocorrencia> confirmadas = lancamentos.listarOcorrenciasConfirmadas(de, ate).stream()
                .map(l -> new Ocorrencia(l.recorrenciaId(), l.competenciaRecorrencia()))
                .collect(Collectors.toSet());
        List<MesEmAberto> meses = new ArrayList<>();
        for (Competencia c = de; !c.depoisDe(ate); c = c.seguinte()) {
            Competencia mes = c;
            int contas = (int) despesas.stream()
                    .filter(r -> r.pedePagamento() && r.ocorreEm(mes) && !r.dataEm(mes).isAfter(hoje)
                            && !confirmadas.contains(new Ocorrencia(r.id(), mes)))
                    .count();
            if (contas > 0) {
                meses.add(new MesEmAberto(mes, contas));
            }
        }
        return meses;
    }

    @Override
    public LancamentoDetalhado confirmar(Long id, Competencia competencia, Dinheiro valor, LocalDate data,
                                         NotaFiscal notaFiscal) {
        Recorrencia r = antesDoInicio(id, competencia) ? exigirHistorico(id, competencia)
                : exigirOcorrencia(id, competencia);
        acesso.exigirSaldoInicial();
        if (r.puladas().contains(competencia)) {
            throw new RegraNegocioException("A ocorrência de " + competencia + " foi pulada: desfaça o pulo antes");
        }
        Categoria categoria = planoContas.buscarCategoria(r.categoriaId()).orElseThrow();
        categoria.exigirLancavel(planoContas.buscarGrupo(categoria.grupoId()).orElseThrow());
        categoria.exigirAceitaNotaFiscal(notaFiscal);
        // Nota fiscal: o mês em que conta é o do pagamento, então ele é informado a cada mês (não vale o dia previsto).
        if (categoria.tributavel() && data == null) {
            throw new RegraNegocioException("Informem o dia em que o dinheiro de " + r.descricao() + " (" + competencia
                    + ") entrou na conta");
        }
        acesso.responsavel(r.membroId());
        // Sem valor informado, vale o previsto (a estimativa pela regra, do mês atual em diante, ou o cadastrado).
        Dinheiro previsto = valor != null ? valor : previsaoFamilia.calcular(List.of(r)).valorEm(r, competencia);
        Lancamento lancamento = new Lancamento(null, r.categoriaId(), r.membroId(), r.descricao(),
                previsto, data != null ? data : r.dataEm(competencia), r.id(), competencia,
                null, null, notaFiscal);
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

    @Override
    public RecorrenciaDetalhada ajustarVencimento(Long id, Competencia competencia, LocalDate vencimento) {
        obrigatorio(competencia, "competência");
        Recorrencia r = exigir(id);
        acesso.exigirPodeAlterar(r.membroId());
        if (!r.agendadaEm(competencia)) {
            throw new RegraNegocioException("A recorrência não tem ocorrência em " + competencia);
        }
        exigirNoHorizonte(competencia);
        if (!r.temVencimento()) {
            throw new RegraNegocioException(r.formaPagamento() == FormaPagamento.CARTAO_CREDITO
                    ? "Conta cobrada no cartão não tem vencimento próprio: vale o vencimento da fatura"
                    : "Esta conta está marcada como sem vencimento");
        }
        return detalhar(recorrencias.salvar(r.comVencimentoAjustado(competencia, vencimento)));
    }

    @Override
    public RecorrenciaDetalhada ajustarValor(Long id, Competencia competencia, Dinheiro valor, boolean daquiEmDiante) {
        Recorrencia r = exigirOcorrencia(id, competencia);
        if (r.puladas().contains(competencia)) {
            throw new RegraNegocioException("A ocorrência de " + competencia + " foi pulada: desfaça o pulo antes");
        }
        if (!daquiEmDiante) {
            return detalhar(recorrencias.salvar(r.comValorAjustado(competencia, valor)));
        }
        if (valor == null) {
            throw new RegraNegocioException("Informem o novo valor de " + competencia + " em diante");
        }
        // Os meses antes ficam como estão: o previsto de hoje deles é guardado antes de mudar o valor cadastrado.
        PrevisaoRecorrencias previsao = previsaoFamilia.calcular(List.of(r));
        Set<Competencia> confirmadas = lancamentos.listarOcorrenciasConfirmadas(r.inicio(), competencia).stream()
                .filter(l -> r.id().equals(l.recorrenciaId()))
                .map(Lancamento::competenciaRecorrencia)
                .collect(Collectors.toSet());
        return detalhar(recorrencias.salvar(r.comValorDesde(competencia, valor, c -> previsao.valorEm(r, c),
                confirmadas)));
    }

    @Override
    public RecorrenciaDetalhada definirEstimativa(Long id, Estimativa estimativa, Competencia desde) {
        Recorrencia r = exigir(id);
        acesso.exigirPodeAlterar(r.membroId());
        Categoria categoria = planoContas.buscarCategoria(r.categoriaId()).orElseThrow();
        if (planoContas.buscarGrupo(categoria.grupoId()).orElseThrow().tipo() != TipoMovimento.DESPESA) {
            throw new RegraNegocioException("Receita não é estimada: vale o valor cadastrado");
        }
        if (r.confirmaSozinha()) {
            throw new RegraNegocioException("Conta que confirma sozinha tem valor fixo: não é estimada");
        }
        Competencia mesAtual = Competencia.de(relogio.hoje());
        if (desde != null && desde.antesDe(mesAtual)) {
            throw new RegraNegocioException("A regra muda a partir do mês atual ou de um mês à frente");
        }
        return detalhar(recorrencias.salvar(r.comEstimativa(estimativa, desde, mesAtual)));
    }

    private boolean antesDoInicio(Long id, Competencia competencia) {
        return competencia != null && recorrencias.buscar(id).map(r -> competencia.antesDe(r.inicio())).orElse(false);
    }

    /**
     * Mês de antes do início da conta (já passado ou o atual), lançado como histórico dela: vira um lançamento ligado à
     * recorrência, conta naquele mês e entra na base da previsão, mas não muda a agenda. Conta avulsa não tem histórico.
     */
    private Recorrencia exigirHistorico(Long id, Competencia competencia) {
        Recorrencia r = exigir(id);
        acesso.exigirPodeAlterar(r.membroId());
        if (r.frequencia() == Frequencia.UNICA) {
            throw new RegraNegocioException("Conta avulsa só tem o mês dela: lance " + competencia + " como despesa");
        }
        if (competencia.depoisDe(Competencia.de(relogio.hoje()))) {
            throw new RegraNegocioException("Antes do início, só dá para lançar meses que já chegaram");
        }
        if (lancamentos.existeOcorrencia(id, competencia)) {
            throw new ConflitoException(r.descricao() + " de " + competencia + " já foi lançada");
        }
        return r;
    }

    /** Recorrência com ocorrência agendada na competência e ainda não confirmada, que o usuário pode alterar. */
    private Recorrencia exigirOcorrencia(Long id, Competencia competencia) {
        obrigatorio(competencia, "competência");
        Recorrencia r = exigir(id);
        acesso.exigirPodeAlterar(r.membroId());
        if (!r.agendadaEm(competencia)) {
            throw new RegraNegocioException("A recorrência não tem ocorrência em " + competencia);
        }
        exigirNoHorizonte(competencia);
        if (lancamentos.existeOcorrencia(id, competencia)) {
            throw new ConflitoException("A ocorrência de " + competencia + " já foi confirmada");
        }
        return r;
    }

    /** Ocorrência depois do último ano com previsão não aparece em lugar nenhum, então não se mexe nela. */
    private void exigirNoHorizonte(Competencia competencia) {
        int ultimo = VisaoAnual.ultimoAnoPrevisto(relogio.hoje());
        if (competencia.ano() > ultimo) {
            throw new RegraNegocioException("A previsão vai até " + ultimo + ": " + competencia + " ainda não aparece");
        }
    }

    private Membro validar(DadosRecorrencia dados) {
        Categoria categoria = planoContas.buscarCategoria(obrigatorio(dados.categoriaId(), "categoria"))
                .orElseThrow(() -> new NaoEncontradoException("Categoria não encontrada: " + dados.categoriaId()));
        Grupo grupo = planoContas.buscarGrupo(categoria.grupoId()).orElseThrow();
        categoria.exigirLancavel(grupo);
        if (dados.confirmarSozinha() && grupo.tipo() != TipoMovimento.DESPESA) {
            throw new RegraNegocioException("Só despesa confirma sozinha: receita que se repete precisa ser conferida");
        }
        if (dados.formaPagamento() != null && grupo.tipo() != TipoMovimento.DESPESA) {
            throw new RegraNegocioException("Forma de pagamento é só para despesa: receita não tem conta a pagar");
        }
        Membro membro = acesso.responsavel(dados.membroId());
        acesso.exigirPodeAlterar(membro.id());
        return membro;
    }

    private Recorrencia exigir(Long id) {
        return recorrencias.buscar(id).orElseThrow(() -> new NaoEncontradoException("Recorrência não encontrada: " + id));
    }

    private Detalhador detalhador() {
        return new Detalhador(planoContas.listarCategorias(), planoContas.listarGrupos(), acesso.membrosPorId(),
                recorrencias.listar());
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
                r.valorAnualizado(), previsao.de(r), previsao.trechos(r));
    }
}
