# Familiança

*O orçamento é de todos.* API REST do orçamento familiar com visão anual. Regras de negócio, decisões e pendências
em [`projeto/projeto.md`](projeto/projeto.md); este arquivo é o lado técnico.

## Stack

Mesma do controle-alugueis: Java 21, Spring Boot 4.1 (WebMVC, Data JPA, Validation, Security, Session JDBC),
PostgreSQL 16, Flyway, springdoc (Swagger UI), Maven Wrapper. Testes: JUnit 5, AssertJ, Testcontainers, ArchUnit.

## Front (Vue)

`frontend/`: Vue 3 + TypeScript + Vite, vue-router, ícones Lucide, fontes Fraunces e Figtree servidas pelo próprio app
(`@fontsource`, sem Google Fonts por causa da CSP) e `qrcode` para o QR do MFA, gerado no navegador. Cores, tipografia e
voz seguem [`projeto/marca.md`](projeto/marca.md); o contexto de produto para a skill impeccable está em
[`PRODUCT.md`](PRODUCT.md).

```bash
cd frontend
npm install
npm run dev     # http://localhost:5174 no host; /api vai para a API em 8080 (proxy do Vite)
npm run build   # vue-tsc + build em frontend/dist
```

- Mesma origem em dev e em produção: o Vite repassa `/api`, e na imagem o `dist` vira `static/` do Spring Boot
  (estágio `front` do `docker/app.Dockerfile`). Sem CORS.
- Rotas do front (history mode) ficam em `frontend/src/router.ts` **e** em `SpaController.ROTAS`: o Spring devolve o
  `index.html` só para as rotas listadas; qualquer outra URL continua negada. Rota nova = as duas listas.
- `src/api/http.ts` busca o token CSRF, manda `X-XSRF-TOKEN` nas requisições que alteram dados e trata os erros
  (ProblemDetail): 401 volta para o login, 402 mostra o recurso fora do plano, 429 diz quanto esperar.
- Nada de `v-html`: o Vue escapa todo texto, e a API não escapa HTML.
- Tema claro/escuro: as cores de `src/estilos/base.css` usam `light-dark()`, e `src/estado/tema.ts` grava a escolha
  (Automático, Claro ou Escuro, em Minha conta) só no navegador e marca `data-tema` no `<html>`. Cor nova entra como
  token com os dois valores, não fixa no componente.

## Pré-requisito

Somente **Docker Desktop**. Nada mais é instalado na máquina (o devcontainer traz JDK, Maven e Node).

## Desenvolver (VSCode ou IntelliJ)

- **VSCode:** extensão *Dev Containers* → `F1` → *Reopen in Container*.
- **IntelliJ:** *Remote Development* → *Dev Containers* → `.devcontainer/devcontainer.json`.

Dentro do container:

```bash
./mvnw test              # todos os testes (unitários + integração com Testcontainers)
./mvnw spring-boot:run   # API em http://localhost:8082/swagger-ui (no host)
```

Ou *Run/Debug* (`.vscode/launch.json`): **Backend DEV (Postgres local)** (lê o `.env`), **Frontend DEV (Vite)**
(`npm run dev` em `frontend/`) ou **Back + Front DEV**, que sobe os dois e para os dois juntos.

### Portas

As portas no host são diferentes das do controle-alugueis, para os dois devcontainers subirem juntos:

| Serviço | Container | Host |
|---|---|---|
| API (rodando no `dev`) | 8080 | `127.0.0.1:8082` |
| Front (Vite, `npm run dev` em `frontend/`) | 5173 | `127.0.0.1:5174` |
| Postgres | 5432 | `127.0.0.1:5433` |
| `app` (profile `app`, imagem de produção) | 8080 | `127.0.0.1:8083` |

Mesmas regras do controle-alugueis para os containers: o devcontainer sobe só `dev` e `postgres`; o `app` fica no
profile `app` (`docker compose --profile app up -d --build`), para usar **ou** a IDE **ou** a imagem. Não derrube o
`dev` com a IDE conectada (`docker compose down` derruba tudo); para mexer só no banco:
`docker compose restart postgres`.

