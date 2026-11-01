package br.com.familianca.adapter.in.web;

import br.com.familianca.adapter.in.web.dto.ImportacaoRequest;
import br.com.familianca.application.port.in.GerenciarImportacoes;
import br.com.familianca.application.port.in.GerenciarImportacoes.Analise;
import br.com.familianca.application.port.in.GerenciarImportacoes.Desfeito;
import br.com.familianca.application.port.in.GerenciarImportacoes.Lote;
import br.com.familianca.application.port.in.GerenciarImportacoes.Resultado;
import br.com.familianca.domain.exception.ArquivoInvalidoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/importacoes")
@Tag(name = "Importação de extrato", description = """
        Importa o extrato do banco ou do cartão (OFX 1.x/2.x ou OFC) para um lugar do saldo. O arquivo é lido em
        memória e descartado: nada dele fica guardado, só a chave de cada linha, para não importar duas vezes.
        Limites: 2 MB e 5.000 lançamentos por arquivo.

        **Fluxo:** `POST /analisar` (não grava) devolve cada linha como NOVA, JA_IMPORTADA ou POSSIVEL_DUPLICATA, com
        a ação sugerida e a categoria sugerida; `POST /` grava o que a pessoa confirmou, tudo ou nada.

        **Permissões:** MEMBRO importa só em nome próprio e só em lugar sem dono ou dele (403); DONO em qualquer um.
        Desfazer: DONO ou quem importou.""")
public class ImportacaoController {

    private final GerenciarImportacoes importacoes;

    public ImportacaoController(GerenciarImportacoes importacoes) {
        this.importacoes = importacoes;
    }

    @PostMapping(value = "/analisar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Analisar extrato", description = "Lê o arquivo e compara com o que já existe. Não grava nada.")
    @ApiResponse(responseCode = "400", description = "Arquivo vazio, ilegível, grande demais ou de formato não suportado")
    @ApiResponse(responseCode = "403", description = "MEMBRO num lugar do saldo de outra pessoa")
    @ApiResponse(responseCode = "404", description = "Lugar do saldo não encontrado")
    public Analise analisar(@Parameter(description = "Arquivo .ofx ou .ofc") @RequestParam("arquivo") MultipartFile arquivo,
                            @RequestParam Long localSaldoId) throws IOException {
        String nome = arquivo.getOriginalFilename() == null ? "" : arquivo.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!nome.endsWith(".ofx") && !nome.endsWith(".ofc")) {
            throw new ArquivoInvalidoException("Formato não suportado: envie um arquivo .ofx ou .ofc");
        }
        return importacoes.analisar(arquivo.getBytes(), localSaldoId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Importar linhas do extrato", description = """
            Tudo ou nada. Cada linha vale como um lançamento normal (categoria ativa e do mesmo tipo, membro ativo).
            Linha cuja chave já existe na hora de gravar conta em `jaExistiam`. Sem categoria, usa "A classificar".""")
    @ApiResponse(responseCode = "403", description = "Lugar ou membro de outra pessoa, para MEMBRO")
    @ApiResponse(responseCode = "404", description = "Lugar, categoria ou membro não encontrado")
    @ApiResponse(responseCode = "422", description = "Categoria desativada ou de outro tipo; saldo inicial não informado")
    public Resultado importar(@Valid @RequestBody ImportacaoRequest r) {
        return importacoes.importar(r.paraPedido());
    }

    @GetMapping
    @Operation(summary = "Listar importações", description = "Mais recentes primeiro.")
    public List<Lote> listar() {
        return importacoes.listar();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desfazer importação", description = """
            Apaga os lançamentos do lote e as chaves das linhas ignoradas, menos os lançamentos editados depois da
            importação (ficam; o retorno diz quantos).""")
    @ApiResponse(responseCode = "403", description = "Não é o dono da família nem quem importou")
    @ApiResponse(responseCode = "404", description = "Importação não encontrada")
    public Desfeito desfazer(@PathVariable Long id) {
        return importacoes.desfazer(id);
    }
}
