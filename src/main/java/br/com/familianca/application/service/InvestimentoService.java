package br.com.familianca.application.service;

import br.com.familianca.application.port.in.GerenciarInvestimentos;
import br.com.familianca.application.port.out.InvestimentoRepository;
import br.com.familianca.domain.exception.ConflitoException;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.model.CarteiraAnual;
import br.com.familianca.domain.model.Investimento;
import br.com.familianca.domain.model.MovimentoInvestimento;
import br.com.familianca.domain.model.Recurso;
import br.com.familianca.domain.model.TipoInvestimento;
import br.com.familianca.domain.service.CalculadoraInvestimentos;
import br.com.familianca.domain.vo.Competencia;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional
@PreAuthorize("hasRole('DONO')")
public class InvestimentoService implements GerenciarInvestimentos {

    private static final CalculadoraInvestimentos CALCULADORA = new CalculadoraInvestimentos();

    private final InvestimentoRepository investimentos;
    private final AcessoFamilia acesso;

    public InvestimentoService(InvestimentoRepository investimentos, AcessoFamilia acesso) {
        this.investimentos = investimentos;
        this.acesso = acesso;
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

    private Investimento exigir(Long id) {
        return investimentos.buscar(id).orElseThrow(() -> new NaoEncontradoException("Investimento", id));
    }

    private void exigirNomeLivre(String nome, Long ignorarId) {
        if (investimentos.existeNome(nome, ignorarId)) {
            throw new ConflitoException("Já existe o investimento " + nome);
        }
    }
}
