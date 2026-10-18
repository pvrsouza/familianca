package br.com.familianca.application.port.in;

import br.com.familianca.domain.model.CarteiraAnual;
import br.com.familianca.domain.model.Investimento;
import br.com.familianca.domain.model.MovimentoInvestimento;
import br.com.familianca.domain.model.TipoInvestimento;
import br.com.familianca.domain.vo.Competencia;

import java.util.List;

/** Carteira da família (recurso INVESTIMENTOS). Consultar: qualquer membro. Alterar: só DONO. */
public interface GerenciarInvestimentos {

    List<Investimento> listar();

    Investimento criar(String nome, TipoInvestimento tipo);

    Investimento atualizar(Long id, String nome, TipoInvestimento tipo, boolean ativo);

    /** Só sem movimentos; com eles, desative. */
    void remover(Long id);

    List<MovimentoInvestimento> listarMovimentos(Long investimentoId);

    /** Cria ou substitui o movimento da competência. */
    MovimentoInvestimento registrarMovimento(MovimentoInvestimento movimento);

    void removerMovimento(Long investimentoId, Competencia competencia);

    CarteiraAnual carteira(int ano);
}
