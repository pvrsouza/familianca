package br.com.familianca.domain.model;

/**
 * Como uma despesa que se repete é paga. Diz se ela pede uma ação de alguém até o vencimento:
 * - BOLETO_PIX: alguém paga até o vencimento (é o que entra na lista de contas a pagar).
 * - DEBITO_AUTOMATICO: sai sozinho da conta no vencimento; basta conferir o valor.
 * - CARTAO_CREDITO: é cobrada no cartão e vai para a fatura; não tem vencimento próprio (o vencimento é o da fatura).
 * Nulo = não informado (tratada como conta com vencimento, se tiver).
 */
public enum FormaPagamento {
    BOLETO_PIX,
    DEBITO_AUTOMATICO,
    CARTAO_CREDITO
}
