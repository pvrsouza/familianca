package br.com.familianca.application.port.in;

import br.com.familianca.application.port.in.GerenciarLancamentos.LancamentoDetalhado;
import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.FormaPagamento;
import br.com.familianca.domain.model.Frequencia;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.NotaFiscal;
import br.com.familianca.domain.model.Recorrencia;
import br.com.familianca.domain.service.PrevisaoRecorrencias.Previsao;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;

import java.time.LocalDate;
import java.util.List;

/** Receitas e despesas que se repetem. Mesmas regras de permissão dos lançamentos. */
public interface GerenciarRecorrencias {

    List<RecorrenciaDetalhada> listar();

    RecorrenciaDetalhada buscar(Long id);

    RecorrenciaDetalhada criar(DadosRecorrencia dados);

    /** Vale para todas as ocorrências ainda não confirmadas; as puladas continuam puladas. */
    RecorrenciaDetalhada atualizar(Long id, DadosRecorrencia dados);

    /** Só sem ocorrências confirmadas; com elas, defina o fim. */
    void remover(Long id);

    /** Ocorrências dos últimos 12 meses com data até hoje, nem confirmadas nem puladas. Mais antigas primeiro. */
    List<OcorrenciaPendente> pendentes();

    /**
     * Vira lançamento. valor e data nulos = os da recorrência. Receita tributável (nota fiscal) exige a data do
     * pagamento a cada mês e pode trazer a competência e a emissão da nota.
     */
    LancamentoDetalhado confirmar(Long id, Competencia competencia, Dinheiro valor, LocalDate data,
                                  NotaFiscal notaFiscal);

    default LancamentoDetalhado confirmar(Long id, Competencia competencia, Dinheiro valor, LocalDate data) {
        return confirmar(id, competencia, valor, data, null);
    }

    RecorrenciaDetalhada pular(Long id, Competencia competencia);

    RecorrenciaDetalhada desfazerPulo(Long id, Competencia competencia);

    /**
     * Vencimento diferente só nesta ocorrência (o boleto do mês veio para outro dia), até 2 meses antes ou depois do
     * mês. Nulo volta ao dia cadastrado. Só conta com vencimento.
     */
    RecorrenciaDetalhada ajustarVencimento(Long id, Competencia competencia, LocalDate vencimento);

    /**
     * membroId nulo = o membro logado; fim nulo = sem prazo. confirmarSozinha: as ocorrências que vencem a partir de
     * hoje viram lançamento sozinhas (só despesa; receita precisa ser conferida). semVencimento e formaPagamento:
     * só despesa (ver {@link Recorrencia}).
     */
    record DadosRecorrencia(Long categoriaId, Long membroId, String descricao, Dinheiro valor, int dia,
                            Frequencia frequencia, Competencia inicio, Competencia fim, String observacao,
                            boolean confirmarSozinha, boolean semVencimento, FormaPagamento formaPagamento) {

        public DadosRecorrencia(Long categoriaId, Long membroId, String descricao, Dinheiro valor, int dia,
                                Frequencia frequencia, Competencia inicio, Competencia fim, String observacao,
                                boolean confirmarSozinha) {
            this(categoriaId, membroId, descricao, valor, dia, frequencia, inicio, fim, observacao, confirmarSozinha,
                    false, null);
        }
    }

    /**
     * @param valorNoAno      valor × ocorrências no ano atual (sem as puladas)
     * @param valorAnualizado quanto pesa num ano cheio (valor × vezes por ano)
     */
    /**
     * @param valorNoAno valor previsto de cada ocorrência do ano (confirmadas pelo valor previsto), sem as puladas
     * @param previsao   estimativa do valor das próximas ocorrências pela regra; nula = vale o valor cadastrado
     */
    record RecorrenciaDetalhada(Recorrencia recorrencia, Categoria categoria, Grupo grupo, Membro membro, int ano,
                                Dinheiro valorNoAno, Dinheiro valorAnualizado, Previsao previsao) {
    }

    /** @param vencimento nulo = conta sem vencimento (ou receita) */
    record OcorrenciaPendente(RecorrenciaDetalhada recorrencia, Competencia competencia, LocalDate data,
                              Dinheiro valorPrevisto, LocalDate vencimento) {
    }
}
