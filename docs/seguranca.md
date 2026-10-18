# Segurança — avaliação e plano

Avaliação do backend contra os requisitos mínimos de segurança (2026-10-17), o que entrou na primeira versão e o
plano para o que falta. O resumo do que já está implementado também fica no README, seção "Segurança".

Legenda: ✅ atendido · 🟡 atendido em parte (falta algo listado no plano) · 🔒 depende de infraestrutura que ainda
não temos.

## Checklist para cada desenvolvimento

Regra do projeto: toda mudança passa por esta lista e aplica os itens pertinentes. O que não puder ser feito agora
entra no plano no fim deste arquivo.

| # | Requisito | Ao desenvolver |
|---|---|---|
| 1 | HTTPS | Nenhuma integração ou link gerado por `http://` em produção (ex.: `CONVITE_URL_BASE`). Cookie novo: `Secure` por perfil, `HttpOnly` e `SameSite=Strict`, quando possível. |
| 2 | Senhas e tokens | Senha só pelo `PasswordEncoder` (BCrypt), nunca em log, resposta ou sessão. Token novo (reset de senha, confirmação de e-mail): 256 bits do `SecureRandom`, só o SHA-256 no banco, uso único e com expiração (modelo: `TokensConvite`). |
| 3 | MFA | Fluxo novo de login passa por `VerificarSegundoFator`. Ação sensível (trocar e-mail, desativar MFA, ação de ADMIN) pede a senha ou um código de novo. |
| 4 | Rate limit | Rota pública nova, ou rota que confere um segredo (senha, código, token), passa pelo `LimiteTentativasLogin` e responde 429 com `Retry-After`. |
| 5 | Validação | Todo `@RequestBody` com `@Valid`, e todo campo com limite (`@Size`, `@Min/@Max`, `@DecimalMin/Max`, `@Email`). Parâmetro de query ou path com faixa. A regra de negócio é validada de novo no domínio. |
| 6 | Sanitização | Texto livre passa por `Validacoes.obrigatorio/opcional`. Front: nunca `v-html` com dado do usuário. Exportação CSV/planilha neutraliza fórmulas (`=`, `+`, `-`, `@`). Log sem senha, token, e-mail digitado nem dado pessoal (`toString` dos DTOs sensíveis devolve vazio). |
| 7 | SQL injection | Só Spring Data (consulta derivada ou JPQL com `:parametro`). Nada de SQL concatenado; o `ArquiteturaTest` barra. |
| 8 | Migrations | Mudança de schema só por migration Flyway nova; migration publicada não se edita. Tabela de dado da família tem `familia_id`, FK e índice. |
| 9 | Rollback | Migrations *expand/contract*: a versão anterior do app tem que funcionar com o schema novo. Remover coluna ou tabela só num deploy seguinte. |
| 10 | Controle de acesso | Rota nova já nasce autenticada (negar por padrão); pública só com motivo, listada no `SecurityConfig`. `@PreAuthorize` no serviço, filtro por `UsuarioAtual.familiaId()` em toda consulta, registro de outra família = 404, recurso pago com `AcessoFamilia.exigir`. Testes de isolamento entre famílias e do papel MEMBRO. |
| 11 | Sessão | Mudou papel, senha, família ou fator de acesso: encerrar sessões (`SessoesUsuario`). Dado guardado na sessão que pode mudar precisa de regra para derrubá-la. |
| 12 | Secrets | Nenhum segredo no código ou no git. Variável nova no `.env.example` e no `infra/deploy/.env.example`; valor de desenvolvimento só no `application.yaml`, nunca valendo no `prod`. Se for segredo, entra no `SegredosProducao`. |
| 13 | CORS | Não abrir origem, método ou header além do necessário; header novo do front entra explicitamente na lista. |
| — | Auditoria e testes | Evento de segurança (login, mudança de acesso, uso de código) vai para o logger `auditoria`, sem dado pessoal. Cada regra de segurança nova tem teste. Item do plano concluído: atualizar este arquivo. |

## Situação por requisito

