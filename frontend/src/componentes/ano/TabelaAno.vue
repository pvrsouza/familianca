<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ChevronRight, Info } from 'lucide-vue-next'
import type { Bloco, CategoriaVisao, GrupoVisao, Tipo, VisaoAnual } from '../../api/tipos'
import { MESES, MESES_CURTOS, ordinal, pct, reais, reaisCurtos } from '../../util/formato'
import { centavos } from '../../estado/centavos'

// A planilha viva: grupos que abrem em categorias, 12 meses, total, média, % do ano e rank. Acima dos meses, a
// copa do ano: uma barra por mês com a despesa realizada (cheia), a prevista (tracejada) e o traço da renda.
const props = defineProps<{ visao: VisaoAnual; mesAtual: number | null }>()
const emit = defineEmits<{ abrir: [categoria: CategoriaVisao, tipo: Tipo, mes: number] }>()

const fechados = ref(new Set<number>())
const mostrarVazias = ref(false)
const realce = ref<number | null>(null)
const meses = MESES_CURTOS

const blocos = computed(() => [
  { chave: 'RECEITA' as const, titulo: 'Receitas', bloco: props.visao.receitas },
  { chave: 'DESPESA' as const, titulo: 'Despesas', bloco: props.visao.despesas },
])

function somar(listas: number[][]): number[] {
  return Array.from({ length: 12 }, (_, i) => listas.reduce((s, l) => s + (l[i] ?? 0), 0))
}

const despesaRealizada = computed(() => somar(props.visao.despesas.grupos.map((g) => g.realizado)))
const despesaPrevista = computed(() => somar(props.visao.despesas.grupos.map((g) => g.previsto)))
const renda = computed(() => props.visao.receitas.total.valores)
const escala = computed(() =>
  Math.max(1, ...renda.value, ...props.visao.despesas.total.valores),
)
const anoVazio = computed(() => props.visao.despesas.total.ano === 0 && props.visao.receitas.total.ano === 0)
const saldoMes = computed(() =>
  props.visao.receitas.total.valores.map((r, i) => r - props.visao.despesas.total.valores[i]),
)
const saldoAno = computed(() => props.visao.receitas.total.ano - props.visao.despesas.total.ano)

function previstoDoBloco(b: Bloco): number[] {
  return somar(b.grupos.map((g) => g.previsto))
}

function categoriasVisiveis(g: GrupoVisao): CategoriaVisao[] {
  return mostrarVazias.value ? g.categorias : g.categorias.filter((c) => c.total.ano !== 0)
}

const temOcultas = computed(() =>
  [...props.visao.receitas.grupos, ...props.visao.despesas.grupos].some((g) => g.categorias.some((c) => c.total.ano === 0)),
)

function alternar(id: number) {
  const s = new Set(fechados.value)
  s.has(id) ? s.delete(id) : s.add(id)
  fechados.value = s
}

const todosFechados = computed(() =>
  [...props.visao.receitas.grupos, ...props.visao.despesas.grupos].every((g) => fechados.value.has(g.id)),
)

function alternarTodos() {
  fechados.value = todosFechados.value
    ? new Set()
    : new Set([...props.visao.receitas.grupos, ...props.visao.despesas.grupos].map((g) => g.id))
}

function dica(total: number, previsto: number, estimado = 0): string | undefined {
  if (!total) return undefined
  if (previsto > 0.004) {
    const realizado = total - previsto
    const deRecorrencia = previsto - estimado
    const partes = [
      realizado > 0.004 ? `${reais(realizado)} confirmado` : '',
      deRecorrencia > 0.004 ? `${reais(deRecorrencia)} previsto` : '',
      estimado > 0.004 ? `${reais(estimado)} estimado pelos meses anteriores` : '',
    ].filter(Boolean)
    return partes.join(' + ')
  }
  return reais(total)
}

/** Dica da célula de categoria: separa o previsto de recorrência da estimativa. */
function dicaCategoria(c: CategoriaVisao, i: number): string | undefined {
  return dica(c.total.valores[i], c.previsto[i], c.estimado?.[i] ?? 0)
}

function classesCelula(i: number, previsto = 0) {
  return {
    atual: props.mesAtual === i + 1,
    realce: realce.value === i,
    prev: previsto > 0.004,
  }
}

