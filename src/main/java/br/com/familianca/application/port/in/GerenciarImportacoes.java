package br.com.familianca.application.port.in;

import br.com.familianca.domain.model.TipoMovimento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Importação de extrato OFX/OFC para um lugar do saldo. O arquivo é lido em memória e descartado. Todos analisam e
 * importam; MEMBRO só importa em nome próprio e só em lugar sem dono ou dele; desfazer: DONO ou quem importou.
 */
public interface GerenciarImportacoes {

    /** Lê o extrato e diz o que é novo, o que já foi importado e o que pode ser duplicata. Não grava nada. */
    Analise analisar(byte[] conteudo, Long localSaldoId);

    /** Tudo ou nada: grava os lançamentos, as chaves das ignoradas e o lote. */
    Resultado importar(Pedido pedido);

    /** Mais recentes primeiro. */
    List<Lote> listar();

    /** Apaga os lançamentos do lote, menos os editados depois da importação. */
    Desfeito desfazer(Long loteId);

    enum Status { NOVA, JA_IMPORTADA, POSSIVEL_DUPLICATA }

    enum Acao { IMPORTAR, IGNORAR, CONFIRMAR_RECORRENCIA }

    record Analise(String banco, String contaOfx, String avisoConta, LocalDate periodoDe, LocalDate periodoAte,
                   Resumo resumo, List<LinhaAnalise> linhas) {
    }

    record Resumo(int total, int novas, int jaImportadas, int possiveisDuplicatas) {
    }

    /** valor sempre positivo; o sinal do extrato é o tipo. */
    record LinhaAnalise(int indice, String chave, LocalDate data, String descricao, BigDecimal valor,
                        TipoMovimento tipo, Status status, Acao acaoSugerida, Long categoriaIdSugerida,
                        LancamentoParecido lancamentoParecido, OcorrenciaPendente ocorrenciaPendente) {
    }

    record LancamentoParecido(Long id, LocalDate data, String descricao, BigDecimal valor, String categoriaNome) {
    }

    /** Conta que se repete e ainda não foi confirmada, que esta linha do extrato provavelmente pagou. */
    record OcorrenciaPendente(Long recorrenciaId, String competencia, String descricao, BigDecimal valor,
                              LocalDate vencimento) {
    }

    /** categoriaId nulo = "A classificar"; membroId nulo = quem importa; recorrenciaId e competencia só na confirmação. */
    record LinhaPedido(String chave, Acao acao, LocalDate data, String descricao, BigDecimal valor,
                       TipoMovimento tipo, Long categoriaId, Long membroId, Long recorrenciaId, String competencia) {
    }

    record Pedido(Long localSaldoId, LocalDate periodoDe, LocalDate periodoAte, List<LinhaPedido> linhas) {
    }

    /** jaExistiam: linhas cuja chave já estava gravada na hora de gravar (não são erro). */
    record Resultado(Long loteId, int importadas, int ignoradas, int confirmadas, int jaExistiam) {
    }

    record Lote(Long id, Long localSaldoId, String localSaldoNome, LocalDate periodoDe, LocalDate periodoAte,
                int importadas, int ignoradas, int confirmadas, String feitaPor, OffsetDateTime feitaEm,
                boolean podeDesfazer) {
    }

    record Desfeito(int removidos, int mantidosPorEdicao) {
    }
}
