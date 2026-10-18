package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;

import static br.com.familianca.domain.Validacoes.obrigatorio;

/**
 * Um lugar onde está o dinheiro da família (ex.: "Itaú corrente", "Dinheiro na carteira", "Cofrinho do João").
 * membroId opcional: nulo = conjunto ou de alguém sem acesso ao Familiança. Inativo não aparece nos meses novos,
 * mas continua nos saldos em que já entrou.
 */
public record LocalSaldo(Long id, String nome, TipoLocalSaldo tipo, Long membroId, boolean ativo) {

    public static final int NOME_MAXIMO = 80;

    public LocalSaldo {
        nome = obrigatorio(nome, "nome");
        if (nome.length() > NOME_MAXIMO) {
            throw new RegraNegocioException("O nome pode ter no máximo " + NOME_MAXIMO + " caracteres");
        }
        obrigatorio(tipo, "tipo");
    }
}
