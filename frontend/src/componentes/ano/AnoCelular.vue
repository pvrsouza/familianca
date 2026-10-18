<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronRight } from 'lucide-vue-next'
import type { CategoriaVisao, GrupoVisao, Tipo, VisaoAnual } from '../../api/tipos'
import { MESES, MESES_CURTOS, ordinal, pct, reais } from '../../util/formato'

// No celular a tabela larga vira o mês escolhido: as 12 barras da copa são o seletor de mês.
const props = defineProps<{ visao: VisaoAnual; mesAtual: number | null }>()
const emit = defineEmits<{ abrir: [categoria: CategoriaVisao, tipo: Tipo, mes: number] }>()
const mes = ref((props.mesAtual ?? 1) - 1)
const abertos = ref(new Set<number>())

function somar(listas: number[][]): number[] {
  return Array.from({ length: 12 }, (_, i) => listas.reduce((s, l) => s + (l[i] ?? 0), 0))
}

const realizada = computed(() => somar(props.visao.despesas.grupos.map((g) => g.realizado)))
const prevista = computed(() => somar(props.visao.despesas.grupos.map((g) => g.previsto)))
const renda = computed(() => props.visao.receitas.total.valores)
const escala = computed(() => Math.max(1, ...renda.value, ...props.visao.despesas.total.valores))

const rendaMes = computed(() => props.visao.receitas.total.valores[mes.value])
const despesaMes = computed(() => props.visao.despesas.total.valores[mes.value])
const sobraMes = computed(() => rendaMes.value - despesaMes.value)

const blocos = computed(() => [
  { titulo: 'Receitas', grupos: props.visao.receitas.grupos, receita: true },
  { titulo: 'Despesas', grupos: props.visao.despesas.grupos, receita: false },
])

function gruposComValor(grupos: GrupoVisao[]) {
  return grupos.filter((g) => g.total.ano !== 0)
}

function alternar(id: number) {
  const s = new Set(abertos.value)
  s.has(id) ? s.delete(id) : s.add(id)
  abertos.value = s
}

function altura(v: number) {
  return `${(v / escala.value) * 100}%`
}

function escolherComTeclado(e: KeyboardEvent) {
  if (e.key === 'ArrowRight') mes.value = Math.min(11, mes.value + 1)
  else if (e.key === 'ArrowLeft') mes.value = Math.max(0, mes.value - 1)
  else return
  e.preventDefault()
  const alvo = (e.currentTarget as HTMLElement).querySelectorAll<HTMLButtonElement>('button')[mes.value]
  alvo?.focus()
}
</script>

<template>
  <div class="celular">
    <div class="copa" role="tablist" aria-label="Mês" @keydown="escolherComTeclado">
      <button v-for="(m, i) in MESES_CURTOS" :key="m" type="button" role="tab" class="mes"
              :aria-selected="i === mes" :tabindex="i === mes ? 0 : -1"
              :aria-label="MESES[i]" @click="mes = i">
        <span class="barra">
          <span class="parte previsto" :style="{ height: altura(prevista[i]) }" />
          <span class="parte realizado" :style="{ height: altura(realizada[i]) }" />
          <span v-if="renda[i] > 0" class="traco" :style="{ bottom: altura(renda[i]) }" />
        </span>
        <span class="rotulo" :class="{ hoje: mesAtual === i + 1 }">{{ m.charAt(0) }}</span>
      </button>
    </div>

    <section class="mes-resumo painel" :aria-label="`Resumo de ${MESES[mes]}`">
      <h2>{{ MESES[mes] }}</h2>
      <dl>
        <div><dt>Renda</dt><dd class="num receita">{{ reais(rendaMes) }}</dd></div>
        <div><dt>Despesas</dt><dd class="num">{{ reais(despesaMes) }}</dd></div>
        <div class="sobra">
          <dt>{{ sobraMes < 0 ? 'Faltou' : 'Sobra' }}</dt>
          <dd class="num" :class="{ negativo: sobraMes < 0 }">{{ reais(Math.abs(sobraMes)) }}</dd>
        </div>
      </dl>
      <p v-if="prevista[mes] > 0.004" class="nota">
        <span class="selo selo-previsto">{{ reais(prevista[mes]) }} previsto</span> ainda não confirmado
      </p>
    </section>

    <section v-for="b in blocos" :key="b.titulo" class="bloco">
      <h3>{{ b.titulo }}</h3>
      <p v-if="gruposComValor(b.grupos).length === 0" class="texto-2 vazio">Nada no ano ainda.</p>
      <ul class="lista painel">
        <li v-for="g in gruposComValor(b.grupos)" :key="g.id" class="grupo">
          <button type="button" class="linha" :aria-expanded="abertos.has(g.id)" @click="alternar(g.id)">
            <ChevronRight class="seta" aria-hidden="true" />
            <span class="nome">{{ g.nome }}</span>
            <span class="valores">
              <span class="num valor" :class="{ prev: g.previsto[mes] > 0.004 }">{{ reais(g.total.valores[mes]) }}</span>
              <span class="num ano">{{ reais(g.total.ano) }} no ano · {{ pct(g.participacao) }}</span>
            </span>
          </button>
          <ul v-if="abertos.has(g.id)" class="categorias">
            <li v-for="c in g.categorias.filter((c) => c.total.ano !== 0)" :key="c.id">
              <button type="button" class="categoria" :disabled="c.calculada"
                      @click="emit('abrir', c, b.receita ? 'RECEITA' : 'DESPESA', mes + 1)">
                <span class="nome">{{ c.nome }}</span>
                <span class="valores">
                  <span class="num valor" :class="{ prev: c.previsto[mes] > 0.004 }">{{ reais(c.total.valores[mes]) }}</span>
                  <span class="num ano">{{ reais(c.total.ano) }} no ano<template v-if="c.rank"> · {{ ordinal(c.rank) }} do ano</template></span>
                </span>
                <ChevronRight v-if="!c.calculada" class="seta-item" aria-hidden="true" />
              </button>
            </li>
          </ul>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.copa {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 2px;
  padding: var(--e-3) var(--e-2) var(--e-1);
  border-radius: var(--raio-g);
  background: var(--papel);
  border: 1px solid var(--linha);
}

