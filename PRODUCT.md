# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

App mobile nativo é futuro e vai consumir o mesmo backend; não faz parte desta fase.

## Stack

SPA com Vite em `frontend/`, compilada no `docker/app.Dockerfile` e servida pelo Spring em
`src/main/resources/static`. Dev server na porta 5173 do devcontainer (`127.0.0.1:5174` no host). Framework:
**Vue.js** (Vue 3), decisão do usuário (2026-10-17). Bibliotecas complementares ainda não definidas.

Backend pronto: API REST em `/api` (Java 21, Spring Boot), sessão por cookie `SESSION` e CSRF por header
`X-XSRF-TOKEN` (`GET /api/auth/csrf`). Detalhes em `README.md`.

## Users

- **Quem organiza** (ex.: Rafael, 42, o provedor): costuma criar a conta e vira DONO. Já usa planilha e quer ver o
  ano inteiro e o peso de cada gasto sem fazer conta de cabeça. Também quer trazer a família para dentro do
  orçamento, sem fiscalizar ninguém.
- **Quem participa** (ex.: Camila, 38): entra por convite, que chega pelo WhatsApp. Quer lançar os próprios gastos
  rápido e saber onde a família está. Desiste se a ferramenta parecer vigilância.
- **Quem está aprendendo** (ex.: Lívia, 16): membro jovem. Precisa de linguagem simples, de números com contexto e
  de nada de culpa.
- Membros de 12 a 70+ anos. Brasil, classe média (B/C). Uma parte tem renda PJ.

Personas completas em `projeto/marca.md` (hipótese v1, a validar com 5 a 8 famílias).

## Product Purpose

Orçamento familiar compartilhado com **visão anual**. A família inteira vê o ano do orçamento mês a mês (realizado ×
previsto), entende quanto cada categoria, grupo e pessoa pesa no ano e decide junto. Nasceu da planilha do fundador
e começa sendo usado pela família dele. Depois vira produto por assinatura.

Sucesso: famílias ativas, **membros por família** (o sinal de que virou "da família"), conversão do teste Premium
para plano pago e retenção mês a mês.

## Positioning

O único orçamento pensado para a **unidade família** (não para uma pessoa) e para o **horizonte do ano** (não do
mês). Ganha da planilha na colaboração e dos apps de controle de gastos (Mobills, Organizze, app do banco) na
visão anual. Família, papéis, convite, visão por pessoa, previsto × realizado e inflação familiar existem desde o
primeiro dia. Comunicação: "orçamento familiar" e "o ano da família", nunca "controle de gastos".

## Operating Context

- Uso no **celular e no desktop com o mesmo cuidado**. No celular: lançar um gasto na hora (no mercado, na rua) e
  abrir o convite que chega pelo WhatsApp. No desktop: analisar o ano numa tabela larga, como na planilha. A visão
  anual tem que funcionar bem nos dois.
- O convite é um link de uso único (7 dias), copiado e enviado por WhatsApp ou e-mail. A tela do convite é o
  primeiro contato do membro com a marca, e a primeira tela do convidado importa tanto quanto a de quem criou a
  conta.
- Lançar uma despesa tem que ser mais rápido que na planilha.
- Gatilhos de uso: virada do ano, susto no cartão, mudança de vida, cansaço de manter a planilha sozinho.

## Capabilities and Constraints

**Escopo desta fase:** só o app (login, cadastro, convite, visão anual, lançamentos e o resto). A landing pública
fica para depois.

Funcionalidades do backend (regras em `projeto/projeto.md`):
- Cadastro (cria a família; quem cadastra vira DONO), login com MFA **opcional** (TOTP + códigos de recuperação),
  convite por link, papéis DONO / MEMBRO. MEMBRO lança e altera só os próprios lançamentos, mas vê o orçamento
  inteiro da família.
- Categorias: tipo (receita/despesa) → grupo (fixo/variável) → categoria; incluir, editar, mover, desativar e
  excluir (só o DONO).
- Lançamentos (com pessoa da família), compras parceladas (2 a 60 parcelas), recorrências (mensal a anual) com
  ocorrências **previstas**, que são confirmadas ou puladas, e a lista de pendentes.
- **Visão anual** (tela principal): mês a mês com total, média, participação e ranking por categoria/grupo;
  realizado × previsto; inflação por grupo e inflação familiar; fluxo de caixa no formato da planilha; visão por
  pessoa.
- **Simulação de impacto**: quanto um gasto pesa no ano, em 5 e 10 anos, em % da renda e das despesas e em dias de
  renda.