function alturaBarra(v: number): string {
  return `${Math.max(0, (v / escala.value) * 100)}%`
}

function textoCopa(i: number): string {
  const prev = despesaPrevista.value[i]
  return `${MESES[i]}: despesas ${reais(despesaRealizada.value[i] + prev)}` +
    (prev > 0.004 ? `, sendo ${reais(prev)} previsto` : '') + `; renda ${reais(renda.value[i])}`
}

/** Clicar no valor abre o mês da categoria (editar, conferir ou lançar). Alíquota é calculada: não tem o que abrir. */
function clicavel(c: CategoriaVisao, i: number): boolean {
  return !c.calculada && (c.ativa || c.total.valores[i] !== 0)
}

function rotuloCelula(c: CategoriaVisao, i: number, tipo: Tipo): string {
  const v = c.total.valores[i]
  const quando = `${MESES[i]}`
  if (!v) return `${c.nome}, ${quando}: nada lançado. Lançar`
  return `${c.nome}, ${quando}: ${dicaCategoria(c, i)}. ${tipo === 'RECEITA' && c.previsto[i] > 0.004 ? 'Confirmar se chegou' : 'Ver e editar'}`
}

const nomeTipo = { RECEITA: 'receita', DESPESA: 'despesa' } as const

function textoRank(c: CategoriaVisao, tipo: Tipo): string | undefined {
  if (!c.rank) return undefined
  return `${ordinal(c.rank)} maior ${nomeTipo[tipo]} do ano`
}

/** As 3 primeiras despesas do ano, para o exemplo da explicação do rank. */
const topoDespesas = computed(() =>
  props.visao.despesas.grupos.flatMap((g) => g.categorias)
    .filter((c) => c.rank && c.rank <= 3)
    .sort((a, b) => (a.rank ?? 0) - (b.rank ?? 0)),
)

function linkCategoria(c: CategoriaVisao): string {
  return `/lancamentos?categoriaId=${c.id}&ano=${props.visao.ano}`
}
</script>

