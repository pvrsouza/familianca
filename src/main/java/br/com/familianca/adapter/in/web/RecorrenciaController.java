package br.com.familianca.adapter.in.web;

import br.com.familianca.adapter.in.web.dto.ConfirmacaoRequest;
import br.com.familianca.adapter.in.web.dto.LancamentoResponse;
import br.com.familianca.adapter.in.web.dto.OcorrenciaPendenteResponse;
import br.com.familianca.adapter.in.web.dto.RecorrenciaRequest;
import br.com.familianca.adapter.in.web.dto.RecorrenciaResponse;
import br.com.familianca.adapter.in.web.dto.Valores;
import br.com.familianca.application.port.in.GerenciarRecorrencias;
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
@RequestMapping("/api/recorrencias")
@Tag(name = "Recorrências", description = """
        Receitas e despesas que se repetem (salário, condomínio, escola, streaming, IPVA anual). Não viram
        lançamento sozinhas: cada ocorrência aparece como **prevista** na visão anual até ser **confirmada** (vira
        lançamento, com o valor real) ou **pulada**. Assim o ano inteiro já aparece desde janeiro.

        Frequências: MENSAL, BIMESTRAL, TRIMESTRAL, SEMESTRAL, ANUAL, contadas a partir do `inicio`. Mesmas regras de
        permissão dos lançamentos.

        **Confirma sozinha** (`confirmarSozinha`, só despesa): para débito automático e conta de valor fixo. O que
        vence a partir do dia em que foi ligada vira lançamento sozinho, com o valor previsto, na próxima consulta da
        família. Valor diferente: edite o lançamento. Excluir essa confirmação marca o mês como pulado (não houve).""")
public class RecorrenciaController {

    private final GerenciarRecorrencias recorrencias;

    public RecorrenciaController(GerenciarRecorrencias recorrencias) {
        this.recorrencias = recorrencias;
    }

    @GetMapping
    @Operation(summary = "Listar recorrências", description = "Com o valor no ano atual e o valor anualizado.")
    public List<RecorrenciaResponse> listar() {
        return recorrencias.listar().stream().map(RecorrenciaResponse::de).toList();
    }

    @GetMapping("/pendentes")
    @Operation(summary = "Ocorrências a confirmar", description = """
            Ocorrências dos últimos 12 meses com data até hoje, nem confirmadas nem puladas: o que a família ainda
            precisa conferir. Mais antigas primeiro.""")
    public List<OcorrenciaPendenteResponse> pendentes() {
        return recorrencias.pendentes().stream().map(OcorrenciaPendenteResponse::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar recorrência")
    public RecorrenciaResponse buscar(@PathVariable Long id) {
        return RecorrenciaResponse.de(recorrencias.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar recorrência")
    public RecorrenciaResponse criar(@Valid @RequestBody RecorrenciaRequest r) {
        return RecorrenciaResponse.de(recorrencias.criar(r.paraDados()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar recorrência", description = """
            Vale para as ocorrências ainda não confirmadas (as confirmadas já são lançamentos). Para encerrar,
            defina o `fim`.""")
    public RecorrenciaResponse atualizar(@PathVariable Long id, @Valid @RequestBody RecorrenciaRequest r) {
        return RecorrenciaResponse.de(recorrencias.atualizar(id, r.paraDados()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir recorrência")
    @ApiResponse(responseCode = "409", description = "Já tem ocorrências confirmadas: defina o fim")
    public void remover(@PathVariable Long id) {
        recorrencias.remover(id);
    }

    @PostMapping("/{id}/ocorrencias/{competencia}/confirmacao")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Confirmar ocorrência", description = """
            Cria o lançamento da ocorrência da competência (AAAA-MM). Sem corpo, usa o valor e o dia da recorrência.
            Já confirmada: 409. Pulada ou fora da agenda: 422.""")
    @ApiResponse(responseCode = "201", description = "Lançamento criado")
    public LancamentoResponse confirmar(@PathVariable Long id, @PathVariable String competencia,
                                        @Valid @RequestBody(required = false) ConfirmacaoRequest r) {
        return LancamentoResponse.de(recorrencias.confirmar(id, Competencia.parse(competencia),
                r != null ? Valores.dinheiro(r.valor()) : null, r != null ? r.data() : null));
    }

    @PostMapping("/{id}/ocorrencias/{competencia}/pulo")
    @Operation(summary = "Pular ocorrência", description = "A competência deixa de ser prevista. Já confirmada: 409.")
    public RecorrenciaResponse pular(@PathVariable Long id, @PathVariable String competencia) {
        return RecorrenciaResponse.de(recorrencias.pular(id, Competencia.parse(competencia)));
    }

    @DeleteMapping("/{id}/ocorrencias/{competencia}/pulo")
    @Operation(summary = "Desfazer pulo")
    public RecorrenciaResponse desfazerPulo(@PathVariable Long id, @PathVariable String competencia) {
        return RecorrenciaResponse.de(recorrencias.desfazerPulo(id, Competencia.parse(competencia)));
    }
}