Fora do devcontainer, direto no Mac (JDK 21+ e Maven): `docker compose up -d postgres` e `./mvnw spring-boot:run`
(a configuração padrão já aponta para `localhost:5433`).

### Testar a API

- **Swagger UI:** `http://localhost:8082/swagger-ui` (exige login: faça `GET /api/auth/csrf`, `POST /api/cadastro` e
  `POST /api/auth/login` pelo próprio Swagger, que envia o token CSRF sozinho).
- **Bruno:** coleção em `bruno/Familiança`, com todas as rotas. Crie `bruno/Familiança/.env` (fora do git) com
  `LOGIN_EMAIL` e `LOGIN_SENHA` (12+ caracteres) e rode `cadastro/Token CSRF` → `cadastro/Criar conta e família` →
  `auth/Entrar`. Ambientes: `local` (8082, Bruno no host) e `devcontainer` (8080, Bruno dentro do container).

## Arquitetura — Hexagonal (Ports & Adapters)

```
br.com.familianca
├─ domain/              Java puro (sem Spring/JPA)
│  ├─ model/            Usuario, Familia, Assinatura, Plano, Recurso, Membro, Convite, Grupo, Categoria,
│  │                    Lancamento, Parcela, Recorrencia, SaldoMes, Investimento, MovimentoInvestimento,
│  │                    VisaoAnual, CarteiraAnual, Variacao
│  ├─ vo/               Dinheiro, Percentual, Competencia (AAAA-MM), SerieMensal (12 meses)
│  ├─ service/          CalculadoraVisaoAnual, CalculadoraInvestimentos, CalculadoraImpacto,
│  │                    GeradorParcelas, CatalogoCategorias (categorias padrão e modelo PJ)
│  └─ exception/        RegraNegocio (422), NaoEncontrado (404), Conflito (409), AcessoNegado (403),
│                       RecursoForaDoPlano (402)
├─ application/
│  ├─ port/in/          casos de uso (CadastrarFamilia, GerenciarLancamentos, ConsultarVisaoAnual...)
│  ├─ port/out/         repositórios, Relogio, UsuarioAtual, SessoesUsuario
│  └─ service/          implementa port/in; @Transactional e @PreAuthorize aqui
└─ adapter/
   ├─ in/web/           controllers REST, DTOs, TratadorErros (ProblemDetail)
   ├─ in/seguranca/     Spring Security + Spring Session (copiado do controle-alugueis, com a família na sessão)
   └─ out/              persistence/ (JPA) e sistema/ (RelogioSistema)
```

Regras verificadas pelo `ArquiteturaTest` (ArchUnit): o domínio não depende de nada externo; a aplicação não
conhece adapters; adapter de entrada não conhece adapter de saída.

### Multi-família (tenant)

A **família** é a conta. Toda tabela do orçamento tem `familia_id`; os adapters de persistência filtram e gravam
sempre pela família do usuário logado (`UsuarioAtual.familiaId()`, que vem da sessão). Registro de outra família
responde 404, como se não existisse. As exceções explícitas são o cadastro e o aceite de convite (antes do login) e
a administração da plataforma.

### Busca de descrições (pg_trgm)

A sugestão de descrição ao lançar busca no Postgres, sem serviço externo: `unaccent` ignora acentos e `pg_trgm`
(`word_similarity`) acha o termo pela metade ou com erro de digitação. As duas extensões vêm com o Postgres e são
criadas pela migration `V10` (trusted desde o PG 13: o dono do banco cria sem superusuário). No JPQL elas aparecem
como `sem_acento(...)` e `semelhanca(termo, texto)`, registradas com tipo em `FuncoesBusca` (sem SQL nativo). Sem
índice por enquanto: a busca já é limitada a uma categoria de uma família; com muito volume, avaliar um índice GIN
de trigramas sobre uma coluna normalizada.

### Planos e recursos (assinatura)