| # | Requisito | Situação | Como está hoje |
|---|---|---|---|
| 1 | HTTPS | ✅ | O Caddy termina o TLS (certificado automático) e é o único caminho até o app, que não publica porta. No `prod`: HSTS de 1 ano, cookies `Secure` e `forward-headers-strategy` para o app saber que a requisição veio por HTTPS. |
| 2 | Senhas com hash | ✅ | BCrypt custo 12, senha de 12 a 64 caracteres, hash nunca vai para a sessão nem para a resposta. Mesma resposta para usuário inexistente e senha errada. |
| 3 | MFA | 🟡 | **Novo:** verificação em duas etapas opcional por aplicativo autenticador (TOTP), com códigos de recuperação. Só liga quando o ambiente tem `MFA_CHAVE`; sem ela, fica desligada e não bloqueia o deploy. **Front pronto** (QR gerado no navegador, códigos de recuperação, segundo passo do login). Falta: definir a chave em produção e obrigatoriedade para ADMIN. |
| 4 | Rate limit | 🟡 | Login (5 a cada 15 min por IP + e-mail), cadastro, aceite de convite, troca de senha e MFA. **Novo:** teto de 300 req/min por IP em toda a `/api`. Falta: contador fora da memória para mais de uma instância (🔒) e um limite por IP no login que valha para todos os e-mails. |
| 5 | Validação de inputs | ✅ | Bean Validation em todos os corpos de requisição (tamanho, formato, faixa) e regras de novo no domínio. Ajuste pequeno no plano: faixa para os parâmetros `ano`. |
| 6 | Sanitização de dados | ✅ | **Novo:** todo texto livre passa por `Validacoes.limpar` (normalização NFC, sem caracteres de controle nem de direção do Unicode). HTML não é escapado na API (o front escapa), e a CSP bloqueia script de outra origem e inline. |
| 7 | SQL Injection | ✅ | Só Spring Data (consultas derivadas e JPQL com parâmetros nomeados). **Novo:** o `ArquiteturaTest` reprova JDBC direto, `@Query` nativa e consulta montada no `EntityManager`. |
| 8 | Migrations | ✅ | Flyway, `ddl-auto: validate`, `SchemaTest`. Em produção o Flyway usa `familianca_ddl` e o app usa `familianca_app`, que não tem permissão de DDL. |
| 9 | Rollback | 🟡 | **Novo:** procedimento escrito (README, "Migrations e rollback") e imagem com tag por versão (`APP_VERSAO`). Falta: automatizar no script de deploy, junto com o guia do VPS. |
| 10 | Controle de acesso | ✅ | Tudo negado por padrão. Papéis de plataforma (ADMIN/USER) e de família (DONO/MEMBRO), `@PreAuthorize` nos serviços, isolamento por família em toda consulta (registro de outra família responde 404). Mudar papel ou remover membro derruba as sessões dele. |
| 11 | Expiração de sessão | ✅ | 30 min de inatividade. **Novo:** no máximo 12 h desde o login, mesmo em uso. O logout invalida a sessão, o login troca o id de sessão (contra session fixation) e a troca de senha ou a ativação do MFA derrubam as outras sessões. |
| 12 | Secrets | 🔒 | Só variáveis de ambiente, `.env` fora do git e da imagem, `:?` no compose de produção para as senhas do banco. **Novo:** no `prod`, aviso no log para segredo ausente, curto ou igual ao de desenvolvimento (`SegredosProducao`), **sem impedir a subida**. Falta o gerenciador de segredos e a rotação. |
| 13 | CORS | ✅ | Lista explícita de origens (`FRONTEND_ORIGIN`); vazio = só a mesma origem. Com credenciais, só os métodos e os headers usados. |

## O que entrou nesta versão

