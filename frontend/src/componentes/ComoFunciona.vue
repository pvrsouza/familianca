<script setup lang="ts">
// Os 5 passos do Familiança para quem chega: do saldo de hoje (o ponto de partida) até a família toda no mesmo quadro.
// Usa a mesma linguagem da copa do ano: tracejado coral = previsto, cheio = pago. "escuro": sobre o petróleo.
defineProps<{ escuro?: boolean }>()
</script>

<template>
  <section class="como" :class="{ escuro }" aria-labelledby="como-funciona-titulo">
    <h2 id="como-funciona-titulo">Como funciona, em 5 passos</h2>
    <ol>
      <li>
        <strong>Informem quanto têm hoje.</strong>
        <span>Conta, poupança, dinheiro na mão, cofrinho. É o ponto de partida: depois cada mês começa sozinho com a
          sobra do anterior.</span>
      </li>
      <li>
        <strong>Cadastrem o que se repete.</strong>
        <span>Aluguel, escola, salário: entra uma vez e já aparece
          <span class="junto"><span class="amostra prevista" aria-hidden="true" /> previsto</span> em todos os meses
          seguintes.</span>
      </li>
      <li>
        <strong>Lancem e marquem o que foi pago.</strong>
        <span>Mercado e farmácia se lançam na hora, pelo celular. Nas contas com vencimento, o previsto vira
          <span class="junto"><span class="amostra paga" aria-hidden="true" /> pago</span> e fica claro o que ainda
          falta pagar no mês.</span>
      </li>
      <li>
        <strong>Vejam o ano inteiro.</strong>
        <span>Os 12 meses lado a lado, como na planilha: quanto cada gasto pesa no ano e quanto vai sobrar.
          <span class="num">R$ 39,90 por mês = R$ 478,80 no ano.</span></span>
      </li>
      <li>
        <strong>Tragam a família.</strong>
        <span>Um convite pelo WhatsApp e todos veem o mesmo quadro. O orçamento é de todos.</span>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.como {
  --linha-passo: var(--pedra);
  --numero-fundo: var(--petroleo-tinta);
  --numero-texto: var(--petroleo-escuro);
  --apoio: var(--texto-2);
  --destaque: var(--texto);
  --amostra-prevista-fundo: var(--coral-claro);
  --amostra-prevista-borda: var(--coral-borda);
  --amostra-paga: var(--petroleo);
}

.como.escuro {
  --linha-passo: rgb(246 244 240 / 0.22);
  --numero-fundo: rgb(246 244 240 / 0.12);
  --numero-texto: var(--areia);
  --apoio: rgb(246 244 240 / 0.78);
  --destaque: var(--areia);
  --amostra-prevista-fundo: rgb(244 181 163 / 0.18);
  --amostra-prevista-borda: var(--coral-sobre-escuro);
  --amostra-paga: var(--areia);
}

h2 {
  margin-bottom: var(--e-4);
  font-family: var(--fonte-titulo);
  font-weight: 500;
  font-size: var(--t-xl);
  line-height: 1.2;
  color: var(--destaque);
}

ol {
  list-style: none;
  margin: 0;
  padding: 0;
  counter-reset: passo;
  display: grid;
}

li {
  position: relative;
  display: grid;
  gap: 0.125rem;
  padding: 0 0 var(--e-4) 2.75rem;
  counter-increment: passo;
  font-size: var(--t-sm);
  line-height: 1.45;
}

li:last-child {
  padding-bottom: 0;
}

/* O número do passo, ligado ao próximo por um fio: um caminho, não uma lista solta. */
li::before {
  content: counter(passo);
  position: absolute;
  left: 0;
  top: -0.125rem;
  display: grid;
  place-items: center;
  width: 1.875rem;
  height: 1.875rem;
  border-radius: 50%;
  background: var(--numero-fundo);
  color: var(--numero-texto);
  font-family: var(--fonte-titulo);
  font-weight: 600;
  font-size: var(--t-md);
}

li:not(:last-child)::after {
  content: '';
  position: absolute;
  left: calc(0.9375rem - 0.5px);
  top: 1.875rem;
  bottom: 0.125rem;
  width: 1px;
  background: var(--linha-passo);
}

strong {
  font-weight: 650;
  font-size: var(--t-md);
  color: var(--destaque);
}

li > span {
  max-width: 44ch;
  color: var(--apoio);
}

.num {
  display: block;
  margin-top: 0.125rem;
  font-weight: 600;
  color: var(--destaque);
}

/* A amostra não se separa da palavra que ela ilustra. */
.junto {
  white-space: nowrap;
}

/* Amostras da copa do ano, do tamanho da letra. */
.amostra {
  display: inline-block;
  width: 0.5rem;
  height: 0.8125rem;
  margin-left: 0.125rem;
  border-radius: 999px;
  vertical-align: -0.0625rem;
}

.amostra.prevista {
  background: var(--amostra-prevista-fundo);
  border: 1.5px dashed var(--amostra-prevista-borda);
}

.amostra.paga {
  background: var(--amostra-paga);
}
</style>
