# Pentest do Familiança (ambiente local de desenvolvimento)

Alvo autorizado: somente `http://dev:8080` (API Spring Boot e SPA) e o código-fonte em `./`. Não ataque
outros hosts, o Postgres diretamente nem o Docker.

## O sistema

Orçamento familiar multi-tenant: cada família vê só os próprios dados (lançamentos, recorrências, saldos,
investimentos, categorias, convites). Papéis na família: DONO e MEMBRO; papel de plataforma ADMIN
(`/api/admin/**`). Sessão por cookie com CSRF (`GET /api/auth/csrf`, header `X-XSRF-TOKEN`). MFA por TOTP opcional.
Documentação da API: `/v3/api-docs` (exige login).

## Contas de teste

O cadastro é aberto: crie você mesmo duas famílias (`GET /api/auth/csrf`, `POST /api/cadastro`,
`POST /api/auth/login`), com e-mails `@exemplo.test` e senha de 12+ caracteres. Use uma para atacar a outra.

## Prioridades

1. Acesso entre famílias (IDOR/BOLA): ler, alterar ou apagar dados de outra família trocando ids.
2. Escalada de papel: MEMBRO fazendo o que só o DONO faz; usuário comum em `/api/admin/**`.
3. Autenticação e sessão: CSRF, fixação de sessão, limite de tentativas de login, desvio do MFA, convites
   (token reutilizado, aceite por outra pessoa).
4. Importação de extrato (`/api/importacoes/analisar`, upload OFX): XXE, arquivo grande, injeção.
5. Validação de entrada, injeção (SQL, log), XSS armazenado em descrições que o front mostra.

O limite de requisições é de 300 por minuto por IP: espalhe as requisições para não ficar só com 429.
