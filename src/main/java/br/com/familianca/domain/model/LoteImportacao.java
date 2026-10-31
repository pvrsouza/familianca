package br.com.familianca.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Uma importação de extrato: de qual lugar do saldo, o período do extrato, quantas linhas entraram, foram ignoradas ou
 * confirmaram uma recorrência, e quem fez. Sem o nome nem o conteúdo do arquivo. criadoEm é nulo logo após criar.
 */
public record LoteImportacao(Long id, Long localSaldoId, LocalDate periodoDe, LocalDate periodoAte, int importadas,
                             int ignoradas, int confirmadas, Long membroId, OffsetDateTime criadoEm) {
}
