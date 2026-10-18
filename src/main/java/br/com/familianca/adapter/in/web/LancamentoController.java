package br.com.familianca.adapter.in.web;

import br.com.familianca.adapter.in.web.dto.LancamentoRequest;
import br.com.familianca.adapter.in.web.dto.LancamentoResponse;
import br.com.familianca.adapter.in.web.dto.ParceladoRequest;
import br.com.familianca.application.port.in.GerenciarLancamentos;
import br.com.familianca.application.port.in.GerenciarLancamentos.Filtro;
import br.com.familianca.domain.model.TipoMovimento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/lancamentos")
@Tag(name = "Lançamentos", description = """
        Receitas e despesas que aconteceram (ou vão acontecer, como parcelas futuras). Cada lançamento conta no mês
        da `data`; o tipo vem do grupo da categoria. `membroId` diz de quem é o gasto ou a receita: é isso que
        aparece na visão por pessoa.

        **Permissões:** todos veem tudo; MEMBRO lança, edita e exclui só os próprios (403 nos de outra pessoa);
        DONO mexe em qualquer um. Categoria desativada ou calculada por alíquota não recebe lançamento (422).""")
public class LancamentoController {

    private final GerenciarLancamentos lancamentos;

    public LancamentoController(GerenciarLancamentos lancamentos) {
        this.lancamentos = lancamentos;
    }

    @GetMapping
    @Operation(summary = "Listar lançamentos", description = "Mais recentes primeiro. Sem `de`/`ate`, o ano atual.")
    public List<LancamentoResponse> listar(
            @Parameter(description = "Data a partir de (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @Parameter(description = "Data até (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long grupoId,
            @RequestParam(required = false) Long membroId,
            @RequestParam(required = false) TipoMovimento tipo) {
        return lancamentos.listar(new Filtro(de, ate, categoriaId, grupoId, membroId, tipo)).stream()
                .map(LancamentoResponse::de).toList();
    }

    @GetMapping("/descricoes")
    @Operation(summary = "Descrições já usadas na categoria", description = """
            Para sugerir ao lançar e evitar nomes diferentes para a mesma coisa. Sem `q`: as 3 descrições mais
            usadas na categoria. Com `q`: até 8 descrições dos lançamentos e recorrências da categoria que contêm
            parte do termo ou se parecem com ele (sem ligar para acento, maiúscula ou erro de digitação: "mercdo"
            acha "Mercado do mês"), as que começam com o termo primeiro. Variações da mesma descrição viram uma.""")
    @ApiResponse(responseCode = "404", description = "Categoria não encontrada")
    @ApiResponse(responseCode = "422", description = "Termo com mais de 200 caracteres")
    public List<String> descricoes(@RequestParam Long categoriaId,
                                   @Parameter(description = "Parte da descrição digitada") @RequestParam(required = false) String q) {
        return lancamentos.descricoesUsadas(categoriaId, q);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar lançamento")
    @ApiResponse(responseCode = "404", description = "Lançamento não encontrado")
    public LancamentoResponse buscar(@PathVariable Long id) {
        return LancamentoResponse.de(lancamentos.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Lançar receita ou despesa")
    @ApiResponse(responseCode = "201", description = "Lançado")
    @ApiResponse(responseCode = "403", description = "MEMBRO lançando em nome de outra pessoa")
    @ApiResponse(responseCode = "404", description = "Categoria ou membro não encontrado")
    @ApiResponse(responseCode = "422", description = "Categoria desativada ou calculada; membro inativo")
    public LancamentoResponse lancar(@Valid @RequestBody LancamentoRequest r) {
        return LancamentoResponse.de(lancamentos.lancar(r.paraDados()));
    }

    @PostMapping("/parcelados")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Lançar compra parcelada", description = """
            Divide `valorTotal` em `parcelas` mensais (2 a 60) a partir de `primeiraData`; cada parcela é um
            lançamento no seu mês, ligadas pelo mesmo `parcelamentoId`.""")
    public List<LancamentoResponse> lancarParcelado(@Valid @RequestBody ParceladoRequest r) {
        return lancamentos.lancarParcelado(r.paraDados(), r.parcelas()).stream().map(LancamentoResponse::de).toList();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar lançamento", description = "Substitui os campos. Numa parcela, só ela muda.")
    public LancamentoResponse atualizar(@PathVariable Long id, @Valid @RequestBody LancamentoRequest r) {
        return LancamentoResponse.de(lancamentos.atualizar(id, r.paraDados()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir lançamento", description = """
            `todasAsParcelas=true` exclui a compra parcelada inteira. Excluir a confirmação de uma recorrência faz a
            ocorrência voltar a ser prevista.""")
    public void remover(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean todasAsParcelas) {
        lancamentos.remover(id, todasAsParcelas);
    }
}
