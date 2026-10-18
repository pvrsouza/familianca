package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;

import static br.com.familianca.domain.Validacoes.obrigatorio;

/**
 * Grupo de categorias (ex.: Habitação, Saúde, Receitas fixas). O tipo não muda depois de criado (as categorias e
 * os lançamentos dependem dele). empresa: grupo de despesas da PJ, mostrado com o peso sobre as receitas tributáveis.
 */
public record Grupo(Long id, TipoMovimento tipo, Natureza natureza, String nome, boolean empresa, int ordem,
                    boolean ativo) {

    public static final int NOME_MAXIMO = 80;

    public Grupo {
        obrigatorio(tipo, "tipo");
        obrigatorio(natureza, "natureza");
        nome = obrigatorio(nome, "nome");
        if (nome.length() > NOME_MAXIMO) {
            throw new RegraNegocioException("O nome do grupo pode ter no máximo " + NOME_MAXIMO + " caracteres");
        }
        if (empresa && tipo != TipoMovimento.DESPESA) {
            throw new RegraNegocioException("Só grupos de despesa podem ser da empresa (PJ)");
        }
    }
}
