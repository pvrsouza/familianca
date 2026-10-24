package br.com.familianca.adapter.in.web;

import br.com.familianca.adapter.in.web.dto.CategoriaRequest;
import br.com.familianca.adapter.in.web.dto.EstimativaPropriaRequest;
import br.com.familianca.adapter.in.web.dto.PlanoContasResponse;
import br.com.familianca.adapter.in.web.dto.PlanoContasResponse.CategoriaResponse;
import br.com.familianca.adapter.in.web.dto.PlanoContasResponse.GrupoResponse;
import br.com.familianca.adapter.in.web.dto.GrupoRequest;
import br.com.familianca.application.port.in.GerenciarCategorias;
import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.vo.Competencia;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@Tag(name = "Categorias", description = """
        Plano de contas da família: **tipo** (RECEITA/DESPESA) → **grupo** (natureza FIXA/VARIAVEL, ex.: Habitação)
        → **categoria** (ex.: Supermercado). Toda família nasce com as categorias padrão. Qualquer membro consulta;
        só o **DONO** altera.

        - Desativar (grupo ou categoria) bloqueia novos lançamentos e recorrências e preserva o histórico; excluir só
          vale sem uso (grupo sem categorias, categoria sem lançamentos e recorrências), senão 409.
        - Nome único (sem diferenciar maiúsculas): grupo por tipo, categoria por grupo (409).
        - **PJ** (recurso DESPESAS_PJ): grupo `empresa`, receita `tributavel` e despesa com `aliquota` (calculada
          todo mês como % das receitas tributáveis, sem lançamentos). `POST /modelos/pj` cria o modelo pronto.""")
public class CategoriaController {

    private final GerenciarCategorias categorias;

    public CategoriaController(GerenciarCategorias categorias) {
        this.categorias = categorias;
    }

    @GetMapping
    @Operation(summary = "Plano de contas", description = "Árvore com tudo, inclusive desativados.")
    public PlanoContasResponse listar() {
        return PlanoContasResponse.de(categorias.listar());
    }

    @PostMapping("/grupos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar grupo")
    @ApiResponse(responseCode = "402", description = "Grupo da empresa sem o recurso DESPESAS_PJ")
    @ApiResponse(responseCode = "409", description = "Nome repetido no tipo")
    public GrupoResponse criarGrupo(@Valid @RequestBody GrupoRequest r) {
        if (r.tipo() == null) {
            throw new RegraNegocioException("Campo obrigatório: tipo");
        }
        return GrupoResponse.de(categorias.criarGrupo(r.paraDados()), List.of());
    }

    @PutMapping("/grupos/{id}")
    @Operation(summary = "Editar, desativar ou reativar grupo", description = "O tipo não muda.")
    public GrupoResponse atualizarGrupo(@PathVariable Long id, @Valid @RequestBody GrupoRequest r) {
        Grupo g = categorias.atualizarGrupo(id, r.paraDados(), r.ativoOuPadrao());
        return PlanoContasResponse.de(categorias.listar()).grupos().stream()
                .filter(gr -> gr.id().equals(g.id())).findFirst().orElseThrow();
    }

    @DeleteMapping("/grupos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir grupo sem categorias")
    @ApiResponse(responseCode = "409", description = "O grupo tem categorias")
    public void removerGrupo(@PathVariable Long id) {
        categorias.removerGrupo(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar categoria")
    @ApiResponse(responseCode = "402", description = "Tributável ou alíquota sem o recurso DESPESAS_PJ")
    @ApiResponse(responseCode = "409", description = "Nome repetido no grupo")
    @ApiResponse(responseCode = "422", description = "Tributável em despesa, alíquota em receita etc.")
    public CategoriaResponse criarCategoria(@Valid @RequestBody CategoriaRequest r) {
        return CategoriaResponse.de(categorias.criarCategoria(r.paraDados()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar, mover, desativar ou reativar categoria", description = "Só para grupo do mesmo tipo.")
    public CategoriaResponse atualizarCategoria(@PathVariable Long id, @Valid @RequestBody CategoriaRequest r) {
        return CategoriaResponse.de(categorias.atualizarCategoria(id, r.paraDados(), r.ativaOuPadrao()));
    }

    @PutMapping("/{id}/estimativa")
    @Operation(summary = "Regra própria de estimativa da categoria", description = """
            Como prever os próximos meses das despesas desta categoria e de todas as contas dela (a regra própria de
            cada conta é substituída: passam a seguir a categoria). Nulo
            volta a seguir a regra da família. Sem `desde`, vale para todos os meses previstos e os valores ajustados
            à mão ficam; com `desde` (AAAA-MM), vale desse mês em diante e os ajustados à mão das contas da categoria,
            desse mês em diante, voltam a seguir a regra. Só quem organiza a família.""")
    public CategoriaResponse definirEstimativa(@PathVariable Long id, @Valid @RequestBody EstimativaPropriaRequest r) {
        return CategoriaResponse.de(categorias.definirEstimativa(id, r.estimativa(),
                r.desde() != null ? Competencia.parse(r.desde()) : null));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir categoria sem uso")
    @ApiResponse(responseCode = "409", description = "Tem lançamentos ou recorrências: desative")
    public void removerCategoria(@PathVariable Long id) {
        categorias.removerCategoria(id);
    }

    @PostMapping("/modelos/pj")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Aplicar o modelo PJ", description = """
            Cria a receita tributável "Notas fiscais emitidas" e o grupo "Empresa (PJ)" com INSS, DAS/Simples, ISS,
            PIS, IRPJ e CSLL (alíquota 0%, a ajustar), contador, pró-labore e despesas gerais. Uma vez só (409).""")
    @ApiResponse(responseCode = "402", description = "Sem o recurso DESPESAS_PJ")
    public PlanoContasResponse aplicarModeloPj() {
        return PlanoContasResponse.de(categorias.aplicarModeloPj());
    }
}