`Plano` (GRATUITO, FAMILIA, PREMIUM) define o limite de pessoas e os `Recurso`s liberados (INVESTIMENTOS,
DESPESAS_PJ). `Assinatura.planoEmVigor(hoje)` resolve o plano que vale (teste/ativa dentro da validade, senão
GRATUITO). Os serviços checam com `AcessoFamilia.exigir(Recurso)` e a API responde **402** com `plano` e `recurso`
no corpo. Recurso novo pago = valor novo no enum + `exigir` no serviço.

## Segurança

Mesma lógica do controle-alugueis, mais o que está abaixo. Avaliação item a item e plano do que falta em
[`docs/seguranca.md`](docs/seguranca.md).

- **Sessão no servidor** (Spring Session JDBC), cookie `SESSION` HttpOnly, SameSite=Strict, Secure no perfil
  `prod`; 30 min de inatividade e no máximo 12 h desde o login, mesmo em uso (`SESSAO_DURACAO_MAXIMA`). A sessão
  guarda o usuário, a família, o membro e os papéis.
- **CSRF** em toda requisição que altera dados, inclusive login, logout, cadastro e aceite de convite
  (`GET /api/auth/csrf` → header `X-XSRF-TOKEN`).
- **Limite de tentativas:** 5 a cada 15 min por IP + e-mail no login; por IP no cadastro e no aceite de convite
  (429 com `Retry-After`). Erro de login é sempre "Credenciais inválidas". Além disso, teto de 300 requisições por
  minuto por IP em toda a `/api` (`API_MAX_REQUISICOES`).
- **Senhas:** BCrypt (custo 12), 12 a 64 caracteres.
- **Verificação em duas etapas (opcional):** aplicativo autenticador (TOTP, RFC 6238) em `/api/conta/mfa`, com 10
  códigos de recuperação de uso único. O segredo fica cifrado no banco (AES-256-GCM, chave `MFA_CHAVE` fora do
  banco); o mesmo código não vale duas vezes. Com MFA ativo, o login exige `codigoMfa` (sem ele: 401 com
  `mfaObrigatorio: true`). **Opcional no deploy:** sem `MFA_CHAVE` em produção o MFA fica desligado
  (`GET /api/conta/mfa` → `disponivel: false`) e o resto funciona.
- **Entradas:** Bean Validation em todo corpo de requisição; no domínio, todo texto livre é normalizado (NFC) e perde
  caracteres de controle e de direção do Unicode (`Validacoes`). HTML não é escapado na API: quem exibe escapa.
- **SQL:** só Spring Data (consultas derivadas e JPQL com parâmetros nomeados). O `ArquiteturaTest` reprova JDBC
  direto, `@Query` nativa e consulta montada no `EntityManager`.
- **Segredos:** só por variável de ambiente, `.env` fora do git e da imagem. No perfil `prod`, `SegredosProducao`
  avisa no log (`evento=SEGREDO_FRACO`) o segredo ausente ou com valor de desenvolvimento, sem impedir a subida.
