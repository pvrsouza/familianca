package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Percentual;

import java.math.BigDecimal;
import java.util.Objects;

import static br.com.familianca.domain.Validacoes.obrigatorio;

/**
 * Onde cada receita ou despesa é classificada (ex.: Supermercado, Plano de saúde, Salário).
 * - tributavel: receita que forma a base das alíquotas da PJ (ex.: notas fiscais emitidas).
 * - aliquota: despesa calculada = alíquota × receitas tributáveis do mês (ex.: ISS, Simples). Não recebe
 *   lançamentos nem recorrências; o valor sai do cálculo.
 * - recorrente: o que costuma se repetir todo mês (ex.: aluguel, telefone, salário). Ao lançar nela, o sistema já
 *   sugere repetir o valor no ano (cria a recorrência). Não obriga: dá para lançar uma vez só.
 * - estimativa: como prever os próximos meses (a estimativa da despesa avulsa e o previsto das contas que se repetem
 *   e seguem a categoria); nulo = segue a regra da família.
 * - estimativaDesde/estimativaAntes: a regra mudou a partir de um mês futuro; até lá vale estimativaAntes (nula =
 *   a da família). Sem estimativaDesde, a regra vale para todos os meses previstos.
 */
public record Categoria(Long id, Long grupoId, String nome, int ordem, boolean ativa, boolean tributavel,
                        Percentual aliquota, boolean recorrente, Estimativa estimativa, Competencia estimativaDesde,
                        Estimativa estimativaAntes) {

    public static final int NOME_MAXIMO = 120;

    public Categoria {
        obrigatorio(grupoId, "grupo");
        nome = obrigatorio(nome, "nome");
        if (nome.length() > NOME_MAXIMO) {
            throw new RegraNegocioException("O nome da categoria pode ter no máximo " + NOME_MAXIMO + " caracteres");
        }
        if (aliquota != null && (aliquota.ehNegativo() || aliquota.valor().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new RegraNegocioException("A alíquota deve estar entre 0% e 100%");
        }
        if (aliquota != null && tributavel) {
            throw new RegraNegocioException("Uma categoria não pode ser tributável e calculada por alíquota");
        }
        if (aliquota != null && recorrente) {
            throw new RegraNegocioException("Categoria calculada por alíquota não recebe lançamentos: não pode ser recorrente");
        }
        if (estimativaDesde == null) {
            estimativaAntes = null;
        }
    }

    public Categoria(Long id, Long grupoId, String nome, int ordem, boolean ativa, boolean tributavel,
                     Percentual aliquota, boolean recorrente, Estimativa estimativa) {
        this(id, grupoId, nome, ordem, ativa, tributavel, aliquota, recorrente, estimativa, null, null);
    }

    /** Categoria que segue a estimativa da família. */
    public Categoria(Long id, Long grupoId, String nome, int ordem, boolean ativa, boolean tributavel,
                     Percentual aliquota, boolean recorrente) {
        this(id, grupoId, nome, ordem, ativa, tributavel, aliquota, recorrente, null, null, null);
    }

    /** Categoria que não se repete por padrão e segue a estimativa da família. */
    public Categoria(Long id, Long grupoId, String nome, int ordem, boolean ativa, boolean tributavel,
                     Percentual aliquota) {
        this(id, grupoId, nome, ordem, ativa, tributavel, aliquota, false, null, null, null);
    }

    /** A regra que vale para estimar os próximos meses: a da categoria ou, sem ela, a da família. */
    public Estimativa estimativaOu(Estimativa daFamilia) {
        return estimativa != null ? estimativa : daFamilia;
    }

    /** A regra própria que vale no mês (nula = segue a família): antes de estimativaDesde, a de antes da mudança. */
    public Estimativa estimativaEm(Competencia mes) {
        return estimativaDesde != null && mes.antesDe(estimativaDesde) ? estimativaAntes : estimativa;
    }

    /** A regra que vale no mês: a da categoria naquele mês ou, sem ela, a da família. */
    public Estimativa estimativaOu(Estimativa daFamilia, Competencia mes) {
        Estimativa propria = estimativaEm(mes);
        return propria != null ? propria : daFamilia;
    }

    /**
     * Nova regra própria (nula = segue a família). Sem mês (ou no mês atual), vale para todos os meses previstos. A
     * partir de um mês depois do atual, os meses antes dele continuam com a regra que vale hoje.
     */
    public Categoria comEstimativa(Estimativa nova, Competencia desde, Competencia mesAtual) {
        Estimativa antes = estimativaEm(mesAtual);
        boolean depois = desde != null && mesAtual.antesDe(desde) && !Objects.equals(antes, nova);
        return new Categoria(id, grupoId, nome, ordem, ativa, tributavel, aliquota, recorrente, nova,
                depois ? desde : null, depois ? antes : null);
    }

    public boolean calculada() {
        return aliquota != null;
    }

    /** Regras que dependem do grupo onde a categoria fica. */
    public void validarNo(Grupo grupo) {
        if (tributavel && grupo.tipo() != TipoMovimento.RECEITA) {
            throw new RegraNegocioException("Só categorias de receita podem ser tributáveis");
        }
        if (calculada() && grupo.tipo() != TipoMovimento.DESPESA) {
            throw new RegraNegocioException("Só categorias de despesa podem ter alíquota");
        }
    }

    /** Lançamentos e recorrências só entram em categoria ativa, de grupo ativo e não calculada. */
    public void exigirLancavel(Grupo grupo) {
        if (!ativa || !grupo.ativo()) {
            throw new RegraNegocioException("A categoria " + nome + " está desativada");
        }
        if (calculada()) {
            throw new RegraNegocioException("A categoria " + nome + " é calculada pela alíquota e não recebe lançamentos");
        }
    }

    /** Competência e emissão da nota só fazem sentido na receita tributável (as notas fiscais emitidas). */
    public void exigirAceitaNotaFiscal(NotaFiscal notaFiscal) {
        if (notaFiscal != null && !tributavel) {
            throw new RegraNegocioException("Competência e emissão da nota só valem para receita tributável, e "
                    + nome + " não é");
        }
    }
}