- Saldos em conta por mês; investimentos (plano Família+); despesas PJ com alíquotas (plano Premium).
- Planos: Gratuito (2 pessoas), Família (6, + investimentos), Premium (12, + PJ). Família nova ganha 30 dias de
  Premium. Recurso fora do plano responde **402**: a interface precisa mostrar o recurso indisponível sem perder
  dados. Nomes e preços dos planos são provisórios.
- Sessão: 30 min de inatividade e no máximo 12 h. Erro de login é sempre "Credenciais inválidas". Limite de
  tentativas responde 429 com `Retry-After`.

Restrições:
- Segurança: toda mudança passa pelo checklist de `docs/seguranca.md`. A API não escapa HTML, então **o front
  escapa tudo que exibe**. Respeitar a CSP do backend.
- Pensar em planos desde o início: recurso vendável separado entra como recurso de plano.
- A planilha `projeto/*.xlsx` tem dados pessoais: serve só de referência e nunca vai para o git, para a imagem nem
  para fixtures.

Ainda não existem: e-mail transacional (convite por e-mail, "esqueci minha senha"), cobrança integrada, cartões de
crédito e fatura, importação da planilha, uma pessoa em mais de uma família.

Terminologia: família, DONO, MEMBRO, convite, grupo, categoria, lançamento, parcela, recorrência, ocorrência
**prevista / confirmada / pulada**, pendentes, visão anual, participação, ranking, inflação familiar, fluxo de
caixa, sobra, simulação de impacto.

## Brand Commitments

Definidos em `projeto/marca.md` (v1, 2026-10-17) e vinculantes para o front:
- **Nome:** Familiança. **Frase:** *"Familiança: o orçamento é de todos."*
- **Ideia central:** aliança. O orçamento é um pacto da família, não o controle de uma pessoa sobre as outras.
- **Personalidade:** acolhedora, clara, honesta, parceira, serena.
- **Voz:** um amigo da família que entende de dinheiro. Fala no plural ("vocês", "a gente"), é concreta ("R$ 39,90
  por mês = R$ 478,80 no ano"), não julga e traz a notícia ruim com um caminho. O vocabulário usado e o evitado
  estão em `projeto/marca.md` §4 (evitar: controle, vigiar, cortar gastos, dependentes, estourou…).
- **Identidade visual já decidida pelo usuário** (detalhada em `projeto/marca.md` §5): cores petróleo `#1F5C66` +
  coral (o previsto e as conquistas) sobre areia clara, verde-folha só para o que entra e grafite-azulado `#1E2A2E`
  nos textos e nas despesas; tipografia Fraunces (títulos) + Figtree (interface e números tabulares); ícones de
  linha (Phosphor/Lucide); logo "a copa do ano" (árvore com 12 barras), ainda a ser finalizada por um designer.

## Evidence on Hand

- `projeto/projeto.md`: regras de negócio e decisões.
- `projeto/marca.md` e `.agents/brand-context.md`: público, posicionamento, voz e identidade.
- `projeto/Orçamento Familiar.xlsx`: planilha de referência com **dados pessoais**. Nunca copiar valores dela.
- Swagger (`/swagger-ui`) e a coleção do Bruno (`bruno/Familiança`) com todas as rotas.
- Exemplos de cálculo citados nos docs: "R$ 15,00 por dia útil = R$ 3.780,00 no ano (4,2% da renda, 15,3 dias de
  renda)".

Não existem e não podem ser inventados: depoimentos, famílias clientes, números de uso, preços finais dos planos,
o logo final e fotografia própria.

## Product Principles

1. **O ano em primeiro lugar.** A visão anual é o centro do produto e o resto leva até ela.
2. **Clareza sem culpa.** Cada número vem com o seu significado, e nenhuma cor, ícone ou texto acusa alguém.
3. **Todo mundo na mesa.** Funciona para a Lívia de 16 e para o avô de 70, e quem foi convidado é tão importante
   quanto quem criou a conta.
4. **Mais rápido que a planilha.** Lançar e confirmar o previsto custa menos esforço do que na planilha.
5. **Calor com rigor.** O calor fica na cor, na tipografia e no texto; os dados são precisos, alinhados e sem
   enfeite.

## Accessibility & Inclusion

Públicos de 12 a 70+ anos, com pessoas que não são técnicas: áreas de toque grandes, bom contraste (texto e
números legíveis por idosos) e linguagem simples. Ainda não foi escolhida uma norma formal; WCAG 2.2 AA é a
referência razoável até haver decisão.
