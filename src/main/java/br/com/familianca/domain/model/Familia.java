package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.vo.Percentual;

import java.math.BigDecimal;

import static br.com.familianca.domain.Validacoes.obrigatorio;

/**
 * A conta: todos os dados do orçamento pertencem a uma família, e é ela quem assina um plano.
 * metaAporte: % do que sobra no mês (renda − despesas) que a família quer investir; base da previsão de aporte.
 * estimativa: regra padrão para estimar os próximos meses das despesas que não se repetem (começa pela média).
 */
public class Familia {

    private final Long id;
    private String nome;
    private Assinatura assinatura;
    private Percentual metaAporte;
    private Estimativa estimativa;

    private Familia(Long id, String nome, Assinatura assinatura, Percentual metaAporte, Estimativa estimativa) {
        this.id = id;
        this.nome = obrigatorio(nome, "nome da família");
        this.assinatura = obrigatorio(assinatura, "assinatura");
        this.metaAporte = validarMeta(metaAporte);
        this.estimativa = obrigatorio(estimativa, "estimativa");
    }

    public static Familia nova(String nome, Assinatura assinatura) {
        return new Familia(null, nome, assinatura, null, Estimativa.MEDIA);
    }

    public static Familia reconstituir(Long id, String nome, Assinatura assinatura, Percentual metaAporte,
                                       Estimativa estimativa) {
        return new Familia(id, nome, assinatura, metaAporte, estimativa);
    }

    public void definirEstimativa(Estimativa estimativa) {
        this.estimativa = obrigatorio(estimativa, "estimativa");
    }

    public void atualizar(String nome, Percentual metaAporte) {
        this.nome = obrigatorio(nome, "nome da família");
        this.metaAporte = validarMeta(metaAporte);
    }

    public void alterarAssinatura(Assinatura assinatura) {
        this.assinatura = obrigatorio(assinatura, "assinatura");
    }

    private static Percentual validarMeta(Percentual meta) {
        if (meta != null && (meta.ehNegativo() || meta.valor().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new RegraNegocioException("A meta de aporte deve estar entre 0% e 100%");
        }
        return meta;
    }

    public Long id() { return id; }
    public String nome() { return nome; }
    public Assinatura assinatura() { return assinatura; }
    public Percentual metaAporte() { return metaAporte; }
    public Estimativa estimativa() { return estimativa; }
}
