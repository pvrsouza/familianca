package br.com.familianca.adapter.in.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String DESCRICAO = """
            Familiança: o orçamento é de todos. API do orçamento familiar com visão anual: cada família (conta)
            tem membros, um plano de categorias, lançamentos, recorrências, saldos e investimentos, e enxerga o ano
            inteiro mês a mês, com o peso de cada gasto no ano.

            **Fluxo típico:** `POST /api/cadastro` (cria a família com as categorias padrão) → login → convidar a
            família (`POST /api/familia/convites`) → lançar despesas e receitas (`/api/lancamentos`) e cadastrar as
            que se repetem (`/api/recorrencias`) → acompanhar `GET /api/visao-anual/{ano}` → simular o impacto de um
            gasto (`POST /api/impacto`).

            **Acesso:** toda rota exige login (sessão em cookie), exceto login, token CSRF, cadastro e o link de
            convite. Faça `GET /api/auth/csrf` e depois `POST /api/auth/login`; o Swagger envia o token CSRF sozinho.
            Cada família só enxerga os próprios dados (o de outra família responde 404).

            **Papéis na família:** `DONO` gerencia membros, convites, categorias, saldos e investimentos e altera
            qualquer lançamento; `MEMBRO` lança e altera só os próprios lançamentos e vê o orçamento inteiro.

            **Convenções gerais**
            - Datas no formato ISO `AAAA-MM-DD`; competências (mês) no formato `AAAA-MM`.
            - Valores monetários com 2 casas decimais, arredondamento `HALF_UP`. Percentuais em % (ex.: `2` = 2%).
            - Séries mensais: listas de 12 valores, de janeiro a dezembro.
            - Datas "de hoje" usam o fuso `America/Sao_Paulo`.

            **Erros** no formato `ProblemDetail` (RFC 9457):
            - `400` dados inválidos (campos obrigatórios, formatos);
            - `401` não autenticado (sem sessão, sessão expirada ou credenciais inválidas);
            - `402` recurso fora do plano da família (campos `plano` e `recurso`) ou limite de pessoas do plano;
            - `403` sem permissão (papel) ou token CSRF ausente/inválido;
            - `404` recurso não encontrado (ou de outra família);
            - `409` conflito (ex.: nome repetido, e-mail já cadastrado, categoria em uso);
            - `422` regra de negócio violada (o campo `detail` explica o motivo);
            - `429` muitas tentativas de login ou cadastro.
            """;

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Familiança")
                .version("Fase 1")
                .description(DESCRICAO));
    }
}
