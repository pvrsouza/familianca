package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Percentual;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import static br.com.familianca.domain.Validacoes.obrigatorio;

/**
 * A conta: todos os dados do orçamento pertencem a uma família, e é ela quem assina um plano.
 * metaAporte: meta de partida, o % do que sobra no mês (renda − despesas) que a família quer investir, usado nos meses
 * em que a regra do aporte não estima (ou ainda não tem base).
 * estimativa: regra padrão para estimar os próximos meses das despesas que não se repetem (começa pela média).
 * estimativaAporte: regra para estimar a meta de aporte dos meses sem meta definida (começa pela média de 3 meses).
 * metasAporte: meta de aporte definida à mão por mês.
 */
public class Familia {

    private final Long id;
    private String nome;
    private Assinatura assinatura;
    private Percentual metaAporte;
    private Estimativa estimativa;
    private Estimativa estimativaAporte;
    private final Map<Competencia, Percentual> metasAporte = new TreeMap<>();

    private Familia(Long id, String nome, Assinatura assinatura, Percentual metaAporte, Estimativa estimativa,
                    Estimativa estimativaAporte, Map<Competencia, Percentual> metasAporte) {
        this.id = id;
        this.nome = obrigatorio(nome, "nome da família");
        this.assinatura = obrigatorio(assinatura, "assinatura");
        this.metaAporte = validarMeta(metaAporte);
        this.estimativa = obrigatorio(estimativa, "estimativa");
        this.estimativaAporte = obrigatorio(estimativaAporte, "regra do aporte");
        metasAporte.forEach((competencia, meta) -> this.metasAporte.put(competencia, validarMeta(meta)));
    }

    public static Familia nova(String nome, Assinatura assinatura) {
        return new Familia(null, nome, assinatura, null, Estimativa.MEDIA_3M, Estimativa.MEDIA_3M, Map.of());
    }

    public static Familia reconstituir(Long id, String nome, Assinatura assinatura, Percentual metaAporte,
                                       Estimativa estimativa, Estimativa estimativaAporte,
                                       Map<Competencia, Percentual> metasAporte) {
        return new Familia(id, nome, assinatura, metaAporte, estimativa, estimativaAporte, metasAporte);
    }

    public void definirEstimativaAporte(Estimativa estimativa) {
        this.estimativaAporte = obrigatorio(estimativa, "regra do aporte");
    }

    /** Meta do mês definida à mão; nula volta à estimativa. */
    public void definirMetaAporte(Competencia competencia, Percentual meta) {
        obrigatorio(competencia, "mês");
        if (meta == null) {
            metasAporte.remove(competencia);
        } else {
            metasAporte.put(competencia, validarMeta(meta));
        }
    }

    /** Ao apagar os dados da família: as metas por mês fazem parte do orçamento. */
    public void limparMetasAporte() {
        metasAporte.clear();
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
    public Estimativa estimativaAporte() { return estimativaAporte; }
    public Map<Competencia, Percentual> metasAporte() { return Collections.unmodifiableMap(metasAporte); }
}
