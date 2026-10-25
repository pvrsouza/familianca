# Mapa de dados — Familiança

**Versão**: v1 · **Data**: 2026-10-25 · **Controlador**: CSS Consultoria em TI Ltda., CNPJ 42.694.582/0001-01 · **Canal**: privacidade@familianca.com.br

Montado a partir das migrations (`src/main/resources/db/migration`), dos controllers, do log de `auditoria`, do
`LogRequisicoes`, do `localStorage` do front e de `infra/deploy`. Base legal de cada atividade em
[`legal-basis.md`](legal-basis.md).

**Resumo**: 8 atividades · 0 com dado sensível por finalidade (A03 pode revelar por inferência) · 0 voltadas a
menores · 0 de alto risco (Res. CD/ANPD 2/2022, Art. 4º: não há larga escala, dado sensível, decisão automatizada
nem tecnologia emergente) · nenhum SDK de terceiro no app (sem analytics, Sentry ou fontes externas).

| Campo | Valor comum a todas |
|---|---|
| Sistemas | Postgres 16 no VPS (rede interna do compose), logs do Docker no VPS |
| Operadores | KingHost (VPS, validação do produto); Cloudflare R2 (backup); futuro: AWS — ver G05 |
| Transferência internacional | KingHost: não (Brasil). **Cloudflare R2: sim** (sem região no Brasil; o dump vai cifrado pelo `rclone crypt` e a Cloudflare não tem a chave). AWS: usar `sa-east-1` (São Paulo) para não haver transferência |
| Segurança | TLS (Caddy, HSTS), BCrypt 12, MFA TOTP opcional, isolamento por `familia_id`, papéis, sessão 30 min/12 h, rate limit, backup cifrado, app sem permissão de DDL ([`docs/seguranca.md`](../docs/seguranca.md)) |
| Alto risco / RIPD | Não / N/A |

### A01 — Conta e autenticação

| Campo | Valor |
|---|---|
| Finalidade | Login, MFA, troca de senha |
| Base legal | Art. 7º, V ([detalhes](legal-basis.md#a01)) |
| Titulares | Usuários adultos (cadastro e convite) |
| Dados | nome, e-mail, `senha_hash`, segredo TOTP (AES-256-GCM), hash dos códigos de recuperação |
| Fonte | Titular, no cadastro / aceite do convite |
| Sistemas | `usuario`, `mfa`, `mfa_codigo_recuperacao` |
| Retenção | Com a conta; exclusão da conta pendente (G02) |

### A02 — Família e convites

| Campo | Valor |
|---|---|
| Finalidade | Formar o grupo e dar acesso |
| Base legal | Art. 7º, V (membros); Art. 7º, IX (e-mail de convidado que ainda não aceitou) |
| Titulares | Membros; **terceiros convidados** (ainda não usuários) |
| Dados | vínculo e papel; e-mail do convidado, quem convidou, validade |
| Fonte | DONO informa o e-mail do convidado |
| Sistemas | `familia`, `membro`, `convite` |
| Retenção | Apagado 30 dias depois de expirar, em qualquer situação (limpeza diária, desde 2026-10-25) |

### A03 — Orçamento da família

| Campo | Valor |
|---|---|
| Finalidade | O serviço: lançar, prever, consolidar e ver o ano |
| Base legal | Art. 7º, V |
| Titulares | Membros; terceiros citados em descrições livres (ex.: "aluguel do João"); possivelmente **dependentes menores** citados (mesada, escola) — não são usuários |
| Dados | valores, datas, categorias, descrição livre, membro que lançou, notas fiscais PJ, saldos por lugar, investimentos, metas |
| Sensível por inferência | Possível (saúde, religião, sindicato) na descrição livre; sem uso além de mostrar à família |
| Fonte | Membros da família |
| Sistemas | `lancamento`, `recorrencia*`, `categoria`, `grupo_categoria`, `saldo_mes*`, `local_saldo`, `investimento`, `movimento_investimento`, `familia_meta_aporte` |
| Retenção | Com a família; o DONO já apaga tudo em "Apagar todos os dados" (com senha); fica nos backups até 30 dias |

### A04 — Segurança, sessão e logs

| Campo | Valor |
|---|---|
| Finalidade | Sessão, limite de tentativas, investigação de abuso |
| Base legal | Art. 7º, IX (e V para a sessão) |
| Titulares | Usuários e visitantes que tentam entrar |
| Dados | id de sessão, id do usuário, IP, porta de origem (registro de acesso), rota, horário; o log de requisições não leva query string desde 2026-10-25 |
| Fonte | Observado |
| Sistemas | `spring_session*`, `registro_acesso` (Marco Civil), memória (rate limit), logs Docker (`json-file`, 5 × 10 MB), log de acesso do Caddy (**a conferir**) |
| Retenção | Sessão 30 min/12 h; `registro_acesso` 6 meses (limpeza diária); logs do Docker por tamanho (G07) |

### A05 — Assinatura e plano

| Campo | Valor |
|---|---|
| Finalidade | Plano, teste e liberação de recursos |
| Base legal | Art. 7º, V (futuro: Art. 7º, II para dados fiscais) |
| Dados | plano, situação, validade (por família) |
| Sistemas | `familia` |
| Retenção | Com a família |

### A06 — Backups

| Campo | Valor |
|---|---|
| Finalidade | Recuperação de desastre |
| Base legal | As das atividades copiadas + Art. 46 |
| Dados | Banco inteiro (`pg_dump`) |
| Sistemas | `~/backups` no VPS (3 cópias), Cloudflare R2 cifrado pelo `rclone crypt` (`backup-cripto:familianca`) |
| Retenção | 3 no VPS; 30 dias no remoto (`rclone delete --min-age`, só depois da cópia do dia) |

### A07 — Preferências no navegador

| Campo | Valor |
|---|---|
| Finalidade | Lembrar preferências de tela |
| Base legal | Art. 7º, V (estritamente necessário) |
| Dados | `familianca.tema`, `.centavos`, `.inflacao`, `.valoresOcultos`, `.categorias-abertas`, `.guia.pulados.{familiaId}`; cookies de sessão e `XSRF-TOKEN` |
| Sistemas | Navegador do usuário |
| Retenção | Até o usuário limpar; no logout, não é limpo (avaliar a chave com id da família) |

### A08 — Suporte e administração

| Campo | Valor |
|---|---|
| Finalidade | Ajustar assinatura e suporte |
| Base legal | Art. 7º, V |
| Dados | Dados da família/conta vistos pela API de admin (**conferir o que o `AdminService` expõe**) |
| Sistemas | `/api/admin/**` |
| Retenção | Nada próprio além da auditoria |
