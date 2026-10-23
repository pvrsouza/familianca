package br.com.familianca.application.port.in;

import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Estimativa;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Natureza;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.vo.Percentual;

import java.util.List;

/** Plano de contas da família. Consultar: qualquer membro. Alterar: só DONO. */
public interface GerenciarCategorias {

    /** Tudo, inclusive desativados. */
    PlanoContas listar();

    Grupo criarGrupo(DadosGrupo dados);

    /** O tipo não muda. Desativar o grupo bloqueia novos lançamentos em todas as categorias dele. */
    Grupo atualizarGrupo(Long id, DadosGrupo dados, boolean ativo);

    /** Só grupo sem categorias. */
    void removerGrupo(Long id);

    Categoria criarCategoria(DadosCategoria dados);

    /** Pode mudar para outro grupo do mesmo tipo. */
    Categoria atualizarCategoria(Long id, DadosCategoria dados, boolean ativa);

    /** Regra própria de estimativa da categoria; nula = segue a da família. */
    Categoria definirEstimativa(Long id, Estimativa estimativa);

    /** Só categoria sem lançamentos nem recorrências; com uso, desative. */
    void removerCategoria(Long id);

    /** Cria o grupo da empresa (impostos por alíquota) e a receita tributável. Recurso DESPESAS_PJ. */
    PlanoContas aplicarModeloPj();

    record PlanoContas(List<Grupo> grupos, List<Categoria> categorias) {
    }

    /** ordem nula = depois dos existentes. */
    record DadosGrupo(TipoMovimento tipo, Natureza natureza, String nome, boolean empresa, Integer ordem) {
    }

    /**
     * recorrente nulo = não (na criação) ou mantém (na edição).
     * estimativaInformada false = segue a família (na criação) ou mantém (na edição); true = vale estimativa
     * (nula = volta a seguir a família).
     */
    record DadosCategoria(Long grupoId, String nome, Integer ordem, boolean tributavel, Percentual aliquota,
                          Boolean recorrente, boolean estimativaInformada, Estimativa estimativa) {
    }
}
