package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;

import java.time.LocalDate;

import static br.com.familianca.domain.Validacoes.obrigatorio;
import static br.com.familianca.domain.Validacoes.opcional;

/**
 * Receita ou despesa que aconteceu (ou vai acontecer, como a parcela futura de uma compra). Conta no mês da data.
 * O tipo (receita/despesa) vem do grupo da categoria.
 * - membroId: de quem é o gasto ou a receita; é o que aparece na visão por pessoa.
 * - recorrenciaId + competenciaRecorrencia: ocorrência confirmada de uma recorrência.
 * - parcela: posição numa compra parcelada.
 * - notaFiscal: competência e emissão da nota (só receita tributável; nulo = não informada). A data é a do pagamento.
 */
public record Lancamento(Long id, Long categoriaId, Long membroId, String descricao, Dinheiro valor, LocalDate data,
                         Long recorrenciaId, Competencia competenciaRecorrencia, Parcela parcela, String observacao,
                         NotaFiscal notaFiscal) {

    public static final int DESCRICAO_MAXIMO = 200;

    public Lancamento {
        obrigatorio(categoriaId, "categoria");
        obrigatorio(membroId, "membro");
        descricao = obrigatorio(descricao, "descrição");
        if (descricao.length() > DESCRICAO_MAXIMO) {
            throw new RegraNegocioException("A descrição pode ter no máximo " + DESCRICAO_MAXIMO + " caracteres");
        }
        obrigatorio(valor, "valor");
        if (!valor.ehPositivo()) {
            throw new RegraNegocioException("O valor deve ser maior que zero");
        }
        obrigatorio(data, "data");
        if ((recorrenciaId == null) != (competenciaRecorrencia == null)) {
            throw new RegraNegocioException("Ocorrência de recorrência sem competência");
        }
        if (recorrenciaId != null && parcela != null) {
            throw new RegraNegocioException("Um lançamento não pode ser parcela e recorrência ao mesmo tempo");
        }
        observacao = opcional(observacao);
    }

    /** Lançamento sem nota fiscal. */
    public Lancamento(Long id, Long categoriaId, Long membroId, String descricao, Dinheiro valor, LocalDate data,
                      Long recorrenciaId, Competencia competenciaRecorrencia, Parcela parcela, String observacao) {
        this(id, categoriaId, membroId, descricao, valor, data, recorrenciaId, competenciaRecorrencia, parcela,
                observacao, null);
    }

    public Competencia competencia() {
        return Competencia.de(data);
    }

    /** Mesmo lançamento com os dados editáveis trocados; a origem (recorrência, parcela) se mantém. */
    public Lancamento editado(Long categoriaId, Long membroId, String descricao, Dinheiro valor, LocalDate data,
                              String observacao, NotaFiscal notaFiscal) {
        return new Lancamento(id, categoriaId, membroId, descricao, valor, data, recorrenciaId,
                competenciaRecorrencia, parcela, observacao, notaFiscal);
    }
}
