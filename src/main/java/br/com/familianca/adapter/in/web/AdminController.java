package br.com.familianca.adapter.in.web;

import br.com.familianca.adapter.in.web.dto.AssinaturaDto;
import br.com.familianca.adapter.in.web.dto.FamiliaAdminResponse;
import br.com.familianca.application.port.in.AdministrarPlataforma;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Administração", description = """
        Operação da plataforma, só **ADMIN** da plataforma (os demais recebem 403). Enquanto não há cobrança
        recorrente integrada, a assinatura das famílias é ajustada aqui.""")
public class AdminController {

    private final AdministrarPlataforma admin;

    public AdminController(AdministrarPlataforma admin) {
        this.admin = admin;
    }

    @GetMapping("/familias")
    @Operation(summary = "Famílias da plataforma")
    public List<FamiliaAdminResponse> listar() {
        return admin.listarFamilias().stream().map(FamiliaAdminResponse::de).toList();
    }

    @PutMapping("/familias/{id}/assinatura")
    @Operation(summary = "Alterar a assinatura de uma família")
    public FamiliaAdminResponse alterarAssinatura(@PathVariable Long id, @Valid @RequestBody AssinaturaDto r) {
        admin.alterarAssinatura(id, r.paraDominio());
        return admin.listarFamilias().stream().filter(f -> f.familia().id().equals(id))
                .findFirst().map(FamiliaAdminResponse::de).orElseThrow();
    }
}
