package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static br.com.familianca.domain.Validacoes.obrigatorio;
import static br.com.familianca.domain.Validacoes.opcional;

/**
 * Receita ou despesa que se repete. Não vira lançamento sozinha: cada ocorrência (competência) aparece como prevista
 * na visão anual até ser confirmada (vira lançamento, com o valor real) ou pulada.
 * - inicio/fim: primeira e última competência possíveis; fim nulo = sem prazo.
 * - dia: dia do mês da ocorrência (31 em fevereiro vira 28/29). Na despesa é o vencimento ("vence no dia"); na
 *   receita, o dia em que costuma cair.
 * - frequencia UNICA: uma vez só, no mês de início (conta avulsa); o fim vira o próprio início.
 * - puladas: competências em que não houve a ocorrência.
 * - confirmarSozinhaDesde: data em que a família ligou a confirmação automática (débito automático, conta de valor
 *   fixo); as ocorrências que vencem a partir dela viram lançamento sozinhas, com o valor previsto. Nulo = desligada.
 * - valorDesde: competência em que o valor cadastrado mudou pela última vez. A estimativa do previsto só considera as
 *   confirmações a partir dela (um reajuste não é desfeito pela média dos meses de antes). Nulo = desde sempre.
 * - semVencimento: a conta não tem data certa para pagar (ex.: mesada); o dia só posiciona a ocorrência no mês.
 * - formaPagamento: como é paga (ver {@link FormaPagamento}); cobrada no cartão não tem vencimento próprio.
 * - vencimentosAjustados: vencimento diferente só numa ocorrência (o boleto deste mês veio para outro dia). Vale
 *   como a data da ocorrência: é a data sugerida ao confirmar e a que decide se já venceu.
 */
public record Recorrencia(Long id, Long categoriaId, Long membroId, String descricao, Dinheiro valor, int dia,
                          Frequencia frequencia, Competencia inicio, Competencia fim, String observacao,
                          Set<Competencia> puladas, LocalDate confirmarSozinhaDesde, Competencia valorDesde,
                          boolean semVencimento, FormaPagamento formaPagamento,
                          Map<Competencia, LocalDate> vencimentosAjustados) {

    /** Um vencimento ajustado pode cair até este número de meses antes ou depois do mês da ocorrência. */
    public static final int AJUSTE_MAXIMO_MESES = 2;

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
        if (frequencia == Frequencia.UNICA) {
            fim = inicio;
        }
        if (fim != null && fim.antesDe(inicio)) {
            throw new RegraNegocioException("O fim não pode ser antes do início");
        }
        observacao = opcional(observacao);
        puladas = puladas == null ? Set.of() : Set.copyOf(puladas);
        vencimentosAjustados = vencimentosAjustados == null ? Map.of() : Map.copyOf(vencimentosAjustados);
        vencimentosAjustados.forEach(Recorrencia::validarAjuste);
    }

    public Recorrencia(Long id, Long categoriaId, Long membroId, String descricao, Dinheiro valor, int dia,
                       Frequencia frequencia, Competencia inicio, Competencia fim, String observacao,
                       Set<Competencia> puladas, LocalDate confirmarSozinhaDesde, Competencia valorDesde) {
        this(id, categoriaId, membroId, descricao, valor, dia, frequencia, inicio, fim, observacao, puladas,
                confirmarSozinhaDesde, valorDesde, false, null, Map.of());
    }

    public Recorrencia(Long id, Long categoriaId, Long membroId, String descricao, Dinheiro valor, int dia,
                       Frequencia frequencia, Competencia inicio, Competencia fim, String observacao,
                       Set<Competencia> puladas, LocalDate confirmarSozinhaDesde) {
        this(id, categoriaId, membroId, descricao, valor, dia, frequencia, inicio, fim, observacao, puladas,
                confirmarSozinhaDesde, null);
    }

    private static void validarAjuste(Competencia competencia, LocalDate vencimento) {
        LocalDate de = competencia.mais(-AJUSTE_MAXIMO_MESES).primeiroDia();
        LocalDate ate = competencia.mais(AJUSTE_MAXIMO_MESES).ultimoDia();
        if (vencimento.isBefore(de) || vencimento.isAfter(ate)) {
            throw new RegraNegocioException("O vencimento de " + competencia + " precisa ficar entre " + de + " e "
                    + ate + " (até " + AJUSTE_MAXIMO_MESES + " meses antes ou depois do mês da conta)");
        }
    }

    /** Se a agenda da recorrência tem ocorrência na competência (pulada ou não). */
    public boolean agendadaEm(Competencia competencia) {
        return !competencia.antesDe(inicio)
                && (fim == null || !competencia.depoisDe(fim))
                && (frequencia == Frequencia.UNICA ? competencia.equals(inicio)
                    : inicio.mesesAte(competencia) % frequencia.meses() == 0);
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

    /** Data da ocorrência: o vencimento ajustado daquele mês ou o dia cadastrado. */
    public LocalDate dataEm(Competencia competencia) {
        LocalDate ajustado = vencimentosAjustados.get(competencia);
        return ajustado != null ? ajustado : competencia.diaLimitado(dia);
    }

    /** Tem data certa para pagar: não marcada "sem vencimento" e não cobrada no cartão (aí vale a fatura). */
    public boolean temVencimento() {
        return !semVencimento && formaPagamento != FormaPagamento.CARTAO_CREDITO;
    }

    /** Vencimento da ocorrência; nulo quando a conta não tem vencimento. */
    public LocalDate vencimentoEm(Competencia competencia) {
        return temVencimento() ? dataEm(competencia) : null;
    }

    /** Vencimento só desta ocorrência; nulo volta ao dia cadastrado. */
    public Recorrencia comVencimentoAjustado(Competencia competencia, LocalDate vencimento) {
        Map<Competencia, LocalDate> novos = new HashMap<>(vencimentosAjustados);
        if (vencimento == null || vencimento.equals(competencia.diaLimitado(dia))) {
            novos.remove(competencia);
        } else {
            novos.put(competencia, vencimento);
        }
        return new Recorrencia(id, categoriaId, membroId, descricao, valor, dia, frequencia, inicio, fim, observacao,
                puladas, confirmarSozinhaDesde, valorDesde, semVencimento, formaPagamento, novos);
    }

    /** Quanto a recorrência pesa num ano cheio (valor × vezes por ano), independentemente de início e fim. */
    public Dinheiro valorAnualizado() {
        return valor.multiplicar(frequencia.vezesPorAno());
    }

    public Recorrencia comPuladas(Set<Competencia> novas) {
        return new Recorrencia(id, categoriaId, membroId, descricao, valor, dia, frequencia, inicio, fim, observacao,
                novas, confirmarSozinhaDesde, valorDesde, semVencimento, formaPagamento, vencimentosAjustados);
    }
}
