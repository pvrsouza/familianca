package br.com.familianca.application.port.out;

/** Apaga de uma vez o orçamento da família do usuário logado (pessoas, convites e assinatura ficam). */
public interface DadosFamiliaRepository {

    Apagados apagarTudo();

    /** Quantos registros saíram de cada parte, para a auditoria. */
    record Apagados(int lancamentos, int recorrencias, int saldos, int investimentos, int grupos, int categorias) {
    }
}
