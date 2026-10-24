package br.com.familianca.application.service;

import br.com.familianca.application.port.in.GerenciarInvestimentos;
import br.com.familianca.application.port.out.InvestimentoRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.domain.exception.ConflitoException;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.CarteiraAnual;
import br.com.familianca.domain.model.CarteiraAnual.Posicao;
import br.com.familianca.domain.model.Investimento;
import br.com.familianca.domain.model.MovimentoInvestimento;
import br.com.familianca.domain.model.Recurso;
import br.com.familianca.domain.model.TipoInvestimento;
import br.com.familianca.domain.service.CalculadoraInvestimentos;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@PreAuthorize("hasRole('DONO')")
public class InvestimentoService implements GerenciarInvestimentos {

    private static final CalculadoraInvestimentos CALCULADORA = new CalculadoraInvestimentos();

    private final InvestimentoRepository investimentos;
    private final AcessoFamilia acesso;
    private final Relogio relogio;

    public InvestimentoService(InvestimentoRepository investimentos, AcessoFamilia acesso, Relogio relogio) {
        this.investimentos = investimentos;
        this.acesso = acesso;
        this.relogio = relogio;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<Investimento> listar() {
        acesso.exigir(Recurso.INVESTIMENTOS);
        return investimentos.listar().stream()
                .sorted(Comparator.comparing(Investimento::nome, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Override
    public Investimento criar(String nome, TipoInvestimento tipo) {
        acesso.exigir(Recurso.INVESTIMENTOS);
        Investimento novo = new Investimento(null, nome, tipo, true);
        exigirNomeLivre(novo.nome(), null);
        return investimentos.salvar(novo);
    }

    @Override
    public Investimento atualizar(Long id, String nome, TipoInvestimento tipo, boolean ativo) {
        acesso.exigir(Recurso.INVESTIMENTOS);
        exigir(id);
        Investimento editado = new Investimento(id, nome, tipo, ativo);
        exigirNomeLivre(editado.nome(), id);
        return investimentos.salvar(editado);
    }

    @Override
    public void remover(Long id) {
        acesso.exigir(Recurso.INVESTIMENTOS);
        exigir(id);
        if (!investimentos.listarMovimentos(id).isEmpty()) {
            throw new ConflitoException("O investimento tem movimentos: desative em vez de excluir");
        }
        investimentos.remover(id);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<MovimentoInvestimento> listarMovimentos(Long investimentoId) {
        acesso.exigir(Recurso.INVESTIMENTOS);
        exigir(investimentoId);
        return investimentos.listarMovimentos(investimentoId).stream()
                .sorted(Comparator.comparing(MovimentoInvestimento::competencia)).toList();
    }

    @Override
    public MovimentoInvestimento registrarMovimento(MovimentoInvestimento movimento) {
        acesso.exigir(Recurso.INVESTIMENTOS);
        exigir(movimento.investimentoId());
        return investimentos.salvarMovimento(movimento);
    }

    @Override
    public void removerMovimento(Long investimentoId, Competencia competencia) {
        acesso.exigir(Recurso.INVESTIMENTOS);
        exigir(investimentoId);
        investimentos.removerMovimento(investimentoId, competencia);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public CarteiraAnual carteira(int ano) {
        acesso.exigir(Recurso.INVESTIMENTOS);
        return CALCULADORA.calcular(ano, investimentos.listar(), investimentos.listarMovimentosAte(ano));
    }

    @Override
    public Investimento encerrar(Long id, Competencia mes) {
        acesso.exigir(Recurso.INVESTIMENTOS);
        Investimento inv = exigir(id);
        if (!inv.ativo()) {
            throw new ConflitoException(inv.nome() + " já está encerrado");
        }
        if (mes.depoisDe(Competencia.de(relogio.hoje()))) {
            throw new RegraNegocioException("O resgate é de um mês que já chegou: " + mes + " ainda não");
        }
        List<MovimentoInvestimento> movimentos = investimentos.listarMovimentos(id);
        if (!movimentos.isEmpty()) {
            Competencia primeiro = movimentos.stream().map(MovimentoInvestimento::competencia)
                    .min(Competencia::compareTo).orElseThrow();
            if (mes.antesDe(primeiro)) {
                throw new RegraNegocioException(inv.nome() + " começou em " + primeiro + ": o resgate não pode ser antes");
            }
            if (movimentos.stream().anyMatch(m -> m.competencia().depoisDe(mes))) {
                throw new RegraNegocioException(inv.nome() + " tem saldo informado depois de " + mes
                        + ": encerrem no último mês com saldo");
            }
            Posicao p = CALCULADORA.calcular(mes.ano(), List.of(inv), movimentos).investimentos().get(0);
            Dinheiro anterior = mes.numeroMes() == 1 ? p.saldoInicial() : p.saldos().mes(mes.numeroMes() - 1);
            // Saldo zero e o resgate de tudo o que havia: o rendimento do mês fica zero.
            investimentos.salvarMovimento(new MovimentoInvestimento(id, mes, Dinheiro.ZERO.subtrair(anterior), null,
                    Dinheiro.ZERO));
        }
        return investimentos.salvar(new Investimento(id, inv.nome(), inv.tipo(), false));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public SaldosDoMes saldosDoMes(Competencia mes) {
        acesso.exigir(Recurso.INVESTIMENTOS);
        Competencia referencia = Competencia.de(relogio.hoje()).mais(-1);
        Competencia c = mes != null ? mes : referencia;
        List<MovimentoInvestimento> movimentos = investimentos.listarMovimentosAte(c.ano());
        Map<Long, Posicao> posicoes = CALCULADORA.calcular(c.ano(), investimentos.listar(), movimentos).investimentos()
                .stream().collect(Collectors.toMap(p -> p.investimento().id(), Function.identity()));
        Map<Long, List<MovimentoInvestimento>> porInvestimento = movimentos.stream()
                .collect(Collectors.groupingBy(MovimentoInvestimento::investimentoId));
        List<LinhaSaldo> linhas = listar().stream()
                .filter(Investimento::ativo)
                // Começou depois do mês: ainda não existia, não tem saldo a informar.
                .filter(i -> porInvestimento.getOrDefault(i.id(), List.of()).stream()
                        .map(MovimentoInvestimento::competencia).min(Competencia::compareTo)
                        .map(primeiro -> !primeiro.depoisDe(c)).orElse(true))
                .map(i -> {
                    Posicao p = posicoes.get(i.id());
                    Dinheiro anterior = p == null ? Dinheiro.ZERO
                            : c.numeroMes() == 1 ? p.saldoInicial() : p.saldos().mes(c.numeroMes() - 1);
                    MovimentoInvestimento m = porInvestimento.getOrDefault(i.id(), List.of()).stream()
                            .filter(x -> x.competencia().equals(c)).findFirst().orElse(null);
                    return new LinhaSaldo(i, anterior, m != null ? m.aporte() : null,
                            m != null ? m.saldoInformado() : null, m != null && m.saldoInformado() != null);
                })
                .toList();
        return new SaldosDoMes(c, referencia, linhas);
    }

    @Override
    public SaldosDoMes atualizarSaldos(Competencia mes, List<SaldoInformado> saldos) {
        acesso.exigir(Recurso.INVESTIMENTOS);
        if (mes.depoisDe(Competencia.de(relogio.hoje()))) {
            throw new RegraNegocioException("O saldo do extrato é de um mês que já chegou: " + mes + " ainda não");
        }
        Set<Long> vistos = new HashSet<>();
        for (SaldoInformado s : saldos) {
            exigir(s.investimentoId());
            if (!vistos.add(s.investimentoId())) {
                throw new RegraNegocioException("O mesmo investimento veio duas vezes");
            }
            if (s.saldo() == null) {
                throw new RegraNegocioException("Informem o saldo do extrato de cada investimento enviado");
            }
            // O % deixou de ser informado: com o saldo do extrato, o rendimento é sempre o calculado.
            investimentos.salvarMovimento(new MovimentoInvestimento(s.investimentoId(), mes, s.aporte(), null,
                    s.saldo()));
        }
        return saldosDoMes(mes);
    }

    private Investimento exigir(Long id) {
        return investimentos.buscar(id).orElseThrow(() -> new NaoEncontradoException("Investimento", id));
    }

    private void exigirNomeLivre(String nome, Long ignorarId) {
        if (investimentos.existeNome(nome, ignorarId)) {
            throw new ConflitoException("Já existe o investimento " + nome);
        }
    }
}
