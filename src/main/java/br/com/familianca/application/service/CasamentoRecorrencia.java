package br.com.familianca.application.service;

import br.com.familianca.application.port.in.GerenciarRecorrencias;
import br.com.familianca.application.port.in.GerenciarRecorrencias.OcorrenciaPendente;
import br.com.familianca.domain.exception.AcessoNegadoException;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Casa linhas de um extrato com ocorrências de recorrência ainda pendentes (para oferecer "confirmar a conta" em vez
 * de criar um lançamento novo e duplicar a despesa). Conservador: na dúvida, não casa.
 * <p>
 * Regras: mesma direção; valor dentro de ±5% do previsto (±20% se a conta é estimada, porque varia todo mês); data de
 * 7 dias antes a 15 dias depois do vencimento (sem vencimento: dentro do mês da competência). Cada ocorrência casa com
 * uma linha só, a mais próxima primeiro (menor diferença de valor, depois de data). Linha com duas ocorrências
 * igualmente próximas fica sem casar. Só entram as ocorrências que {@link GerenciarRecorrencias#pendentes()} traz
 * (vencidas até hoje, dos últimos 12 meses) e que quem importa pode confirmar.
 */
@Component
public class CasamentoRecorrencia {

    private static final BigDecimal TOLERANCIA_FIXA = new BigDecimal("0.05");
    private static final BigDecimal TOLERANCIA_ESTIMADA = new BigDecimal("0.20");
    private static final int DIAS_ANTES = 7;
    private static final int DIAS_DEPOIS = 15;

    private final GerenciarRecorrencias recorrencias;
    private final AcessoFamilia acesso;

    public CasamentoRecorrencia(GerenciarRecorrencias recorrencias, AcessoFamilia acesso) {
        this.recorrencias = recorrencias;
        this.acesso = acesso;
    }

    public record LinhaCasavel(int indice, LocalDate data, Dinheiro valor, TipoMovimento tipo, String descricao) {
    }

    public record OcorrenciaCasada(Long recorrenciaId, Competencia competencia, String descricao, Dinheiro valor,
                                   LocalDate vencimento) {
    }

    private record Par(LinhaCasavel linha, OcorrenciaPendente ocorrencia, BigDecimal difValor, long difDias) {
    }

    private static final Comparator<Par> MAIS_PROXIMO = Comparator.comparing(Par::difValor).thenComparing(Par::difDias);

    /** Índice da linha -> ocorrência casada. Linhas sem casamento não aparecem. */
    public Map<Integer, OcorrenciaCasada> casar(List<LinhaCasavel> linhas) {
        List<OcorrenciaPendente> pendentes = recorrencias.pendentes().stream().filter(this::podeConfirmar).toList();
        List<Par> pares = new ArrayList<>();
        for (LinhaCasavel linha : linhas) {
            List<Par> daLinha = pendentes.stream().map(p -> par(linha, p)).filter(p -> p != null)
                    .sorted(MAIS_PROXIMO).toList();
            boolean ambigua = daLinha.size() > 1 && MAIS_PROXIMO.compare(daLinha.get(0), daLinha.get(1)) == 0;
            if (!ambigua) {
                pares.addAll(daLinha);
            }
        }
        pares.sort(MAIS_PROXIMO.thenComparing(p -> p.linha().indice()));
        Map<Integer, OcorrenciaCasada> casadas = new LinkedHashMap<>();
        Set<String> usadas = new HashSet<>();
        for (Par p : pares) {
            OcorrenciaPendente o = p.ocorrencia();
            Long id = o.recorrencia().recorrencia().id();
            if (casadas.containsKey(p.linha().indice()) || !usadas.add(id + "/" + o.competencia())) {
                continue;
            }
            casadas.put(p.linha().indice(), new OcorrenciaCasada(id, o.competencia(),
                    o.recorrencia().recorrencia().descricao(), o.valorPrevisto(), o.vencimento()));
        }
        return casadas;
    }

    /** Confirma a ocorrência com o valor e a data do extrato (regra "pagar = confirmar", com as mesmas permissões). */
    public Lancamento confirmar(Long recorrenciaId, Competencia competencia, LocalDate data, Dinheiro valor) {
        return recorrencias.confirmar(recorrenciaId, competencia, valor, data).lancamento();
    }

    private Par par(LinhaCasavel linha, OcorrenciaPendente o) {
        if (linha.tipo() != o.recorrencia().grupo().tipo() || linha.data() == null || linha.valor() == null) {
            return null;
        }
        BigDecimal previsto = o.valorPrevisto().valor();
        BigDecimal difValor = linha.valor().valor().subtract(previsto).abs();
        BigDecimal percentual = o.recorrencia().recorrencia().estimativa() != null
                ? TOLERANCIA_ESTIMADA : TOLERANCIA_FIXA;
        if (difValor.compareTo(previsto.multiply(percentual)) > 0) {
            return null;
        }
        LocalDate referencia = o.vencimento() != null ? o.vencimento() : o.data();
        boolean naJanela = o.vencimento() != null
                ? !linha.data().isBefore(referencia.minusDays(DIAS_ANTES))
                        && !linha.data().isAfter(referencia.plusDays(DIAS_DEPOIS))
                : o.competencia().equals(Competencia.de(linha.data()));
        return naJanela ? new Par(linha, o, difValor, Math.abs(ChronoUnit.DAYS.between(referencia, linha.data())))
                : null;
    }

    /** MEMBRO só confirma as próprias: oferecer a dos outros faria o lote inteiro falhar com 403. */
    private boolean podeConfirmar(OcorrenciaPendente o) {
        try {
            acesso.exigirPodeAlterar(o.recorrencia().recorrencia().membroId());
            return true;
        } catch (AcessoNegadoException e) {
            return false;
        }
    }
}