.mes {
  display: grid;
  justify-items: center;
  gap: var(--e-1);
  padding: var(--e-1) 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  font: inherit;
  cursor: pointer;
}

.mes[aria-selected='true'] {
  background: var(--petroleo-tinta);
}

.barra {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  width: 0.875rem;
  height: 4rem;
}

.parte {
  display: block;
  width: 100%;
}

.parte.realizado {
  background: var(--petroleo);
  border-radius: 0 0 2px 2px;
  transition: background-color 400ms var(--ease);
}

.parte {
  transition: height 420ms var(--ease);
}

.parte.previsto {
  background: var(--coral-claro);
  outline: 1px dashed var(--coral-borda);
  outline-offset: -1px;
  border-radius: 3px 3px 0 0;
}

.traco {
  position: absolute;
  left: -3px;
  right: -3px;
  height: 2px;
  background: var(--verde);
  border-radius: 1px;
}

.rotulo {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--texto-2);
  text-transform: uppercase;
}

.mes[aria-selected='true'] .rotulo {
  color: var(--petroleo-escuro);
  font-weight: 800;
}

.mes[aria-selected='true'] {
  box-shadow: inset 0 -3px 0 var(--petroleo);
}

.rotulo.hoje {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.mes-resumo {
  margin-top: var(--e-4);
  padding: var(--e-4);
}

.mes-resumo h2 {
  font-family: var(--fonte-titulo);
  font-weight: 500;
  font-size: var(--t-xl);
  text-transform: capitalize;
  margin-bottom: var(--e-3);
}

dl {
  display: grid;
  gap: var(--e-2);
  margin: 0;
}

dl div {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

dt {
  color: var(--texto-2);
}

dd {
  margin: 0;
  font-weight: 600;
}

.sobra {
  padding-top: var(--e-2);
  border-top: 1px solid var(--linha);
}

.sobra dd {
  font-weight: 700;
  font-size: var(--t-lg);
}

.negativo {
  color: var(--erro);
}

.nota {
  margin-top: var(--e-3);
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.bloco {
  margin-top: var(--e-6);
}

.bloco h3 {
  margin-bottom: var(--e-2);
  font-size: var(--t-xs);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--texto-2);
}

.vazio {
  font-size: var(--t-sm);
}

.grupo {
  display: block !important;
  padding: 0 !important;
}

.linha {
  display: flex;
  align-items: center;
  gap: var(--e-2);
  width: 100%;
  min-height: 3.5rem;
  padding: var(--e-2) var(--e-4) var(--e-2) var(--e-3);
  border: 0;
  background: transparent;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.seta {
  width: 1rem;
  height: 1rem;
  flex: none;
  color: var(--texto-2);
  transition: transform 180ms var(--ease);
}

.linha[aria-expanded='true'] .seta {
  transform: rotate(90deg);
}

.nome {
  flex: 1;
  min-width: 0;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.valores {
  display: grid;
  justify-items: end;
  text-align: right;
}

.valor {
  font-weight: 700;
}

.valor.prev {
  padding: 0 var(--e-1);
  border-radius: 4px;
  background: var(--coral-claro);
  outline: 1px dashed var(--coral-borda);
  outline-offset: -1px;
}

.ano {
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.categorias {
  list-style: none;
  margin: 0;
  padding: 0 var(--e-4) var(--e-2) 2.25rem;
}

.categorias li {
  border-top: 1px dashed var(--linha);
}

.categoria {
  display: flex;
  align-items: center;
  gap: var(--e-2);
  width: 100%;
  min-height: 3rem;
  padding: var(--e-1) 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.categoria:disabled {
  cursor: default;
}

.seta-item {
  width: 1rem;
  height: 1rem;
  flex: none;
  color: var(--texto-2);
}

.categorias .nome {
  font-weight: 500;
  color: var(--texto);
  text-decoration: none;
}

.categorias .valor {
  font-weight: 600;
}
</style>