- **Cabeçalhos:** CSP, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy`, `Permissions-Policy` e HSTS no `prod`.
  A imagem roda sem root.
- **Papéis:** plataforma `ADMIN`/`USER` e família `DONO`/`MEMBRO` (autoridades `ROLE_*`). Mudar papel ou remover
  membro encerra as sessões dele.
- **Convites:** token aleatório de 256 bits, só o SHA-256 no banco, uso único, expira em 7 dias.
- **Auditoria:** logger `auditoria` (login ok/falha/bloqueado, MFA ativado/desativado/falha, código de recuperação
  usado, sessão expirada, logout, acesso negado, família criada, convite aceito, assinatura alterada), sem senha,
  token ou e-mail digitado. O log de requisições (`LogRequisicoes`) grava método, rota, status e duração, sem a query
  string (ela leva texto digitado, como a busca de descrições).
- **Registro de acesso (Marco Civil, Art. 15):** cada login completo grava `usuario_id`, IP, porta de origem e momento
  na tabela `registro_acesso`, guardada por 6 meses. A porta vem do Caddy no header `X-Porta-Origem` (o proxy
  sobrescreve o que vier do cliente). Sem FK para `usuario`: continua depois da exclusão da conta. Não há rota na API;
  a consulta é por SQL, só com ordem judicial.
- **Limpeza diária (`LimpezaRetencao`, 3h30):** apaga o registro de acesso com mais de 6 meses e os convites expirados
  há mais de 30 dias (evento `RETENCAO_APLICADA` na auditoria, só com as contagens).
- **App mobile:** sessão pelo header `X-Auth-Token` já prevista e desligada (`SESSAO_TOKEN_HEADER`), como no
  controle-alugueis; ver `SessaoConfig`.

Rotas públicas: `GET /api/auth/csrf`, `POST /api/auth/login`, `POST /api/cadastro`, `GET /api/convites/{token}`,
`POST /api/convites/{token}/aceite`, `GET /actuator/health`. ADMIN da plataforma opcional no primeiro start
(`ADMIN_EMAIL`/`ADMIN_PASSWORD`, ver `.env.example`).

## Endpoints

| Recurso | Rotas |
|---|---|
| Autenticação | `GET /api/auth/csrf`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` |
| Cadastro e convite (públicos) | `POST /api/cadastro`, `GET /api/convites/{token}`, `POST /api/convites/{token}/aceite` |
| Minha conta | `PUT /api/conta/senha`, `PUT /api/conta/nome`, `GET /api/conta/mfa`, `POST /api/conta/mfa/configuracao`, `POST /api/conta/mfa/ativacao`, `POST /api/conta/mfa/codigos-recuperacao`, `POST /api/conta/mfa/desativacao` |
| Família | `GET/PUT /api/familia` (traz `primeiroAno`: o ano mais antigo com algum dado; `metaAportePercentual` é a meta de partida), `PUT /api/familia/estimativa-aporte` (regra da meta de aporte de cada mês), `PUT/DELETE /api/familia/metas-aporte/{AAAA-MM}` (meta do mês definida à mão / voltar à estimativa; a meta de cada mês sai em `resumo.metasAporte` da visão anual), `PUT /api/familia/membros/{id}/papel`, `DELETE /api/familia/membros/{id}`, `POST /api/familia/convites`, `DELETE /api/familia/convites/{id}` |
| Categorias | `GET /api/categorias`, `POST/PUT/DELETE /api/categorias/grupos[/{id}]`, `POST/PUT/DELETE /api/categorias[/{id}]`, `PUT /api/categorias/{id}/estimativa` (`{estimativa, desde?}`: substitui a regra própria das contas da categoria; sem `desde`, todos os meses previstos; com `desde` AAAA-MM, desse mês em diante), `POST /api/categorias/modelos/pj` |
| Lançamentos | `GET/POST /api/lancamentos` (filtros: `de`, `ate`, `categoriaId`, `grupoId`, `membroId`, `tipo`, `q`, `origem`), `POST /api/lancamentos/parcelados`, `GET/PUT/DELETE /api/lancamentos/{id}` |
| Recorrências | `GET/POST /api/recorrencias`, `GET /api/recorrencias/pendentes`, `GET /api/recorrencias/contas-a-pagar?mes=AAAA-MM` (checklist do mês: `aPagar`, `paraConferir`, `anterioresEmAberto`), `GET/PUT/DELETE /api/recorrencias/{id}`, `POST /api/recorrencias/{id}/ocorrencias/{AAAA-MM}/confirmacao` (também mês já chegado antes do início: histórico da conta), `POST/DELETE /api/recorrencias/{id}/ocorrencias/{AAAA-MM}/pulo`, `PUT /api/recorrencias/{id}/ocorrencias/{AAAA-MM}/vencimento`, `PUT /api/recorrencias/{id}/ocorrencias/{AAAA-MM}/valor` (só o mês ou `daquiEmDiante`), `PUT /api/recorrencias/{id}/estimativa` (`{estimativa, desde?}`; a resposta traz `previsoes`, um trecho por regra do mês atual em diante) |
| Saldos | `GET /api/saldos?ano=`, `PUT/DELETE /api/saldos/{AAAA-MM}` |
| Investimentos | `GET/POST /api/investimentos`, `PUT/DELETE /api/investimentos/{id}`, `GET /api/investimentos/{id}/movimentos`, `PUT/DELETE /api/investimentos/{id}/movimentos/{AAAA-MM}`, `GET /api/investimentos/carteira?ano=` (cada investimento traz `abertura`: o primeiro saldo informado, que não conta como rendimento), `GET /api/investimentos/saldos?mes=` (saldos do mês e quantos estão atualizados; sem `mes`, o de referência, o anterior ao atual), `PUT /api/investimentos/saldos/{AAAA-MM}` (`{saldos: [{investimentoId, saldo, aporte?}]}`, todos de uma vez; substitui o registro do mês, então mande o `aporte` que já existia), `POST /api/investimentos/{id}/encerramento` (`{mes}`: resgatou tudo, zera dali em diante) |
| Visão anual | `GET /api/visao-anual`, `GET /api/visao-anual/{ano}` (categorias e itens trazem `previsoes`: como são previstos do mês atual em diante; `ano` até o atual + 2, `VisaoAnual.ANOS_DE_PREVISAO`, senão 422 — a mesma trava vale para confirmar, pular e ajustar ocorrências; e a partir do `primeiroAno` da família, senão 422), `POST /api/impacto` |
| Administração (ADMIN) | `GET /api/admin/familias`, `PUT /api/admin/familias/{id}/assinatura` |

