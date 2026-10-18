package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;

import static br.com.familianca.domain.Validacoes.obrigatorio;

/** Uma aplicação da carteira da família (ex.: "CDB Banco X", "Tesouro IPCA 2035"). */
public record Investimento(Long id, String nome, TipoInvestimento tipo, boolean ativo) {

    public Investimento {
        nome = obrigatorio(nome, "nome");
        if (nome.length() > 100) {
            throw new RegraNegocioException("O nome pode ter no máximo 100 caracteres");
        }
        obrigatorio(tipo, "tipo");
    }
}
