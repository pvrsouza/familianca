---
name: iteracao-negocio
description: Revisa o modelo de negócio do Familiança (projeto/negocio.md) à luz da entrega que acabou de ser feita ou de uma ideia nova. Use ao fim de toda entrega (ver CLAUDE.md), quando alguém pedir "pensar o negócio", "modelo de negócio", "preço", "planos", "concorrentes", "validar hipótese" ou "/iteracao-negocio".
---

# Iteração de negócio

Objetivo: cada entrega do produto também move o modelo de negócio. Ao fim, `projeto/negocio.md` diz o que a entrega
mudou nas hipóteses e qual é a próxima pergunta de negócio a responder.

## Passos

1. **Ler o contexto:** `projeto/negocio.md` (canvas, hipóteses, registro), "Planos e assinatura" e "Pendências" em
   `projeto/projeto.md`, e o que mudou na entrega (`git log` e `git diff` desde a última entrada do registro, ou o
   que foi pedido nesta sessão).
2. **Ligar a entrega às hipóteses:** para cada mudança, dizer qual hipótese ela ajuda a testar, qual bloco do canvas
   ela altera (receita, custo, canal, proposta) e se cria um recurso que deveria ser de plano pago. Se não toca em
   nada, dizer isso em uma linha e ir para o passo 5.
3. **Criticar de verdade:** usar a skill `grilling` (mattpocock-skills) sobre a hipótese mais arriscada afetada: uma
   pergunta por vez, com recomendação. Pontos fixos do contexto brasileiro: concorrência gratuita (Mobills,
   Organizze, app do banco), disposição a pagar da classe B/C, Pix e cartão recorrente, LGPD, custo de Open Finance.
4. **Pesquisar só quando faltar dado:** `anthropic-skills:deep-research` para mercado, preços de concorrentes ou
   regulação; `brand-building-skills:competitor-branding`, `target-audience` e `brand-positioning` para posicionamento
   e público. Se os plugins `venture` ou `Product Management` estiverem instalados, podem ser usados no lugar. Toda
   informação externa entra com a fonte e a data; nada de número inventado.
5. **Atualizar `projeto/negocio.md`:** nova entrada no topo do "Registro de iterações" (data, o que mudou, efeito
   nas hipóteses, decisões, perguntas abertas); status das hipóteses; canvas, se algum bloco mudou. Decisão de
   negócio que vira regra do produto também entra em `projeto/projeto.md` (planos, pendências).
6. **Entregar em poucas linhas:** o que mudou no modelo, a hipótese mais arriscada agora e a próxima ação para
   testá-la (entrevista, métrica, experimento), para quem pediu escolher.

## Fora do escopo

Não alterar código nem preços nos planos sem decisão de quem pediu. Não rodar em tarefas sem efeito no produto
(rodar testes, commit, dúvida pontual).
