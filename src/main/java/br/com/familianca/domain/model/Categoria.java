package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.vo.Percentual;

import java.math.BigDecimal;

import static br.com.familianca.domain.Validacoes.obrigatorio;

/**
 * Onde cada receita ou despesa é classificada (ex.: Supermercado, Plano de saúde, Salário).
 * - tributavel: receita que forma a base das alíquotas da PJ (ex.: notas fiscais emitidas).
 * - aliquota: despesa calculada = alíquota × receitas tributáveis do mês (ex.: ISS, Simples). Não recebe
 *   lançamentos nem recorrências; o valor sai do cálculo.
 * - recorrente: o que costuma se repetir todo mês (ex.: aluguel, telefone, salário). Ao lançar nela, o sistema já
 *   sugere repetir o valor no ano (cria a recorrência). Não obriga: dá para lançar uma vez só.
 * - estimativa: como estimar os próximos meses quando a despesa não se repete; nulo = segue a regra da família.
 */
public record Categoria(Long id, Long grupoId, String nome, int ordem, boolean ativa, boolean tributavel,
                        Percentual aliquota, boolean recorrente, Estimativa estimativa) {

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
    }

    /** Categoria que segue a estimativa da família. */
    public Categoria(Long id, Long grupoId, String nome, int ordem, boolean ativa, boolean tributavel,
                     Percentual aliquota, boolean recorrente) {
        this(id, grupoId, nome, ordem, ativa, tributavel, aliquota, recorrente, null);
    }

    /** Categoria que não se repete por padrão e segue a estimativa da família. */
    public Categoria(Long id, Long grupoId, String nome, int ordem, boolean ativa, boolean tributavel,
                     Percentual aliquota) {
        this(id, grupoId, nome, ordem, ativa, tributavel, aliquota, false, null);
    }

    /** A regra que vale para estimar os próximos meses: a da categoria ou, sem ela, a da família. */
    public Estimativa estimativaOu(Estimativa daFamilia) {
        return estimativa != null ? estimativa : daFamilia;
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
}
