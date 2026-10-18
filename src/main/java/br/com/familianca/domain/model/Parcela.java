package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;

import java.util.UUID;

import static br.com.familianca.domain.Validacoes.obrigatorio;

/** Posição do lançamento numa compra parcelada: parcela numero de total, todas com o mesmo parcelamentoId. */
public record Parcela(UUID parcelamentoId, int numero, int total) {

    public Parcela {
        obrigatorio(parcelamentoId, "parcelamento");
        if (total < 2 || numero < 1 || numero > total) {
            throw new RegraNegocioException("Parcela inválida: " + numero + "/" + total);
        }
    }
}
