package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static br.com.familianca.domain.Validacoes.obrigatorio;
import static br.com.familianca.domain.Validacoes.opcional;

/**
 * Receita ou despesa que se repete. Não vira lançamento sozinha: cada ocorrência (competência) aparece como prevista
 * na visão anual até ser confirmada (vira lançamento, com o valor real) ou pulada.
 * - inicio/fim: primeira e última competência possíveis; fim nulo = sem prazo.
 * - dia: dia do mês da ocorrência (31 em fevereiro vira 28/29).
 * - puladas: competências em que não houve a ocorrência.
 * - confirmarSozinhaDesde: data em que a família ligou a confirmação automática (débito automático, conta de valor
 *   fixo); as ocorrências que vencem a partir dela viram lançamento sozinhas, com o valor previsto. Nulo = desligada.
 */
public record Recorrencia(Long id, Long categoriaId, Long membroId, String descricao, Dinheiro valor, int dia,
                          Frequencia frequencia, Competencia inicio, Competencia fim, String observacao,
                          Set<Competencia> puladas, LocalDate confirmarSozinhaDesde) {

    public Recorrencia {
        obrigatorio(categoriaId, "categoria");
        obrigatorio(membroId, "membro");
        descricao = obrigatorio(descricao, "descrição");
        if (descricao.length() > Lancamento.DESCRICAO_MAXIMO) {
            throw new RegraNegocioException("A descrição pode ter no máximo " + Lancamento.DESCRICAO_MAXIMO
                    + " caracteres");
        }
        obrigatorio(valor, "valor");
        if (!valor.ehPositivo()) {
            throw new RegraNegocioException("O valor deve ser maior que zero");
        }
        if (dia < 1 || dia > 31) {
            throw new RegraNegocioException("O dia deve estar entre 1 e 31");
        }
        obrigatorio(frequencia, "frequência");
        obrigatorio(inicio, "início");
        if (fim != null && fim.antesDe(inicio)) {
            throw new RegraNegocioException("O fim não pode ser antes do início");
        }
        observacao = opcional(observacao);
        puladas = puladas == null ? Set.of() : Set.copyOf(puladas);
    }

    /** Se a agenda da recorrência tem ocorrência na competência (pulada ou não). */
    public boolean agendadaEm(Competencia competencia) {
        return !competencia.antesDe(inicio)
                && (fim == null || !competencia.depoisDe(fim))
                && inicio.mesesAte(competencia) % frequencia.meses() == 0;
    }

    /** Agendada e não pulada. */
    public boolean ocorreEm(Competencia competencia) {
        return agendadaEm(competencia) && !puladas.contains(competencia);
    }

    /** Competências do ano com ocorrência (sem as puladas), em ordem. */
    public List<Competencia> ocorrenciasNoAno(int ano) {
        List<Competencia> ocorrencias = new ArrayList<>();
        for (int mes = 1; mes <= 12; mes++) {
            Competencia c = Competencia.of(ano, mes);
            if (ocorreEm(c)) {
                ocorrencias.add(c);
            }
        }
        return ocorrencias;
    }

    public boolean confirmaSozinha() {
        return confirmarSozinhaDesde != null;
    }

    /** Ocorre na competência e vence entre o dia em que a confirmação automática foi ligada e hoje. */
    public boolean confirmaSozinhaEm(Competencia competencia, LocalDate hoje) {
        if (!confirmaSozinha() || !ocorreEm(competencia)) {
            return false;
        }
        LocalDate data = dataEm(competencia);
        return !data.isBefore(confirmarSozinhaDesde) && !data.isAfter(hoje);
    }

    public LocalDate dataEm(Competencia competencia) {
        return competencia.diaLimitado(dia);
    }

    /** Quanto a recorrência pesa num ano cheio (valor × vezes por ano), independentemente de início e fim. */
    public Dinheiro valorAnualizado() {
        return valor.multiplicar(frequencia.vezesPorAno());
    }

    public Recorrencia comPuladas(Set<Competencia> novas) {
        return new Recorrencia(id, categoriaId, membroId, descricao, valor, dia, frequencia, inicio, fim, observacao,
                novas, confirmarSozinhaDesde);
    }
}
