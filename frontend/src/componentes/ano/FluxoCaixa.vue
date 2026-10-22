<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { Pencil } from 'lucide-vue-next'
import type { LugarSaldo, Saldo, Serie, SeriePercentual, VisaoAnual } from '../../api/tipos'
import { lugaresSaldo, saldos } from '../../api/servicos'
import { sessao } from '../../estado/sessao'
import { MESES, MESES_CURTOS, competencia, pct, reais, reaisCurtos, variacao } from '../../util/formato'
import Mensagem from '../Mensagem.vue'
import PainelSaldo from './PainelSaldo.vue'

// O resumo da planilha: (1) sobra do mês anterior, (2) renda, (3) aplicações, (4) disponível = 1 + 2 − 3,
// (5) despesas e o fluxo líquido = 4 − 5. A sobra vem do saldo em conta informado ou do mês anterior.
const props = defineProps<{ visao: VisaoAnual; mesAtual: number | null }>()
const emit = defineEmits<{ mudou: [] }>()

const r = computed(() => props.visao.resumo)
const temInvestimentos = computed(() => sessao.tem('INVESTIMENTOS'))

interface Linha {
  rotulo: string
  serie: Serie
  nivel: 'forte' | 'normal' | 'sub' | 'resultado'
  receita?: boolean
  sinal?: boolean
}

const linhas = computed<Linha[]>(() => {
  const x = r.value
  const l: Linha[] = [
    { rotulo: 'Renda do mês', serie: x.rendaTotal, nivel: 'forte', receita: true },
    { rotulo: 'Receitas fixas', serie: x.receitasFixas, nivel: 'sub' },
    { rotulo: 'Receitas variáveis', serie: x.receitasVariaveis, nivel: 'sub' },
  ]
  if (temInvestimentos.value) l.push({ rotulo: 'Aplicações nos investimentos', serie: x.aplicacoes, nivel: 'normal', sinal: true })
  l.push(
    { rotulo: 'Disponível no mês', serie: x.saldoDisponivel, nivel: 'forte', sinal: true },
    { rotulo: 'Despesas do mês', serie: x.despesasTotal, nivel: 'forte' },
    { rotulo: 'Despesas fixas', serie: x.despesasFixas, nivel: 'sub' },
    { rotulo: 'Despesas variáveis', serie: x.despesasVariaveis, nivel: 'sub' },
    { rotulo: 'Fluxo do mês', serie: x.fluxoCaixaLiquido, nivel: 'resultado', sinal: true },
  )
  return l
})

const linhasInvest = computed(() =>
  temInvestimentos.value
    ? [
        { rotulo: 'Renda dos investimentos', serie: r.value.rendaInvestimentos },
        { rotulo: 'Aporte previsto (meta)', serie: r.value.aportePrevisto },
      ]
    : [],
)

const mesesNegativos = computed(() =>
  r.value.fluxoCaixaLiquido.valores
    .map((v, i) => ({ v, i }))
    .filter((x) => x.v < -0.004),
)

// No celular, a tabela abre já no mês atual.
const rolagem = ref<HTMLElement | null>(null)
onMounted(async () => {
  await nextTick()
  const el = rolagem.value
  const atual = el?.querySelector<HTMLElement>('thead th.atual')
  const primeira = el?.querySelector<HTMLElement>('thead th')
  if (!el || !atual || !primeira || el.scrollWidth <= el.clientWidth) return
  await document.fonts.ready
  el.scrollLeft = atual.getBoundingClientRect().left - el.getBoundingClientRect().left + el.scrollLeft
    - primeira.getBoundingClientRect().width - 8
})

const nomesNegativos = computed(() => mesesNegativos.value.map((x) => MESES_CURTOS[x.i]).join(', '))

function valor(v: number, sinal?: boolean): string {
  if (sinal && v < -0.004) return '−' + reaisCurtos(-v)
  return reaisCurtos(v)
}

function pcts(s: SeriePercentual) {
  return s.valores
}

// ——— Saldo em conta ———
// Informado uma vez (o saldo inicial) e corrigido quando o banco mostra outro valor; nos outros meses a sobra vem
// sozinha do mês anterior. O painel (PainelSaldo) explica as regras.
const editando = ref<number | null>(null)
const podeEditar = computed(() => sessao.dono.value)
const temSaldoInicial = computed(() => r.value.sobraInformada.some(Boolean))
/** Botão rápido: corrigir o mês atual ou, sem saldo nenhum ainda, informar o inicial. */
const mesDoBotao = computed(() => (props.mesAtual ?? 1) - 1)

// Saldos como foram informados (valor, dia e lugares), para o painel e a dica da célula.
const informados = ref<Saldo[]>([])
const lugares = ref<LugarSaldo[]>([])
watch(() => props.visao, async () => {
  try {
    ;[informados.value, lugares.value] = await Promise.all([saldos.listar(props.visao.ano), lugaresSaldo.listar()])
  } catch {
    informados.value = [] // só a dica da célula deixa de aparecer
  }
}, { immediate: true })

