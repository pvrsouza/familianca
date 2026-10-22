<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { visao } from '../api/servicos'
import type { Impacto, Periodicidade } from '../api/tipos'
import { mensagemDeErro } from '../api/http'
import { PERIODICIDADES, anoAtual, pct, reais } from '../util/formato'
import CampoValor from '../componentes/CampoValor.vue'
import Mensagem from '../componentes/Mensagem.vue'

// "É só um cafezinho": quanto um gasto pequeno pesa no ano, em 10 anos e nos dias de renda da família.
const valor = ref<number | null>(15)
const periodicidade = ref<Periodicidade>('DIAS_UTEIS')
const resultado = ref<Impacto | null>(null)
const calculando = ref(false)
const erro = ref('')
let espera: ReturnType<typeof setTimeout> | undefined
let pedido = 0

const exemplos: { rotulo: string; valor: number; periodicidade: Periodicidade }[] = [
  { rotulo: 'Cafezinho no trabalho', valor: 6, periodicidade: 'DIAS_UTEIS' },
  { rotulo: 'Delivery no fim de semana', valor: 60, periodicidade: 'SEMANAL' },
  { rotulo: 'Uma assinatura de streaming', valor: 39.9, periodicidade: 'MENSAL' },
  { rotulo: 'Lanche da escola', valor: 12, periodicidade: 'DIAS_UTEIS' },
]

async function calcular() {
  erro.value = ''
  if (!valor.value || valor.value <= 0) {
    resultado.value = null
    return
  }
  const meu = ++pedido
  calculando.value = true
  try {
    const r = await visao.impacto(valor.value, periodicidade.value, anoAtual())
    if (meu === pedido) resultado.value = r
  } catch (e) {
    if (meu === pedido) erro.value = mensagemDeErro(e)
  } finally {
    if (meu === pedido) calculando.value = false
  }
}

watch([valor, periodicidade], () => {
  clearTimeout(espera)
  espera = setTimeout(calcular, 350)
}, { immediate: true })
onBeforeUnmount(() => clearTimeout(espera))

function usar(e: (typeof exemplos)[number]) {
  valor.value = e.valor
  periodicidade.value = e.periodicidade
}

const por = computed(() => PERIODICIDADES.find((p) => p.valor === periodicidade.value)?.por ?? '')
const unica = computed(() => periodicidade.value === 'UNICA')
const anos = computed(() =>
  resultado.value ? Array.from({ length: 10 }, (_, i) => resultado.value!.valorAnual * (i + 1)) : [],
)
const diasTexto = computed(() => {
  const d = resultado.value?.diasDeRenda
  if (d == null) return null
  return d.toLocaleString('pt-BR', { maximumFractionDigits: 1 })
})
</script>

<template>
  <div class="pagina">
    <header class="pagina-cabecalho">
      <div>
        <h1>Quanto pesa no ano?</h1>
        <p>Um gasto pequeno que se repete vira um número grande no ano. Coloque o valor e de quanto em quanto tempo ele acontece.</p>
      </div>
    </header>

    <div class="simulador">
      <form class="painel painel-corpo formulario entrada-sim" novalidate @submit.prevent="calcular">
        <div class="campo">
          <label for="q-valor">Valor</label>
          <CampoValor id="q-valor" v-model="valor" />
        </div>
        <fieldset class="campo">
          <legend class="rotulo">Acontece</legend>
          <div class="periodos">
            <button v-for="p in PERIODICIDADES" :key="p.valor" type="button" class="periodo"
                    :aria-pressed="periodicidade === p.valor" @click="periodicidade = p.valor as Periodicidade">
              {{ p.rotulo }}
            </button>
          </div>
        </fieldset>
      </form>
      <div class="exemplos">
        <span class="rotulo">Exemplos</span>
        <button v-for="e in exemplos" :key="e.rotulo" type="button" class="botao botao-pequeno" @click="usar(e)">
          {{ e.rotulo }}
        </button>
      </div>

      <section class="resultado" aria-live="polite" :aria-busy="calculando">
        <Mensagem v-if="erro" tipo="erro">{{ erro }}</Mensagem>
        <template v-else-if="resultado">
          <p class="de num">{{ reais(resultado.valor) }} {{ por }}</p>
          <p class="grande num">{{ reais(unica ? resultado.valor : resultado.valorAnual) }}</p>
          <p class="no-ano">{{ unica ? 'uma vez no ano' : 'no ano' }}</p>

          <dl class="detalhes">
            <div v-if="resultado.sobreRendaAnual !== null">
              <dt>da renda da família no ano</dt>
              <dd class="num">{{ pct(resultado.sobreRendaAnual) }}</dd>
            </div>
            <div v-if="diasTexto">
              <dt>dias de renda para pagar</dt>
              <dd class="num">{{ diasTexto }}</dd>
            </div>
            <div v-if="resultado.sobreDespesasAnuais !== null">
              <dt>das despesas do ano</dt>
              <dd class="num">{{ pct(resultado.sobreDespesasAnuais) }}</dd>
            </div>
          </dl>
          <p v-if="resultado.sobreRendaAnual === null" class="texto-2 sem-renda">
            Quando houver renda lançada no ano, mostramos também quanto isso representa dela.
          </p>

          <div v-if="!unica" class="dez-anos">
            <div class="dez-anos-cabecalho">
              <span>Em 5 anos <strong class="num">{{ reais(resultado.valorEm5Anos) }}</strong></span>
              <span>Em 10 anos <strong class="num">{{ reais(resultado.valorEm10Anos) }}</strong></span>
            </div>
            <div class="barras" role="img" :aria-label="`Somando ano a ano até ${reais(resultado.valorEm10Anos)} em 10 anos`">
              <span v-for="(v, i) in anos" :key="i" class="barra" :class="{ marco: i === 4 || i === 9 }"
                    :style="{ height: (v / anos[9]) * 100 + '%' }" />
            </div>
            <div class="anos-rotulo" aria-hidden="true"><span>1º ano</span><span>10º ano</span></div>
          </div>
        </template>
        <p v-else class="texto-2">Coloque um valor para ver o peso no ano.</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.simulador {
  display: grid;
  grid-template-columns: minmax(0, 26rem) minmax(0, 1fr);
  grid-template-areas: 'form resultado' 'exemplos resultado';
  gap: var(--e-5) var(--e-8);
  align-items: start;
}

