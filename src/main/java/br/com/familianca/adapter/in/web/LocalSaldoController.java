package br.com.familianca.adapter.in.web;

import br.com.familianca.adapter.in.web.dto.LocalSaldoRequest;
import br.com.familianca.adapter.in.web.dto.LocalSaldoResponse;
import br.com.familianca.application.port.in.GerenciarLocaisSaldo;
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
@RequestMapping("/api/locais-saldo")
@Tag(name = "Lugares do saldo", description = """
        Onde está o dinheiro da família: conta corrente ou digital, poupança, dinheiro na mão, cofrinho, investimento
        de resgate rápido (fora da carteira de investimentos) ou outro. Cadastrados uma vez, servem para informar o
        saldo do mês item a item. Dono opcional. Qualquer membro consulta; só o DONO altera.""")
public class LocalSaldoController {

    private final GerenciarLocaisSaldo locais;

    public LocalSaldoController(GerenciarLocaisSaldo locais) {
        this.locais = locais;
    }

    @GetMapping
    @Operation(summary = "Listar lugares", description = "Ativos e inativos, por nome.")
    public List<LocalSaldoResponse> listar() {
        return locais.listar().stream().map(LocalSaldoResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar lugar")
    @ApiResponse(responseCode = "409", description = "Nome repetido")
    public LocalSaldoResponse criar(@Valid @RequestBody LocalSaldoRequest r) {
        return LocalSaldoResponse.de(locais.criar(r.nome(), r.tipo(), r.membroId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar, desativar ou reativar lugar", description = "Inativo não aparece nos meses novos.")
    public LocalSaldoResponse atualizar(@PathVariable Long id, @Valid @RequestBody LocalSaldoRequest r) {
        return LocalSaldoResponse.de(locais.atualizar(id, r.nome(), r.tipo(), r.membroId(), r.ativoOuPadrao()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir lugar que nunca entrou num saldo")
    @ApiResponse(responseCode = "409", description = "Já entrou num saldo: desative")
    public void remover(@PathVariable Long id) {
        locais.remover(id);
    }
}