/** "2026-10-18" → "18/10" */
function diaMes(iso: string): string {
  const [, mes, dia] = iso.split('-')
  return `${dia}/${mes}`
}

function dicaSaldo(i: number): string | undefined {
  const s = informados.value.find((x) => x.competencia === competencia(props.visao.ano, i + 1))
  if (!s || !r.value.sobraInformada[i]) return undefined
  const partes = [s.dataReferencia
    ? `Saldo do dia 1º calculado do saldo de ${diaMes(s.dataReferencia)} (${reais(s.valor)}), desfazendo o que foi lançado de 1º até esse dia.`
    : 'Saldo do dia 1º informado.']
  if (s.conferencia) partes.push(`Saldo de ${diaMes(s.conferencia.dia)} para conferir: ${reais(s.conferencia.valor)}.`)
  if (s.porLocal.length) {
    partes.push(s.porLocal.map((p) => `${lugares.value.find((l) => l.id === p.localId)?.nome ?? 'Lugar'}: ${reais(p.valor)}`).join(' · '))
  }
  return partes.join(' ')
}
</script>

<template>
  <div class="fluxo">
    <Mensagem v-if="mesesNegativos.length" tipo="erro">
      <template v-if="mesesNegativos.length === 1">
        Em {{ MESES[mesesNegativos[0].i] }} as despesas passam do disponível em
        <strong class="num">{{ reais(-mesesNegativos[0].v) }}</strong>. Vejam juntos o que pesa mais nesse mês.
      </template>
      <template v-else>
        Em {{ mesesNegativos.length }} meses as despesas passam do disponível
        ({{ nomesNegativos }}). Vejam juntos o que pesa mais nesses meses.
      </template>
    </Mensagem>

    <div ref="rolagem" class="tabela-rolagem">
      <table class="tabela fluxo-tabela">
        <thead>
          <tr>
            <th scope="col">Valores em R$</th>
            <th v-for="(m, i) in MESES_CURTOS" :key="m" scope="col" :class="{ atual: mesAtual === i + 1 }">{{ m }}</th>
            <th scope="col">Ano</th>
          </tr>
        </thead>
        <tbody>
          <tr class="sobra">
            <th scope="row">
              Sobra do mês anterior
              <span class="ajuda">ou o saldo do dia 1º informado</span>
            </th>
            <td v-for="(v, i) in r.sobraMesAnterior.valores" :key="i" :class="{ atual: mesAtual === i + 1 }">
              <button v-if="podeEditar" type="button" class="celula-editavel" :class="{ informado: r.sobraInformada[i] }"
                      :aria-label="`Informar o saldo em conta do dia 1º de ${MESES[i]}`" :title="dicaSaldo(i)"
                      @click="editando = i">
                <span class="num">{{ valor(v, true) }}</span>
                <Pencil class="lapis" aria-hidden="true" />
              </button>
              <span v-else class="num" :class="{ informado: r.sobraInformada[i] }" :title="dicaSaldo(i)">{{ valor(v, true) }}</span>
            </td>
            <td class="num">{{ valor(r.sobraMesAnterior.ano, true) }}</td>
          </tr>
          <tr v-for="l in linhas" :key="l.rotulo" :class="l.nivel">
            <th scope="row">{{ l.rotulo }}</th>
            <td v-for="(v, i) in l.serie.valores" :key="i" class="num"
                :class="{ atual: mesAtual === i + 1, negativo: l.nivel === 'resultado' && v < -0.004, receita: l.receita }">
              {{ valor(v, l.sinal) }}
            </td>
            <td class="num" :class="{ negativo: l.nivel === 'resultado' && l.serie.ano < -0.004, receita: l.receita }">
              {{ valor(l.serie.ano, l.sinal) }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="legenda-saldo">
      <p>
        <span class="ponto" aria-hidden="true" /> Mês com saldo em conta informado, sempre o do dia 1º. Informem o saldo
        uma vez, no mês em que começaram; depois cada mês começa com a sobra do anterior.<template v-if="podeEditar">
        Se o banco mostrar outro valor, corrijam: a diferença vale daquele mês em diante.</template>
      </p>
      <button v-if="podeEditar" type="button" class="botao botao-sutil botao-pequeno" @click="editando = mesDoBotao">
        <Pencil aria-hidden="true" />
        {{ temSaldoInicial ? `Corrigir o saldo de ${MESES[mesDoBotao]}` : 'Informar o saldo inicial' }}
      </button>
    </div>

    <section class="secao">
      <div class="secao-cabecalho">
        <h2>Inflação da família</h2>
        <p>Quanto as despesas de vocês variaram de um mês para o outro, e no ano (composta).</p>
      </div>
      <div class="tabela-rolagem">
        <table class="tabela">
          <thead>
            <tr>
              <th scope="col">Variação</th>
              <th v-for="(m, i) in MESES_CURTOS" :key="m" scope="col" :class="{ atual: mesAtual === i + 1 }">{{ m }}</th>
              <th scope="col">No ano</th>
            </tr>
          </thead>
          <tbody>
            <tr class="forte">
              <th scope="row">Despesas totais</th>
              <td v-for="(v, i) in pcts(r.inflacaoFamiliar)" :key="i" class="num">{{ variacao(v) }}</td>
              <td class="num">{{ variacao(r.inflacaoFamiliar.ano) }}</td>
            </tr>
            <tr v-for="g in visao.despesas.grupos.filter((g) => g.total.ano !== 0)" :key="g.id">
              <th scope="row">{{ g.nome }}</th>
              <td v-for="(v, i) in pcts(g.inflacao)" :key="i" class="num">{{ variacao(v) }}</td>
              <td class="num">{{ variacao(g.inflacao.ano) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="legenda-saldo">Mês sem despesa fica sem variação (não conta como −100%).</p>
    </section>

    <section v-if="linhasInvest.length" class="secao">
      <div class="secao-cabecalho">
        <h2>Investimentos no fluxo</h2>
        <p>Grau de independência = renda dos investimentos ÷ despesas fixas.</p>
      </div>
      <div class="tabela-rolagem">
        <table class="tabela">
          <thead>
            <tr>
              <th scope="col">Valores em R$</th>
              <th v-for="m in MESES_CURTOS" :key="m" scope="col">{{ m }}</th>
              <th scope="col">Ano</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="l in linhasInvest" :key="l.rotulo">
              <th scope="row">{{ l.rotulo }}</th>
              <td v-for="(v, i) in l.serie.valores" :key="i" class="num">{{ reaisCurtos(v) }}</td>
              <td class="num">{{ reaisCurtos(l.serie.ano) }}</td>
            </tr>
            <tr>
              <th scope="row">Grau de independência</th>
              <td v-for="(v, i) in r.grauIndependencia.valores" :key="i" class="num">{{ pct(v) }}</td>
              <td class="num">{{ pct(r.grauIndependencia.ano) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <PainelSaldo :visao="visao" :mes="editando" :informados="informados" @fechar="editando = null" @mudou="emit('mudou')"
                 @trocar-mes="editando = $event" />
  </div>
</template>

<style scoped>
.fluxo {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--e-4);
}

.fluxo .secao {
  margin-top: var(--e-6);
}

.tabela th:first-child {
  position: sticky;
  left: 0;
  background: var(--papel);
  min-width: 11.5rem;
  font-weight: 500;
}

.tabela thead th:first-child {
  background: var(--areia-funda);
}

.tabela thead th {
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.tabela thead th.atual {
  background: var(--petroleo-tinta-forte);
  color: var(--petroleo-escuro);
}

.tabela td.atual {
  background: var(--tinta-atual);
}

.tabela td {
  height: 2.5rem;
}

.tabela th,
.tabela td {
  padding-left: var(--e-2);
  padding-right: var(--e-2);
}

.ajuda {
  display: block;
  font-size: 0.75rem;
  color: var(--texto-2);
  font-weight: 400;
}

.forte th,
.forte td {
  font-weight: 700;
}

.sub th {
  padding-left: var(--e-6);
  color: var(--texto-2);
}

.sub td {
  color: var(--texto-2);
}

.resultado th,
.resultado td {
  font-weight: 700;
  background: var(--areia);
  border-top: 2px solid var(--pedra-forte);
}

.resultado th:first-child {
  background: var(--areia);
}

.negativo {
  color: var(--erro);
}

.celula-editavel {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  width: 100%;
  min-height: 2rem;
  padding: 0 4px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
}

.celula-editavel:hover {
  border-color: var(--pedra-forte);
  background: var(--campo);
}

.lapis {
  position: absolute;
  left: 2px;
  top: 50%;
  transform: translateY(-50%);
  width: 0.75rem;
  height: 0.75rem;
  color: var(--texto-2);
  opacity: 0;
  transition: opacity 150ms var(--ease);
}

.celula-editavel:hover .lapis,
.celula-editavel:focus-visible .lapis {
  opacity: 1;
}

.informado,
.celula-editavel.informado .num {
  position: relative;
  font-weight: 700;
  color: var(--petroleo-escuro);
}

.informado::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 5px;
  border-radius: 50%;
  background: var(--petroleo);
  vertical-align: middle;
}

.legenda-saldo {
  font-size: var(--t-xs);
  color: var(--texto-2);
  margin-top: calc(var(--e-2) * -1);
}

div.legenda-saldo {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--e-2) var(--e-4);
}

div.legenda-saldo p {
  max-width: 70ch;
}

.fluxo .secao .legenda-saldo {
  margin-top: var(--e-2);
}

.ponto {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 4px;
  border-radius: 50%;
  background: var(--petroleo);
  vertical-align: middle;
}

@media (max-width: 640px) {
  .tabela th:first-child {
    min-width: 8.5rem;
    white-space: normal;
  }

  .sobra .ajuda {
    display: none;
  }
}
</style>