<template>
  <div class="ferramentas">
    <div class="legenda" aria-hidden="true">
      <span><i class="amostra realizado" /> confirmado</span>
      <span><i class="amostra previsto" /> previsto</span>
      <span><i class="amostra renda" /> renda do mês</span>
    </div>
    <div class="acoes">
      <label v-if="temOcultas" class="caixa-marcar">
        <input v-model="mostrarVazias" type="checkbox" /> Mostrar categorias sem valor no ano
      </label>
      <label class="caixa-marcar" title="Sem centavos, os valores aparecem arredondados para o real mais próximo">
        <input type="checkbox" :checked="centavos.ligados.value" @change="centavos.alternar()" /> Mostrar centavos
      </label>
      <button type="button" class="botao botao-sutil botao-pequeno" @click="alternarTodos">
        {{ todosFechados ? 'Abrir todos os grupos' : 'Recolher os grupos' }}
      </button>
    </div>
  </div>

  <div class="planilha" role="region" :aria-label="`Orçamento de ${visao.ano}, mês a mês`" tabindex="0"
       @mouseleave="realce = null">
    <table :class="{ 'com-centavos': centavos.ligados.value }">
      <colgroup>
        <col class="c-nome" />
        <col v-for="m in meses" :key="m" class="c-mes" />
        <col class="c-total" />
        <col class="c-media" />
        <col class="c-pct" />
        <col class="c-rank" />
      </colgroup>
      <thead>
        <tr class="copa">
          <th class="canto-copa" scope="row">
            <span>Despesas do mês</span>
          </th>
          <td v-for="(m, i) in meses" :key="m" :class="classesCelula(i)" @mouseenter="realce = i">
            <div class="barra-mes" role="img" :aria-label="textoCopa(i)" :title="textoCopa(i)">
              <span v-if="anoVazio" class="fantasma" />
              <span class="pilha">
                <span class="parte previsto" :style="{ height: alturaBarra(despesaPrevista[i]) }" />
                <span class="parte realizado" :class="{ topo: despesaPrevista[i] <= 0.004 }"
                      :style="{ height: alturaBarra(despesaRealizada[i]) }" />
              </span>
              <span v-if="renda[i] > 0" class="traco-renda" :style="{ bottom: alturaBarra(renda[i]) }" />
            </div>
          </td>
          <td colspan="4" class="copa-resumo">
            <span class="num">{{ reais(visao.despesas.total.ano) }}</span>
            <span>de despesas no ano</span>
          </td>
        </tr>
        <tr class="meses">
          <th scope="col" class="canto">{{ centavos.ligados.value ? 'Valores em R$' : 'Valores em R$, arredondados' }}</th>
          <th v-for="(m, i) in meses" :key="m" scope="col" :class="classesCelula(i)" @mouseenter="realce = i">
            <abbr :title="MESES[i]">{{ m }}</abbr>
          </th>
          <th scope="col">Total</th>
          <th scope="col">Média</th>
          <th scope="col"><abbr title="Participação no total do ano">% ano</abbr></th>
          <th scope="col" class="col-rank">
            <button type="button" class="explicar" popovertarget="explica-rank" aria-label="Rank: o que é">
              Rank <Info aria-hidden="true" />
            </button>
          </th>
        </tr>
      </thead>

      <tbody v-for="b in blocos" :key="b.chave" :class="b.chave === 'RECEITA' ? 'bloco-receita' : 'bloco-despesa'">
        <tr class="linha-tipo">
          <th scope="row">{{ b.titulo }}</th>
          <td v-for="(v, i) in b.bloco.total.valores" :key="i" class="num" :class="classesCelula(i, previstoDoBloco(b.bloco)[i])"
              :title="dica(v, previstoDoBloco(b.bloco)[i])" @mouseenter="realce = i">{{ reaisCurtos(v) }}</td>
          <td class="num total">{{ reaisCurtos(b.bloco.total.ano) }}</td>
          <td class="num">{{ reaisCurtos(b.bloco.total.mediaMensal) }}</td>
          <td class="num">{{ b.bloco.total.ano ? '100%' : '–' }}</td>
          <td />
        </tr>

        <tr v-if="b.bloco.grupos.length === 0" class="linha-vazia">
          <td :colspan="17">Nenhum grupo de {{ b.titulo.toLowerCase() }} ainda.</td>
        </tr>

        <template v-for="g in b.bloco.grupos" :key="g.id">
          <tr class="linha-grupo" :class="{ inativo: !g.ativo }">
            <th scope="row">
              <button type="button" class="abrir" :aria-expanded="!fechados.has(g.id)" @click="alternar(g.id)">
                <ChevronRight class="seta" aria-hidden="true" />
                <span class="nome-grupo">{{ g.nome }}</span>
                <span class="natureza">{{ g.natureza === 'FIXA' ? 'fixa' : 'variável' }}</span>
              </button>
            </th>
            <td v-for="(v, i) in g.total.valores" :key="i" class="num" :class="classesCelula(i, g.previsto[i])"
                :title="dica(v, g.previsto[i])" @mouseenter="realce = i">{{ reaisCurtos(v) }}</td>
            <td class="num total">{{ reaisCurtos(g.total.ano) }}</td>
            <td class="num">{{ reaisCurtos(g.total.mediaMensal) }}</td>
            <td class="num">{{ pct(g.participacao) }}</td>
            <td />
          </tr>
          <template v-if="!fechados.has(g.id)">
            <tr v-for="c in categoriasVisiveis(g)" :key="c.id" class="linha-categoria" :class="{ inativo: !c.ativa }">
              <th scope="row">
                <RouterLink :to="linkCategoria(c)" class="nome-categoria">{{ c.nome }}</RouterLink>
                <span v-if="c.calculada" class="selo">{{ pct(c.aliquota, 2) }}</span>
                <span v-if="!c.ativa" class="selo">desativada</span>
              </th>
              <td v-for="(v, i) in c.total.valores" :key="i" class="num" :class="classesCelula(i, c.previsto[i])"
                  @mouseenter="realce = i">
                <button v-if="clicavel(c, i)" type="button" class="celula" :aria-label="rotuloCelula(c, i, b.chave)"
                        :title="dicaCategoria(c, i)" @click="emit('abrir', c, b.chave, i + 1)">{{ reaisCurtos(v) }}</button>
                <span v-else :title="dicaCategoria(c, i)">{{ reaisCurtos(v) }}</span>
              </td>
              <td class="num total">{{ reaisCurtos(c.total.ano) }}</td>
              <td class="num">{{ reaisCurtos(c.total.mediaMensal) }}</td>
              <td class="num">{{ pct(c.participacao) }}</td>
              <td class="num rank" :title="textoRank(c, b.chave)">{{ c.rank ? ordinal(c.rank) : '–' }}</td>
            </tr>
          </template>
        </template>
      </tbody>

      <tbody class="bloco-saldo">
        <tr class="linha-saldo">
          <th scope="row">Receitas − despesas</th>
          <td v-for="(v, i) in saldoMes" :key="i" class="num" :class="[classesCelula(i), { negativo: v < -0.004 }]"
              :title="reais(v)" @mouseenter="realce = i">
            {{ v < -0.004 ? '−' + reaisCurtos(-v) : reaisCurtos(v) }}
          </td>
          <td class="num total" :class="{ negativo: saldoAno < -0.004 }">
            {{ saldoAno < -0.004 ? '−' + reaisCurtos(-saldoAno) : reaisCurtos(saldoAno) }}
          </td>
          <td colspan="3" />
        </tr>
      </tbody>
    </table>
  </div>

  <div id="explica-rank" popover class="explicacao">
    <h3>Rank: o que mais pesa no ano</h3>
    <p>
      É a posição de cada categoria pelo total do ano. <strong>1º</strong> é a que mais pesa. Receitas e despesas têm
      cada uma a sua contagem.
    </p>
    <p>
      Serve para saber por onde começar: mexer numa das primeiras posições muda o ano; mexer na 30ª quase não aparece.
    </p>
    <p v-if="topoDespesas.length" class="exemplo">
      Em {{ visao.ano }}, as despesas que mais pesam:
      <template v-for="(c, n) in topoDespesas" :key="c.id">
        <br /><strong>{{ ordinal(c.rank!) }}</strong> {{ c.nome }}, <span class="num">{{ reais(c.total.ano) }}</span>
        ({{ pct(c.participacao) }}){{ n === topoDespesas.length - 1 ? '.' : ';' }}
      </template>
    </p>
    <button type="button" class="botao botao-pequeno" popovertarget="explica-rank" popovertargetaction="hide">Entendi</button>
  </div>