| Mudança | Onde |
|---|---|
| MFA por aplicativo autenticador (TOTP RFC 6238, tolerância de ±30 s, código não reaproveitável), 10 códigos de recuperação de uso único (só SHA-256 no banco), segredo cifrado com AES-256-GCM e chave `MFA_CHAVE` fora do banco | `domain/service/Totp`, `domain/model/Mfa`, `MfaService`, `SegundoFatorService`, `MfaController`, `V5__mfa.sql` |
| Login exige `codigoMfa` quando o MFA está ativo (401 com `mfaObrigatorio`); código errado conta no limite de tentativas e vai para a auditoria (`MFA_FALHA`) | `AuthController` |
| Duração máxima da sessão (12 h) | `DuracaoMaximaSessao` |
| Teto de requisições por IP na `/api` | `LimiteRequisicoesApi` |
| Limpeza de texto livre (controle, direção do Unicode, NFC) | `domain/Validacoes` |
| Regras de arquitetura contra SQL montado à mão | `ArquiteturaTest` |
| Aviso de segredos fracos no perfil `prod` (só log, não bloqueia) | `SegredosProducao` |
| `Permissions-Policy` | `SecurityConfig` |
| Imagem sem root e com tag por versão (rollback) | `docker/app.Dockerfile`, `infra/deploy/compose.prod.yaml` |
| Testes: vetores da RFC 6238, fluxo completo do MFA (ativação, reuso, recuperação, desativação), expiração absoluta, 429 por IP, segredos de produção, sanitização | `TotpTest`, `SegurancaApiTest`, `LimiteRequisicoesApiTest`, `SegredosProducaoTest`, `ValidacoesTest` |

**Front v1 (2026-10-17):**

| Mudança | Onde |
|---|---|
| Rotas do front liberadas uma a uma (o resto continua negado) e devolvendo só o `index.html`; teste cobre rota liberada, rota parecida não listada e POST | `SpaController`, `SecurityConfig`, `SegurancaApiTest` |
| CSRF em toda requisição que altera dados, sessão perdida (401) volta para o login, `volta=` só aceita caminho interno (sem redirecionamento aberto) | `frontend/src/api/http.ts`, `router.ts`, `EntrarView` |
| Sem `v-html`; fontes e QR code locais (nada de serviço externo; a `uri` do MFA carrega o segredo) | `frontend/src` |
| Imagem compila o front num estágio Node e não leva source maps | `docker/app.Dockerfile`, `frontend/vite.config.ts` |

**Apagar os dados da família e sugestão de descrições (2026-10-18):**

| Mudança | Onde |
|---|---|
| "Apagar todos os dados" (`POST /api/familia/dados/apagamento`): só DONO (`@PreAuthorize`), pede a senha de novo (ação sem volta), senha errada conta no limite do login (429), apaga só pela família da sessão (nunca por id vindo da requisição), exclusão em lote só com JPQL parametrizado e evento `DADOS_FAMILIA_APAGADOS` na auditoria (ids e contagens, sem dado pessoal). `ApagarDadosRequest` não expõe a senha no `toString`. No front, além da senha, digitar o nome da família | `FamiliaService`, `FamiliaController`, `DadosFamiliaPersistenceAdapter`, `FamiliaView` |
| Descrições já usadas (`GET /api/lancamentos/descricoes?q=`): categoria de outra família responde 404, consulta filtrada pela família da sessão, termo até 200 caracteres (422) e no máximo 8 resultados. A busca é JPQL com parâmetros: `unaccent` e `word_similarity` (pg_trgm) entram como funções registradas no Hibernate com modelo fixo, e `%`/`_` digitados são escapados (não viram curinga). As extensões vêm da migration V10 (trusted, criadas pelo dono do banco, sem superusuário) | `LancamentoService`, `FuncoesBusca`, `LancamentoJpaRepository`, `RecorrenciaJpaRepository`, `V10__busca_descricoes.sql` |
| Testes: senha errada, limite de tentativas, MEMBRO (403), sem login, isolamento entre famílias nas duas rotas | `ApagarDadosApiTest`, `LancamentoApiTest` |

**Vencimentos das contas (2026-10-18):**

| Mudança | Onde |
|---|---|
| Vencimento só num mês (`PUT /api/recorrencias/{id}/ocorrencias/{competencia}/vencimento`): mesma permissão das outras ações da ocorrência (MEMBRO só nas próprias), recorrência de outra família = 404, data limitada a 2 meses antes ou depois do mês (domínio), forma de pagamento e "sem vencimento" validados no domínio e no serviço (receita não aceita forma de pagamento) | `RecorrenciaService`, `Recorrencia`, `RecorrenciaController` |
| Migration V11 só aditiva (colunas com padrão, tabela filha com `on delete cascade`, como `recorrencia_excecao`). Rollback: a versão anterior funciona com o schema, mas não lê a frequência `UNICA`; só voltar de versão sem contas avulsas (anotado na migration) | `V11__vencimentos.sql` |

