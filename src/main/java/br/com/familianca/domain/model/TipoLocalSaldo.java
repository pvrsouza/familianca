package br.com.familianca.domain.model;

/** Onde o dinheiro está. INVESTIMENTO: só o de resgate rápido que não é acompanhado na carteira de investimentos. */
public enum TipoLocalSaldo {
    CONTA_CORRENTE,
    POUPANCA,
    DINHEIRO,
    COFRINHO,
    INVESTIMENTO,
    OUTRO
}