Detalhes, regras e códigos de erro de cada rota no Swagger.

## Produção

Mesmo VPS do controle-alugueis, mesma receita: `infra/deploy/compose.prod.yaml` (app + Postgres próprio, sem portas
publicadas, usuários `familianca_ddl`/`familianca_app` criados por `postgres-init/`) atrás do Caddy compartilhado
(rede `web`). O site do Caddy está em `infra/proxy/familianca.caddy`, para copiar para o proxy do VPS (ele envia a
porta de origem do cliente em `X-Porta-Origem`, usada no registro de acesso; recopie quando mudar). O guia passo a
passo do deploy ainda será escrito (ver pendências no `projeto.md`).

## Migrations e rollback

Schema só pelo Flyway (`db/migration`, `ddl-auto: validate`); o Flyway usa o `familianca_ddl` e a aplicação o
`familianca_app`, sem permissão de DDL. Migration publicada não se edita: correção é uma migration nova.

O Flyway gratuito não desfaz migrations, então o rollback é assim:

1. **Antes de cada deploy:** backup (`infra/deploy/backup-prod.sh`) e imagem com tag nova
   (`APP_VERSAO=AAAAMMDD-HHMM docker compose -f compose.prod.yaml up -d --build`). Anote a tag anterior.
2. **Só o código deu problema** (migration compatível): volte a imagem, `APP_VERSAO=<tag anterior> docker compose
   -f compose.prod.yaml up -d`. Por isso as migrations seguem *expand/contract*: coluna/tabela nova num deploy,
   remoção do que ficou velho só num deploy seguinte, quando a versão anterior já não precisa dela.
3. **A migration estragou dados ou é incompatível:** pare o app, restaure o backup do passo 1 e suba a tag anterior.

## Backup

```bash
bash scripts/backup.sh                                   # dentro do devcontainer → ./backups
bash scripts/restore.sh backups/familianca-AAAAMMDD-HHMMSS.dump
```

Em produção: `infra/deploy/backup-prod.sh` (mesmo esquema do controle-alugueis, com cópia criptografada fora do VPS,
no Cloudflare R2 enquanto estivermos no VPS). Retenção: 3 cópias no VPS (`BACKUP_MANTER`) e 30 dias no remoto
(`BACKUP_REMOTO_DIAS`, LGPD); o remoto só é podado depois que a cópia do dia deu certo.

## Dados pessoais

Controladora (LGPD): CSS Consultoria em Tecnologia da Informação Ltda., CNPJ 42.694.582/0001-01. Mapa de dados,
bases legais, lacunas e o rascunho da política de privacidade ficam em `.lgpd/` (comece pelo `STATUS.md`).

A planilha de referência (`projeto/*.xlsx`) tem dados pessoais e **nunca** entra no git (`.gitignore` bloqueia
`*.xlsx`) nem na imagem Docker (`.dockerignore`).