**Saldo do dia e saldo lugar a lugar (2026-10-18):**

| Mudança | Onde |
|---|---|
| Saldo com dia (`dataReferencia`) e conta do saldo de um dia para o do dia 1º (`GET /api/saldos/movimento`): só DONO, dia dentro do mês e não futuro (422), soma só os lançamentos da família da sessão | `SaldoService`, `SaldoMes`, `MovimentoDoMes`, `SaldoController` |
| Lugares do saldo (`/api/locais-saldo`): consultar qualquer membro, alterar só DONO; lugar e dono (membro) de outra família = 404; nome obrigatório, até 80 caracteres e sem repetir na família (409); tipo fechado; até 50 lugares por saldo, cada um uma vez; lugar já usado não é excluído (409, desativar) | `LocalSaldoService`, `LocalSaldoController`, `SaldoService`, `SaldoRequest` |
| Migrations V12 e V13 só aditivas (coluna nula; tabelas novas, valores do mês com `on delete cascade` no saldo). "Apagar todos os dados" também apaga os lugares e seus valores. Rollback anotado nas migrations | `V12__saldo_do_dia.sql`, `V13__locais_saldo.sql`, `DadosFamiliaPersistenceAdapter` |
| Testes: MEMBRO (403) ao alterar, isolamento entre famílias, apagar dados com lugares | `SaldoApiTest`, `ApagarDadosApiTest` |
| Saldo inicial obrigatório: a regra fica no servidor (`AcessoFamilia.exigirSaldoInicial` em lançar, parcelar, criar recorrência e confirmar), não só no front; checada depois da validação de categoria e membro, para recurso de outra família seguir respondendo 404 | `LancamentoService`, `RecorrenciaService`, `SaldoService` |
| Saldo de conferência e mês do saldo: regras no servidor, não só no front. Conferência só junto do saldo do dia 1º, dentro do mês e não no futuro; mês que já passou só aceita o dia 1º (dia já guardado se mantém); saldo inicial de mês futuro recusado (422). Migration V14 só aditiva (colunas nulas com check), rollback anotado | `SaldoMes`, `SaldoService`, `SaldoRequest`, `V14__saldo_conferencia.sql`, `SaldoApiTest` |

**MFA e segredos não bloqueiam a primeira versão.** Sem `MFA_CHAVE`, a aplicação sobe com o MFA desligado
(`GET /api/conta/mfa` → `disponivel: false`, e configurar responde 422). Os segredos fracos só geram aviso no log.
Para ligar o MFA depois: gerar a chave (`openssl rand -hex 32`), colocar no `.env` do VPS, guardar junto com as
senhas do banco e reiniciar o app. Depois de ligada, não troque nem apague a chave: quem tem MFA ativo deixa de
conseguir entrar (falha fechada) até a chave voltar.

## Plano para o que falta

### Fase 1 — antes do primeiro deploy (não precisa de infra nova)

| Item | Requisito | O que fazer |
|---|---|---|
| Ligar o MFA em produção | 3 | Gerar a `MFA_CHAVE` e colocá-la no `.env` do VPS (pode ser depois da primeira subida). |
| Script de deploy com rollback | 9 | No guia `deploy_vps.md` (já pendente), criar um `infra/deploy/deploy.sh` que faz backup, gera a tag `AAAAMMDD-HHMM`, sobe, confere o `/actuator/health` e, se falhar, volta para a tag anterior. Guardar as 3 últimas imagens. |
| Teste de restore | 9 | Restaurar um backup de produção numa base de teste uma vez por mês (o controle-alugueis já tem o roteiro). |
| Conferir o TLS | 1 | Depois do deploy, rodar o SSL Labs no domínio (nota A ou mais) e conferir o redirecionamento de HTTP para HTTPS. Pedir o HSTS preload só quando o domínio estiver estável. |
| ~~Tela do MFA no front~~ | 3 | ✅ Feito (2026-10-17): QR com a biblioteca `qrcode` no navegador, códigos de recuperação com "copiar" e segundo passo do login quando vem `mfaObrigatorio` (`ContaView`, `EntrarView`). |
| Regras do front | 6 | Seguidas na v1: nenhum `v-html`, CSP sem mudança (sem `unsafe-eval`, fontes servidas pelo app). Manter em toda tela nova. |
| Faixa nos parâmetros | 5 | `ano` entre 2000 e 2100 e `ordem` ≥ 0 (`@Validated` no controller + `@Min/@Max`). |
| Verificação de dependências | — | OWASP `dependency-check-maven` (ou Trivy na imagem) rodando no build e gitleaks no pre-commit para pegar segredo antes do commit. Quando houver repositório remoto: Dependabot e CI rodando `./mvnw test`. |