</template>

<style scoped>
.ferramentas {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--e-3);
  margin-bottom: var(--e-3);
}

.legenda {
  display: flex;
  flex-wrap: wrap;
  gap: var(--e-4);
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.legenda span {
  display: inline-flex;
  align-items: center;
  gap: var(--e-2);
}

.amostra {
  display: inline-block;
  width: 0.75rem;
  height: 0.75rem;
  border-radius: 3px;
}

.amostra.realizado {
  background: var(--petroleo);
}

.amostra.previsto {
  background: var(--coral-claro);
  border: 1px dashed var(--coral-borda);
}

.amostra.renda {
  height: 3px;
  width: 0.875rem;
  border-radius: 2px;
  background: var(--verde);
}

/* ——— A planilha ——— */
.planilha {
  overflow: auto;
  max-height: calc(100dvh - var(--barra-altura) - 2.5rem);
  border: 1px solid var(--linha);
  border-radius: var(--raio-g);
  background: var(--papel);
  overscroll-behavior: contain;
}

table {
  width: 100%;
  min-width: 81rem;
  border-collapse: separate;
  border-spacing: 0;
  font-size: var(--t-sm);
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'tnum';
}

.c-nome {
  width: 14rem;
}

.c-mes {
  width: 4.25rem;
}

.c-total {
  width: 5.75rem;
}

.c-media {
  width: 5rem;
}

/* Com centavos ("12.345,67") as colunas de valor precisam de mais espaço. */
table.com-centavos {
  min-width: 97rem;
}

.com-centavos .c-mes {
  width: 5.5rem;
}

.com-centavos .c-total {
  width: 7rem;
}

.com-centavos .c-media {
  width: 6.25rem;
}

.c-pct {
  width: 4.25rem;
}

.c-rank {
  width: 3.25rem;
}

th,
td {
  padding: 0 var(--e-2);
  height: 2.375rem;
  text-align: right;
  white-space: nowrap;
  border-bottom: 1px solid var(--linha);
}

tbody th {
  position: sticky;
  left: 0;
  z-index: 1;
  text-align: left;
  font-weight: 500;
  background: var(--papel);
  border-right: 1px solid var(--linha);
  padding-left: var(--e-4);
  max-width: 14rem;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Copa do ano */
.copa td,
.copa th {
  height: 5.5rem;
  padding-top: var(--e-3);
  padding-bottom: 0;
  vertical-align: bottom;
  border-bottom: 0;
  background: var(--papel);
}

.canto-copa {
  position: sticky;
  left: 0;
  z-index: 3;
  text-align: left;
  font-size: var(--t-xs);
  font-weight: 600;
  color: var(--texto-2);
  padding: 0 var(--e-4) var(--e-2);
  border-right: 1px solid var(--linha);
}

.barra-mes {
  position: relative;
  height: 4.25rem;
  margin: 0 auto;
  width: 1.75rem;
}

.pilha {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.parte {
  display: block;
  width: 100%;
  transition: height 420ms var(--ease);
}

.parte.realizado {
  background: var(--petroleo);
  border-radius: 0 0 2px 2px;
}

.parte.previsto {
  background: var(--coral-claro);
  outline: 1px dashed var(--coral-borda);
  outline-offset: -1px;
  border-radius: 4px 4px 0 0;
}

.parte.realizado.topo {
  border-radius: 4px 4px 2px 2px;
}

.fantasma {
  position: absolute;
  inset: 25% 0 0;
  border: 1.5px dashed var(--pedra-forte);
  border-radius: 4px 4px 2px 2px;
}

.traco-renda {
  position: absolute;
  left: -0.375rem;
  right: -0.375rem;
  height: 3px;
  margin-bottom: -1.5px;
  border-radius: 2px;
  background: var(--verde);
}

.copa td.realce .barra-mes,
.copa td.atual .barra-mes {
  filter: saturate(1.15);
}

.copa td.realce .parte.realizado {
  background: var(--petroleo-escuro);
}

.copa-resumo {
  text-align: left !important;
  padding-left: var(--e-4) !important;
  padding-bottom: var(--e-2) !important;
  font-size: var(--t-xs);
  color: var(--texto-2);
  white-space: normal;
  line-height: 1.3;
}

.copa-resumo .num {
  display: block;
  font-size: var(--t-lg);
  font-weight: 700;
  color: var(--texto);
}

/* Cabeçalho dos meses */
.meses th {
  position: sticky;
  top: 0;
  z-index: 2;
  height: 2.25rem;
  background: var(--areia-funda);
  color: var(--texto-2);
  font-size: var(--t-xs);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border-top: 1px solid var(--linha);
  border-bottom: 1px solid var(--pedra-forte);
}

.meses th abbr {
  text-decoration: none;
}

.meses .canto {
  left: 0;
  z-index: 4;
  text-align: left;
  padding-left: var(--e-4);
  border-right: 1px solid var(--linha);
  text-transform: none;
  letter-spacing: 0;
}

.meses th.atual {
  color: var(--petroleo-escuro);
  background: var(--petroleo-tinta-forte);
}

/* Linhas */
.linha-tipo th,
.linha-tipo td {
  height: 2.75rem;
  font-weight: 700;
  background: var(--areia);
  font-size: var(--t-sm);
}

.linha-tipo th {
  text-transform: uppercase;
  letter-spacing: 0.04em;
  font-size: var(--t-xs);
}

.bloco-receita .linha-tipo th {
  color: var(--verde);
}

.bloco-despesa .linha-tipo th {
  color: var(--grafite);
}

tbody + tbody .linha-tipo th,
tbody + tbody .linha-tipo td {
  border-top: 2px solid var(--pedra-forte);
}

.linha-grupo th,
.linha-grupo td {
  font-weight: 650;
}

.abrir {
  display: flex;
  align-items: center;
  gap: var(--e-1);
  width: 100%;
  min-height: 2.25rem;
  margin-left: calc(var(--e-2) * -1);
  padding: 0 var(--e-1);
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.abrir:hover {
  background: var(--areia);
}

.seta {
  width: 1rem;
  height: 1rem;
  flex: none;
  color: var(--texto-2);
  transition: transform 180ms var(--ease);
}

.abrir[aria-expanded='true'] .seta {
  transform: rotate(90deg);
}

.nome-grupo {
  overflow: hidden;
  text-overflow: ellipsis;
}

.natureza {
  margin-left: auto;
  padding-left: var(--e-2);
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--texto-2);
}

.linha-categoria th {
  padding-left: calc(var(--e-4) + 1.5rem);
  color: var(--texto);
}

.linha-categoria td {
  color: var(--texto);
}

.nome-categoria {
  color: inherit;
  text-decoration: none;
}

.nome-categoria:hover {
  color: var(--acao);
  text-decoration: underline;
}

.linha-categoria .selo {
  margin-left: var(--e-2);
}

.inativo th,
.inativo td {
  color: var(--cinza-claro);
}

.linha-vazia td {
  text-align: left;
  color: var(--texto-2);
  padding-left: var(--e-4);
}

td.total {
  font-weight: 700;
  border-left: 1px solid var(--linha);
}

td.rank {
  color: var(--texto-2);
}

/* Valor clicável: abre o mês da categoria. Ocupa a célula toda para o alvo ficar grande. */
td:has(> .celula) {
  padding: 0;
}

.celula {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 2.375rem;
  padding: 0 var(--e-2);
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: right;
  cursor: pointer;
}

.celula:hover {
  color: var(--petroleo-escuro);
  text-decoration: underline;
  text-decoration-color: var(--pedra-forte);
  text-underline-offset: 3px;
}

.celula:focus-visible {
  outline-offset: -2px;
}

.col-rank {
  padding: 0 var(--e-1);
}

.explicar {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.2rem;
  width: 100%;
  min-height: 2rem;
  padding: 0 var(--e-1);
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  font: inherit;
  letter-spacing: inherit;
  text-transform: inherit;
  cursor: pointer;
}

.explicar:hover {
  color: var(--petroleo-escuro);
  background: var(--petroleo-tinta);
}

.explicar svg {
  width: 0.875rem;
  height: 0.875rem;
  flex: none;
}

.explicacao {
  width: min(26rem, calc(100vw - 2rem));
  padding: var(--e-5);
  border: 1px solid var(--linha);
  border-radius: var(--raio-g);
  background: var(--papel);
  color: var(--texto);
  box-shadow: 0 8px 24px rgb(18 58 65 / 0.16);
}

.explicacao::backdrop {
  background: rgb(18 58 65 / 0.12);
}

.explicacao h3 {
  margin-bottom: var(--e-2);
  font-family: var(--fonte-titulo);
  font-weight: 500;
  font-size: var(--t-lg);
}

.explicacao p {
  margin-bottom: var(--e-3);
  font-size: var(--t-sm);
  line-height: 1.55;
}

.explicacao .exemplo {
  padding: var(--e-3);
  border-radius: var(--raio);
  background: var(--areia);
}

/* Estados das células */
td.atual {
  background-color: rgb(228 238 239 / 0.55);
}

td.realce {
  background-color: rgb(207 224 226 / 0.45);
}

/* Previsto → confirmado: a célula perde o tracejado e assenta no papel. */
td {
  transition: background-color 450ms var(--ease), outline-color 450ms var(--ease);
}

td.prev {
  background-color: var(--coral-claro);
  outline: 1px dashed var(--coral-borda);
  outline-offset: -4px;
  border-radius: 6px;
}

td.prev.realce {
  background-color: #f9dcd2;
}

tbody tr:hover > th,
tbody tr:hover > td:not(.prev) {
  background-color: rgb(239 236 230 / 0.7);
}

tbody tr:hover > th {
  background-color: var(--areia);
}

.linha-saldo th,
.linha-saldo td {
  height: 2.75rem;
  font-weight: 700;
  background: var(--areia);
  border-top: 2px solid var(--pedra-forte);
  border-bottom: 0;
}

.negativo {
  color: var(--erro);
}
</style>