.entrada-sim {
  grid-area: form;
}

.resultado {
  grid-area: resultado;
}

.exemplos {
  grid-area: exemplos;
}

fieldset {
  margin: 0;
  padding: 0;
  border: 0;
}

legend {
  margin-bottom: var(--e-2);
  font-size: var(--t-sm);
  font-weight: 600;
}

.periodos {
  display: flex;
  flex-wrap: wrap;
  gap: var(--e-2);
}

.periodo {
  min-height: 2.5rem;
  padding: 0 var(--e-3);
  border: 1px solid var(--pedra-forte);
  border-radius: 999px;
  background: var(--campo);
  color: var(--texto);
  font: inherit;
  font-size: var(--t-sm);
  font-weight: 600;
  cursor: pointer;
  transition: background-color 160ms var(--ease), border-color 160ms var(--ease), color 160ms var(--ease);
}

.periodo:hover {
  border-color: var(--petroleo);
}

.periodo[aria-pressed='true'] {
  background: var(--petroleo);
  border-color: var(--petroleo);
  color: #fff;
}

.exemplos {
  display: flex;
  flex-wrap: wrap;
  gap: var(--e-2);
  align-items: center;
}

.exemplos .rotulo {
  width: 100%;
  font-size: var(--t-sm);
  font-weight: 600;
}

.resultado {
  padding: var(--e-4) 0;
}

.de {
  font-size: var(--t-lg);
  font-weight: 600;
  color: var(--texto-2);
}

.grande {
  margin-top: var(--e-2);
  font-family: var(--fonte-titulo);
  font-weight: 500;
  font-size: clamp(2.75rem, 6vw, 4.5rem);
  line-height: 1;
  letter-spacing: -0.02em;
  color: var(--petroleo-escuro);
}

.no-ano {
  margin-top: var(--e-1);
  font-size: var(--t-xl);
  font-family: var(--fonte-titulo);
  color: var(--acao);
}

.detalhes {
  display: flex;
  flex-wrap: wrap;
  gap: var(--e-4) var(--e-10);
  margin: var(--e-8) 0 0;
  padding-top: var(--e-5);
  border-top: 1px solid var(--linha);
}

.detalhes div {
  display: grid;
}

.detalhes dd {
  order: -1;
  margin: 0;
  font-size: var(--t-2xl);
  font-weight: 700;
}

.detalhes dt {
  font-size: var(--t-sm);
  color: var(--texto-2);
}

.sem-renda {
  margin-top: var(--e-4);
  font-size: var(--t-sm);
}

.dez-anos {
  margin-top: var(--e-8);
  max-width: 34rem;
}

.dez-anos-cabecalho {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--e-2);
  font-size: var(--t-sm);
  color: var(--texto-2);
}

.dez-anos-cabecalho strong {
  margin-left: var(--e-1);
  color: var(--texto);
  font-size: var(--t-md);
}

.barras {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 6px;
  align-items: end;
  height: 7rem;
  margin-top: var(--e-3);
}

.barra {
  border-radius: 4px 4px 2px 2px;
  background: var(--petroleo-tinta-forte);
  transition: height 420ms var(--ease);
}

.barra.marco {
  background: var(--petroleo);
}

.anos-rotulo {
  display: flex;
  justify-content: space-between;
  margin-top: var(--e-1);
  font-size: var(--t-xs);
  color: var(--texto-2);
}

@media (max-width: 900px) {
  .simulador {
    grid-template-columns: 1fr;
    grid-template-areas: 'form' 'resultado' 'exemplos';
    gap: var(--e-5);
  }
}
</style>
