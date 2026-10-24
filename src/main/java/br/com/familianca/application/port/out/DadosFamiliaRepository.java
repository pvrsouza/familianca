package br.com.familianca.application.port.out;

import java.util.Optional;

/** Orçamento da família do usuário logado como um todo: apagar de uma vez e até onde ele vai no passado. */
public interface DadosFamiliaRepository {

    /** Apaga o orçamento (pessoas, convites e assinatura ficam). */
    Apagados apagarTudo();

    /** Ano mais antigo com algum dado: lançamento, início de recorrência, saldo ou movimento de investimento. */
    Optional<Integer> primeiroAno();

    /** Quantos registros saíram de cada parte, para a auditoria. */
    record Apagados(int lancamentos, int recorrencias, int saldos, int locaisSaldo, int investimentos, int grupos,
                    int categorias) {
    }
}