### Fase 2 — antes de abrir para outras famílias (assinatura)

| Item | Requisito | O que fazer |
|---|---|---|
| MFA obrigatório para ADMIN | 3, 10 | O login com MFA ganha a autoridade `FATOR_MFA`; `/api/admin/**` passa a exigir `ROLE_ADMIN` **e** `FATOR_MFA` (o ADMIN ainda entra para ativar o MFA, mas não administra antes disso). |
| E-mail transacional | 3, 11 | Precisa de um provedor de e-mail (🔒). Com ele: confirmar o e-mail no cadastro, "esqueci minha senha" (token de uso único, como o convite) e aviso de "MFA ativado/desativado" e "senha trocada". |
| Reset de MFA pelo suporte | 3 | Endpoint de ADMIN para apagar o MFA de um usuário que perdeu celular e códigos, com registro na auditoria e confirmação por e-mail. |
| Limite de login por IP | 4 | Segundo bucket por IP (ex.: 30 tentativas a cada 15 min, para qualquer e-mail), contra quem testa muitas contas a partir do mesmo IP (credential stuffing). |
| Sessões ativas | 11 | Listar as sessões do usuário (Spring Session já indexa por usuário) e oferecer "sair de todos os dispositivos". |
| Senhas vazadas | 2 | Recusar senha que aparece em vazamentos (Have I Been Pwned por k-anonymity: só os 5 primeiros caracteres do SHA-1 saem do servidor). |
| Algoritmo de hash migrável | 2 | Trocar para `DelegatingPasswordEncoder` (prefixo `{bcrypt}`) para poder adotar Argon2 depois, re-hashando no login. |
| LGPD | — | Exportar os dados da família e excluir a conta (o orçamento já pode ser apagado em "Recomeçar do zero"; falta excluir pessoas, família e contas, e confirmar por e-mail). |

### Fase 3 — depende de infraestrutura 🔒

| Item | Requisito | O que fazer |
|---|---|---|
| Segredos obrigatórios | 12 | Com o gerenciador no lugar, `SegredosProducao` volta a impedir a subida com segredo fraco, em vez de só avisar. |
| Gerenciador de segredos | 12 | Tirar os segredos do `.env` em texto puro. Passo intermediário sem serviço externo: Docker secrets por arquivo (`/run/secrets`, permissão 0400), lidos pelo Spring com `spring.config.import=optional:configtree:/run/secrets/` e pelo Postgres com `POSTGRES_PASSWORD_FILE`. Assim os segredos deixam de aparecer no `docker inspect`. Depois: Infisical, Doppler, Vault ou SOPS+age com o arquivo cifrado no git. |
| Rotação de segredos | 12 | Senhas do banco a cada 6–12 meses (`alter role ... password`). Para a `MFA_CHAVE`, aceitar chave atual + anterior e re-cifrar os segredos no login, para trocar a chave sem quebrar quem já usa MFA. |
| Rate limit compartilhado | 4 | Com mais de uma instância do app: buckets no Redis (bucket4j-redis) ou no Postgres. Na borda: rate limit no proxy ou um WAF (ex.: Cloudflare na frente do VPS). |
| Defesa em profundidade no banco | 7, 10 | Row-Level Security no Postgres por `familia_id` (o app informa a família da sessão com `set_config` na transação). Um bug de consulta deixa de vazar dados de outra família. |
| Logs e alertas | — | Logs estruturados em JSON no `prod` (`logging.structured.format.console`, que também evita injeção de linhas no log), envio para um coletor e alertas para picos de `LOGIN_FALHA`, `LOGIN_BLOQUEADO` e `MFA_FALHA`. |
