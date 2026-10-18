package br.com.familianca.application.port.out;

import br.com.familianca.domain.model.Investimento;
import br.com.familianca.domain.model.MovimentoInvestimento;
import br.com.familianca.domain.vo.Competencia;

import java.util.List;
import java.util.Optional;

/** Investimentos e movimentos da família do usuário logado. */
public interface InvestimentoRepository {

    List<Investimento> listar();

    Optional<Investimento> buscar(Long id);

    Investimento salvar(Investimento investimento);

    void remover(Long id);

    boolean existeNome(String nome, Long ignorarId);

    /** Movimentos de todos os investimentos com competência até dezembro do ano. */
    List<MovimentoInvestimento> listarMovimentosAte(int ano);

    List<MovimentoInvestimento> listarMovimentos(Long investimentoId);

    MovimentoInvestimento salvarMovimento(MovimentoInvestimento movimento);

    void removerMovimento(Long investimentoId, Competencia competencia);
}
