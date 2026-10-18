package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.GerenciarCategorias.PlanoContas;
import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Estimativa;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Natureza;
import br.com.familianca.domain.model.TipoMovimento;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** Árvore tipo → grupo → categoria, em ordem (receitas antes das despesas). */
public record PlanoContasResponse(List<GrupoResponse> grupos) {

    public static PlanoContasResponse de(PlanoContas p) {
        return new PlanoContasResponse(p.grupos().stream()
                .sorted(Comparator.comparing(Grupo::tipo).thenComparingInt(Grupo::ordem).thenComparing(Grupo::nome))
                .map(g -> GrupoResponse.de(g, p.categorias().stream()
                        .filter(c -> c.grupoId().equals(g.id()))
                        .sorted(Comparator.comparingInt(Categoria::ordem).thenComparing(Categoria::nome))
                        .toList()))
                .toList());
    }

    public record GrupoResponse(Long id, TipoMovimento tipo, Natureza natureza, String nome,
                                @Schema(description = "Grupo de despesas da empresa (PJ).") boolean empresa,
                                int ordem, boolean ativo, List<CategoriaResponse> categorias) {

        public static GrupoResponse de(Grupo g, List<Categoria> categorias) {
            return new GrupoResponse(g.id(), g.tipo(), g.natureza(), g.nome(), g.empresa(), g.ordem(), g.ativo(),
                    categorias.stream().map(CategoriaResponse::de).toList());
        }
    }

    public record CategoriaResponse(Long id, Long grupoId, String nome, int ordem, boolean ativa,
                                    @Schema(description = "Receita na base das alíquotas da PJ.") boolean tributavel,
                                    @Schema(description = "% sobre as receitas tributáveis do mês; nulo = categoria comum.")
                                    BigDecimal aliquota,
                                    @Schema(description = "Calculada pela alíquota: não recebe lançamentos.")
                                    boolean calculada,
                                    @Schema(description = "Costuma se repetir: ao lançar, o app sugere repetir no ano.")
                                    boolean recorrente,
                                    @Schema(description = "Regra própria para estimar os próximos meses; nulo = segue a família.")
                                    Estimativa estimativa) {

        public static CategoriaResponse de(Categoria c) {
            return new CategoriaResponse(c.id(), c.grupoId(), c.nome(), c.ordem(), c.ativa(), c.tributavel(),
                    Valores.valor(c.aliquota()), c.calculada(), c.recorrente(), c.estimativa());
        }
    }
}
