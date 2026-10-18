package br.com.familianca.adapter.in.web;

import br.com.familianca.adapter.in.web.dto.ImpactoRequest;
import br.com.familianca.adapter.in.web.dto.ImpactoResponse;
import br.com.familianca.adapter.in.web.dto.Valores;
import br.com.familianca.adapter.in.web.dto.VisaoAnualResponse;
import br.com.familianca.application.port.in.ConsultarVisaoAnual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Visão anual", description = """
        O diferencial do Familiança: o ano inteiro da família mês a mês e o peso de cada gasto no ano.

        - **Realizado** = lançamentos; **previsto** = ocorrências de recorrências ainda não confirmadas nem puladas,
          mais a **estimativa** (`estimado`) dos meses seguintes ao atual para despesas que não se repetem: média,
          máximo ou mínimo dos meses que já passaram, pela regra da família ou da categoria. O total de cada mês
          soma realizado e previsto. A estimativa não entra na visão por pessoa.
        - Por grupo e categoria: 12 meses, total e média do ano, participação no total do tipo, ranking das
          categorias e a inflação do grupo (variação mês a mês e acumulada, composta).
        - Resumo no formato da planilha: sobra do mês anterior, renda, aplicações, saldo disponível, despesas, fluxo
          de caixa líquido, renda dos investimentos, grau de independência financeira, aporte previsto e inflação
          familiar.
        - Por pessoa: receitas e despesas de cada membro e o peso de cada um no orçamento.""")
public class VisaoAnualController {

    private final ConsultarVisaoAnual visao;

    public VisaoAnualController(ConsultarVisaoAnual visao) {
        this.visao = visao;
    }

    @GetMapping("/api/visao-anual")
    @Operation(summary = "Visão do ano atual")
    public VisaoAnualResponse anoAtual() {
        return ano(visao.anoAtual());
    }

    @GetMapping("/api/visao-anual/{ano}")
    @Operation(summary = "Visão de um ano")
    public VisaoAnualResponse ano(@PathVariable int ano) {
        return VisaoAnualResponse.de(visao.visaoAnual(ano));
    }

    @PostMapping("/api/impacto")
    @Operation(summary = "Simular o impacto de um gasto no ano", description = """
            Quanto um gasto (único ou que se repete) representa no ano: valor anual, em 5 e 10 anos, % da renda e
            das despesas da família no ano e quantos dias de renda ele consome. Ex.: R$ 15 por dia útil.""")
    public ImpactoResponse impacto(@Valid @RequestBody ImpactoRequest r) {
        int ano = r.ano() != null ? r.ano() : visao.anoAtual();
        return ImpactoResponse.de(visao.simularImpacto(Valores.dinheiro(r.valor()), r.periodicidade(), ano), ano);
    }
}
