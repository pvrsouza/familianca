<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ChevronRight, Info } from 'lucide-vue-next'
import type { Bloco, CategoriaVisao, GrupoVisao, ItemVisao, SeriePercentual, Tipo, VisaoAnual } from '../../api/tipos'
import {
  MESES, MESES_CURTOS, anoAtual, competenciaCurta, explicarTrecho, ordinal, pct, previsaoCurta, previsaoDaCategoria, reais, reaisCurtos,
  variacao,
} from '../../util/formato'
import { centavos } from '../../estado/centavos'
import { inflacao } from '../../estado/inflacao'
import { detalhes } from '../../estado/detalhes'
import { catalogo } from '../../estado/catalogo'
import { sessao } from '../../estado/sessao'
import SelosCategoria from '../SelosCategoria.vue'
import ConfigPrevisao from '../ConfigPrevisao.vue'
import FaixaEdicao from '../FaixaEdicao.vue'
import Mensagem from '../Mensagem.vue'
import PainelMes, { type AlvoMes } from './PainelMes.vue'

// A planilha viva: grupos que abrem em categorias, 12 meses, total, média, % do ano e rank. Acima dos meses, a
// copa do ano: uma barra por mês com a despesa realizada (cheia), a prevista (tracejada) e o traço da renda.
// Como na planilha, a inflação da família fica sob as despesas e a de cada grupo de despesa sob o grupo.
const props = defineProps<{ visao: VisaoAnual; mesAtual: number | null }>()
// ——— Editar dentro da tabela ———
// Clicar num mês abre uma faixa logo abaixo da linha (da categoria ou do item), com o que forma aquele valor:
// confirmar, outro valor, pular, editar, lançar. Clicar de novo na mesma célula, no X ou Esc fecha.
const edicao = ref<{ chave: string; alvo: AlvoMes } | null>(null)
const planilha = ref<HTMLElement | null>(null)
const larguraFaixa = ref(0)

function chaveDe(c: CategoriaVisao, item?: ItemVisao): string {
  return item ? `${c.id}|${item.recorrenciaId ?? (item.estimativa ? 'estimativa' : item.descricao)}` : `${c.id}`
}

function editando(chave: string, i: number): boolean {
  return edicao.value?.chave === chave && edicao.value.alvo.mes === i + 1
}

function medir() {
  larguraFaixa.value = planilha.value?.clientWidth ?? 0
}

function abrirAqui(c: CategoriaVisao, tipo: Tipo, mes: number, item?: ItemVisao) {
  const chave = chaveDe(c, item)
  if (edicao.value?.chave === chave && edicao.value.alvo.mes === mes) {
    edicao.value = null
    return
  }
  medir()
  previsao.value = null
  edicao.value = {
    chave,
    alvo: {
      categoria: c, tipo, ano: props.visao.ano, mes,
      item: item ? { descricao: item.descricao, recorrenciaId: item.recorrenciaId, estimativa: item.estimativa } : null,
    },
  }
}

watch(() => props.visao.ano, () => {
  edicao.value = null
  previsao.value = null
})

function categoriaDaVisao(id: number | undefined): CategoriaVisao | undefined {
  return [...props.visao.receitas.grupos, ...props.visao.despesas.grupos].flatMap((g) => g.categorias)
    .find((c) => c.id === id)
}

/** Como a estimativa da categoria aberta é prevista, na visão atual (muda quando a regra muda). */
const previsoesAtuais = computed(() => categoriaDaVisao(edicao.value?.alvo.categoria.id)?.previsoes ?? [])

// ——— Como a despesa é prevista ———
// Ao lado do nome da despesa (a conta que se repete ou a estimativa), um texto discreto diz o que a previsão usa
// ("média 3 meses", "valor cadastrado"). Clicar no nome (ou nesse texto) abre, logo abaixo da linha, a configuração
// atual e a troca da regra.
const previsao = ref<{ chave: string; categoriaId: number; recorrenciaId: number | null; titulo: string; descricao: string } | null>(null)
const avisoPrevisao = ref('')

/**
 * O item tem regra de previsão para mostrar: despesa com trechos de previsão, no ano atual ou adiante. Mesmo com o
 * ano todo confirmado, a regra vale para os anos seguintes.
 */
function temPrevisao(item: ItemVisao | undefined, tipo: Tipo): boolean {
  return !!item && tipo === 'DESPESA' && item.previsoes?.length > 0 && props.visao.ano >= anoAtual()
}

catalogo.carregar().catch(() => {})

/** Despesa não calculada tem regra de previsão da categoria (vale para as contas dela e a estimativa dos avulsos). */
function categoriaPrevisivel(c: CategoriaVisao, tipo: Tipo): boolean {
  return tipo === 'DESPESA' && !c.calculada && c.ativa
}

function regraDaCategoria(c: CategoriaVisao): string {
  const doCatalogo = catalogo.estado.grupos.flatMap((g) => g.categorias).find((x) => x.id === c.id)
  return previsaoDaCategoria(doCatalogo ?? { estimativa: c.estimativa, estimativaDesde: null, estimativaAntes: null },
    sessao.estado.familia?.estimativa ?? 'MEDIA_3M')
}

/** Só a regra, curta ("média 3 meses"); de quem é e as contas com regra própria ficam na dica. */
function textoCategoria(c: CategoriaVisao): string {
  return regraDaCategoria(c).replace(' (como a família)', '')
}

function dicaPrevisaoCategoria(c: CategoriaVisao): string {
  const proprias = c.itens.filter((i) => i.previsoes?.some((t) => t.origem === 'DESPESA')).map((i) => i.descricao)
  return `Previsão da categoria: ${regraDaCategoria(c)}.`
    + (proprias.length ? ` Com regra própria: ${proprias.join(', ')}.` : ' Todas as contas seguem esta regra.')
    + ' Clique para ver ou mudar.'
}

/**
 * O item só mostra a regra quando ela foge da categoria (regra própria, mudança marcada ou sem histórico): o que
 * segue a categoria já está dito na linha dela.
 */
function mostraTextoItem(item: ItemVisao): boolean {
  const t = item.previsoes ?? []
  return t.length > 1 || t.some((x) => x.origem === 'DESPESA' || (x.valor == null && x.regra !== 'NENHUMA'))
}

function abrirPrevisaoCategoria(c: CategoriaVisao) {
  const chave = chaveDe(c)
  if (previsao.value?.chave === chave) {
    previsao.value = null
    return
  }
  medir()
  edicao.value = null
  avisoPrevisao.value = ''
  previsao.value = { chave, categoriaId: c.id, recorrenciaId: null, titulo: c.nome, descricao: 'Regra da categoria' }
}

function textoPrevisao(item: ItemVisao): string {
  return previsaoCurta(item.previsoes, item.recorrenciaId != null)
}

function dicaPrevisao(item: ItemVisao): string {
  const conta = item.recorrenciaId != null
  return item.previsoes.map((t, i) => `${i === 0 ? 'Agora' : `A partir de ${competenciaCurta(t.desde ?? '')}`}: ${explicarTrecho(t, conta)}`)
    .join('\n') + '\nClique para ver ou mudar.'
}

function abrirPrevisao(c: CategoriaVisao, item: ItemVisao) {
  const chave = chaveDe(c, item)
  if (previsao.value?.chave === chave) {
    previsao.value = null
    return
  }
  medir()
  edicao.value = null
  avisoPrevisao.value = ''
  previsao.value = {
    chave, categoriaId: c.id, recorrenciaId: item.recorrenciaId,
    titulo: item.estimativa ? `${c.nome}: estimativa` : item.descricao,
    descricao: item.estimativa ? 'Despesas avulsas da categoria' : c.nome,
  }
}

function previsaoFeita(mensagem: string) {
  avisoPrevisao.value = mensagem
}
window.addEventListener('resize', medir)
onBeforeUnmount(() => window.removeEventListener('resize', medir))

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

// ——— Agrupar ou abrir em itens ———
// Categoria com mais de um item (três telefones, duas escolas) pode abrir uma linha por item. A escolha fica no
// navegador, por categoria; o botão da barra abre ou agrupa todas de uma vez.
const detalhavel = (c: CategoriaVisao) => c.itens.length > 1
const aberta = (c: CategoriaVisao) => detalhavel(c) && detalhes.abertas.value.has(c.id)
const detalhaveis = computed(() =>
  [...props.visao.receitas.grupos, ...props.visao.despesas.grupos].flatMap((g) => g.categorias).filter(detalhavel),
)
const todasAbertas = computed(() => detalhaveis.value.length > 0 && detalhaveis.value.every(aberta))

function alternarDetalhes() {
  detalhes.definir(detalhaveis.value.map((c) => c.id), !todasAbertas.value)
}

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

/** Variação do mês com o anterior; com previsto no mês ou no anterior, ela também é prevista. */
function inflacaoPrevista(previsto: number[], i: number): boolean {
  return previsto[i] > 0.004 || (i > 0 && previsto[i - 1] > 0.004)
}

function textoInflacao(nome: string, serie: SeriePercentual, previsto: number[], i: number): string {
  const v = serie.valores[i]
  if (v == null) return `${nome}, ${MESES[i]}: sem variação (mês sem despesa ou sem o mês anterior para comparar)`
  const sentido = v > 0 ? 'subiram' : v < 0 ? 'caíram' : 'ficaram iguais'
  const comparado = i > 0 ? `em relação a ${MESES[i - 1]}` : 'em relação a dezembro do ano anterior'
  return `${nome}, ${MESES[i]}: as despesas ${sentido} ${v !== 0 ? variacao(v) + ' ' : ''}${comparado}`
    + (inflacaoPrevista(previsto, i) ? '. Inclui valores previstos: muda quando forem confirmados' : '')
}

function textoInflacaoAno(nome: string, serie: SeriePercentual): string {
  return serie.ano == null
    ? `${nome}: sem inflação no ano`
    : `${nome}: inflação acumulada no ano, composta mês a mês, de ${variacao(serie.ano)}`
}

function classesInflacao(v: number | null, previsto: number[], i: number) {
  return {
    ...classesCelula(i),
    sobe: v != null && v > 0.05,
    desce: v != null && v < -0.05,
    'infl-prev': inflacaoPrevista(previsto, i),
  }
}

const previstoDespesas = computed(() => previstoDoBloco(props.visao.despesas))

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
      <label class="caixa-marcar" title="Linhas com quanto as despesas variaram de um mês para o outro: da família e de cada grupo">
        <input type="checkbox" :checked="inflacao.ligada.value" @change="inflacao.alternar()" /> Mostrar inflação
      </label>
      <label class="caixa-marcar" title="Sem centavos, os valores aparecem arredondados para o real mais próximo">
        <input type="checkbox" :checked="centavos.ligados.value" @change="centavos.alternar()" /> Mostrar centavos
      </label>
      <button v-if="detalhaveis.length" type="button" class="botao botao-sutil botao-pequeno" @click="alternarDetalhes"
              title="Categorias com mais de um item (como três telefones) mostram uma linha para cada">
        {{ todasAbertas ? 'Agrupar as categorias' : 'Abrir as categorias em itens' }}
      </button>
      <button type="button" class="botao botao-sutil botao-pequeno" @click="alternarTodos">
        {{ todosFechados ? 'Abrir todos os grupos' : 'Recolher os grupos' }}
      </button>
    </div>
  </div>

  <div ref="planilha" class="planilha" role="region" :aria-label="`Orçamento de ${visao.ano}, mês a mês`" tabindex="0"
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

        <tr v-if="b.chave === 'DESPESA' && inflacao.ligada.value && b.bloco.total.ano !== 0" class="linha-inflacao inflacao-familia">
          <th scope="row">
            <button type="button" class="explicar explicar-inflacao" popovertarget="explica-inflacao"
                    aria-label="Inflação da família: o que é">
              Inflação da família <Info aria-hidden="true" />
            </button>
          </th>
          <td v-for="(v, i) in visao.resumo.inflacaoFamiliar.valores" :key="i" class="num"
              :class="classesInflacao(v, previstoDespesas, i)" :title="textoInflacao('Família', visao.resumo.inflacaoFamiliar, previstoDespesas, i)"
              @mouseenter="realce = i">{{ variacao(v) }}</td>
          <td class="num total" :title="textoInflacaoAno('Família', visao.resumo.inflacaoFamiliar)">
            {{ variacao(visao.resumo.inflacaoFamiliar.ano) }}
          </td>
          <td colspan="3" class="nota-inflacao">no ano</td>
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
            <tr v-if="b.chave === 'DESPESA' && inflacao.ligada.value && g.total.ano !== 0" class="linha-inflacao">
              <th scope="row"><span class="rotulo-inflacao">Inflação</span></th>
              <td v-for="(v, i) in g.inflacao.valores" :key="i" class="num" :class="classesInflacao(v, g.previsto, i)"
                  :title="textoInflacao(g.nome, g.inflacao, g.previsto, i)" @mouseenter="realce = i">{{ variacao(v) }}</td>
              <td class="num total" :title="textoInflacaoAno(g.nome, g.inflacao)">{{ variacao(g.inflacao.ano) }}</td>
              <td colspan="3" />
            </tr>
            <template v-for="c in categoriasVisiveis(g)" :key="c.id">
            <tr class="linha-categoria" :class="{ inativo: !c.ativa, detalhavel: detalhavel(c) }">
              <th scope="row">
                <button v-if="detalhavel(c)" type="button" class="abrir-itens" :aria-expanded="aberta(c)"
                        :aria-label="`${aberta(c) ? 'Agrupar' : 'Abrir em itens'}: ${c.nome} (${c.itens.length})`"
                        :title="aberta(c) ? 'Agrupar' : `Abrir os ${c.itens.length} itens`" @click="detalhes.alternar(c.id)">
                  <ChevronRight class="seta" aria-hidden="true" />
                </button>
                <RouterLink :to="linkCategoria(c)" class="nome-categoria">{{ c.nome }}</RouterLink>
                <SelosCategoria class="selos" :categoria="c" :tipo="b.chave" />
                <button v-if="categoriaPrevisivel(c, b.chave)" type="button" class="como-previsto da-categoria"
                        :title="dicaPrevisaoCategoria(c)" :aria-expanded="previsao?.chave === chaveDe(c)"
                        :aria-label="`Como ${c.nome} é prevista: ${textoCategoria(c)}. Ver ou mudar`"
                        @click="abrirPrevisaoCategoria(c)">{{ textoCategoria(c) }}</button>
                <span v-if="!c.ativa" class="selo">desativada</span>
              </th>
              <td v-for="(v, i) in c.total.valores" :key="i" class="num"
                  :class="[classesCelula(i, c.previsto[i]), { editando: editando(chaveDe(c), i) }]" @mouseenter="realce = i">
                <button v-if="clicavel(c, i)" type="button" class="celula" :aria-label="rotuloCelula(c, i, b.chave)"
                        :title="dicaCategoria(c, i)" :aria-expanded="editando(chaveDe(c), i)"
                        @click="abrirAqui(c, b.chave, i + 1)">{{ reaisCurtos(v) }}</button>
                <span v-else :title="dicaCategoria(c, i)">{{ reaisCurtos(v) }}</span>
              </td>
              <td class="num total">{{ reaisCurtos(c.total.ano) }}</td>
              <td class="num">{{ reaisCurtos(c.total.mediaMensal) }}</td>
              <td class="num">{{ pct(c.participacao) }}</td>
              <td class="num rank" :title="textoRank(c, b.chave)">{{ c.rank ? ordinal(c.rank) : '–' }}</td>
            </tr>
            <tr v-if="edicao?.chave === chaveDe(c)" class="linha-edicao">
              <td colspan="17">
                <div class="faixa-dentro" :style="{ width: larguraFaixa ? `${larguraFaixa}px` : undefined }">
                  <PainelMes embutido :alvo="edicao.alvo" :previsoes="previsoesAtuais" @fechar="edicao = null" />
                </div>
              </td>
            </tr>
            <tr v-if="previsao?.chave === chaveDe(c)" class="linha-edicao">
              <td colspan="17">
                <div class="faixa-dentro" :style="{ width: larguraFaixa ? `${larguraFaixa}px` : undefined }">
                  <FaixaEdicao aberto :titulo="previsao.titulo" :descricao="previsao.descricao" @fechar="previsao = null">
                    <Mensagem v-if="avisoPrevisao" tipo="sucesso">{{ avisoPrevisao }}</Mensagem>
                    <ConfigPrevisao :categoria-id="previsao.categoriaId" :recorrencia-id="previsao.recorrenciaId"
                                    :previsoes="categoriaDaVisao(previsao.categoriaId)?.previsoes" @feito="previsaoFeita" />
                  </FaixaEdicao>
                </div>
              </td>
            </tr>
            <template v-if="aberta(c)">
              <template v-for="item in c.itens" :key="item.descricao">
              <tr class="linha-item">
                <th scope="row">
                  <button v-if="temPrevisao(item, b.chave)" type="button" class="nome-item clicavel"
                          :aria-expanded="previsao?.chave === chaveDe(c, item)" :title="dicaPrevisao(item)"
                          @click="abrirPrevisao(c, item)">
                    {{ item.descricao }}
                    <span v-if="mostraTextoItem(item)" class="como-previsto">{{ textoPrevisao(item) }}</span>
                  </button>
                  <span v-else class="nome-item">{{ item.descricao }}</span>
                </th>
                <td v-for="(v, i) in item.total.valores" :key="i" class="num"
                    :class="[classesCelula(i, item.previsto[i]), { editando: editando(chaveDe(c, item), i) }]"
                    @mouseenter="realce = i">
                  <!-- Abre a faixa logo abaixo, só com este item naquele mês. -->
                  <button v-if="clicavel(c, i)" type="button" class="celula"
                          :aria-label="`${item.descricao}, ${MESES[i]}: ${v ? dica(v, item.previsto[i]) : 'nada lançado'}. Abrir`"
                          :title="dica(v, item.previsto[i])" :aria-expanded="editando(chaveDe(c, item), i)"
                          @click="abrirAqui(c, b.chave, i + 1, item)">{{ reaisCurtos(v) }}</button>
                  <span v-else :title="dica(v, item.previsto[i])">{{ reaisCurtos(v) }}</span>
                </td>
                <td class="num total">{{ reaisCurtos(item.total.ano) }}</td>
                <td class="num">{{ reaisCurtos(item.total.mediaMensal) }}</td>
                <td colspan="2" />
              </tr>
              <tr v-if="edicao?.chave === chaveDe(c, item)" class="linha-edicao">
              <td colspan="17">
                <div class="faixa-dentro" :style="{ width: larguraFaixa ? `${larguraFaixa}px` : undefined }">
                  <PainelMes embutido :alvo="edicao.alvo" :previsoes="previsoesAtuais" @fechar="edicao = null" />
                </div>
              </td>
            </tr>
            <tr v-if="previsao?.chave === chaveDe(c, item)" class="linha-edicao">
              <td colspan="17">
                <div class="faixa-dentro" :style="{ width: larguraFaixa ? `${larguraFaixa}px` : undefined }">
                  <FaixaEdicao aberto :titulo="previsao.titulo" :descricao="previsao.descricao" @fechar="previsao = null">
                    <Mensagem v-if="avisoPrevisao" tipo="sucesso">{{ avisoPrevisao }}</Mensagem>
                    <ConfigPrevisao :categoria-id="previsao.categoriaId" :recorrencia-id="previsao.recorrenciaId"
                                    :previsoes="categoriaDaVisao(previsao.categoriaId)?.previsoes" @feito="previsaoFeita" />
                  </FaixaEdicao>
                </div>
              </td>
            </tr>
              </template>
            </template>
            </template>
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

  <div id="explica-inflacao" popover class="explicacao">
    <h3>Inflação da família</h3>
    <p>
      Quanto as despesas de vocês variaram de um mês para o outro, como na planilha. <strong>+5%</strong> em março quer
      dizer que março custou 5% a mais que fevereiro. Cada grupo de despesa tem a sua linha logo abaixo do nome.
    </p>
    <p>
      Na coluna <strong>Total</strong> fica a inflação acumulada no ano, composta mês a mês. Mês sem despesa fica sem
      variação (não conta como −100%), e o que tem valor previsto aparece em itálico: muda quando for confirmado.
    </p>
    <p v-if="visao.resumo.inflacaoFamiliar.ano != null" class="exemplo">
      Em {{ visao.ano }}, a inflação da família no ano está em
      <strong class="num">{{ variacao(visao.resumo.inflacaoFamiliar.ano) }}</strong>.
    </p>
    <button type="button" class="botao botao-pequeno" popovertarget="explica-inflacao" popovertargetaction="hide">Entendi</button>
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

.linha-categoria.detalhavel th {
  padding-left: var(--e-4);
}

.abrir-itens {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.5rem;
  height: 1.5rem;
  margin-right: 0;
  padding: 0;
  border: 0;
  border-radius: var(--raio);
  background: transparent;
  vertical-align: middle;
  cursor: pointer;
}

.abrir-itens:hover {
  background: var(--areia);
}

.abrir-itens[aria-expanded='true'] .seta {
  transform: rotate(90deg);
}

/* Itens da categoria aberta: um nível abaixo, mais discretos. */
.linha-item th,
.linha-item td {
  height: 2rem;
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.linha-item th {
  padding-left: calc(var(--e-4) + 3rem);
  font-weight: 400;
}

.nome-item {
  overflow-wrap: anywhere;
}

/* O nome da despesa que é prevista por regra abre a configuração; parece texto até passar o mouse. A regra fica
   sempre na linha de baixo. */
.nome-item.clicavel {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.nome-item.clicavel:hover,
.nome-item.clicavel[aria-expanded='true'] {
  color: var(--texto);
  text-decoration: underline;
  text-decoration-color: var(--linha);
  text-underline-offset: 3px;
}

/* Como a despesa é prevista ("média 3 meses"): discreto, em itálico e menor, para não competir com o nome. */
.como-previsto {
  padding: 0;
  border: 0;
  background: none;
  color: var(--texto-2);
  font: inherit;
  font-size: 0.625rem;
  font-style: italic;
  font-weight: 400;
  opacity: 0.55;
  white-space: nowrap;
  cursor: pointer;
}

/* Na categoria, a regra fica na linha de baixo do nome (depois da seta, quando há). */
.como-previsto.da-categoria {
  display: block;
  text-align: left;
}

.linha-categoria.detalhavel .como-previsto.da-categoria {
  margin-left: 1.5rem;
}

.nome-item.clicavel:hover .como-previsto,
.como-previsto:hover {
  opacity: 1;
}

.linha-categoria .selos {
  margin-left: var(--e-2);
}

/* Inflação: linhas mais baixas e discretas, como a linha "Inflação" sob cada grupo da planilha. */
.linha-inflacao th,
.linha-inflacao td {
  height: 2rem;
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.linha-inflacao th {
  padding-left: calc(var(--e-4) + 1.5rem);
  font-weight: 500;
}

.rotulo-inflacao {
  font-style: italic;
}

.linha-inflacao td.sobe {
  color: var(--coral-borda);
}

.linha-inflacao td.desce {
  color: var(--verde);
}

.linha-inflacao td.infl-prev {
  font-style: italic;
}

.linha-inflacao td.total {
  font-weight: 700;
}

.inflacao-familia th,
.inflacao-familia td {
  height: 2.25rem;
  font-weight: 600;
  background: var(--areia);
}

.inflacao-familia th {
  padding-left: var(--e-2);
  color: var(--texto);
}

.inflacao-familia .explicar-inflacao {
  justify-content: flex-start;
  width: auto;
  font-weight: 600;
}

.nota-inflacao {
  text-align: left;
  font-weight: 400 !important;
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
  box-shadow: 0 8px 24px var(--sombra-cor);
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
  background-color: var(--tinta-atual);
}

td.realce {
  background-color: var(--tinta-realce);
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
  background-color: var(--coral-realce);
}

tbody tr:hover > th,
tbody tr:hover > td:not(.prev) {
  background-color: var(--tinta-hover);
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

/* ——— Indicadores do ano ———
   Total, Média, % ano e Rank formam uma faixa à parte depois dos doze meses: divisória dupla, fundo levemente
   tingido (por cima do fundo de cada linha) e texto um passo abaixo. Os meses são o dado do dia a dia; a faixa é o
   resumo que se consulta. O Total continua forte: é o número que a família compara. Posição 14 em diante = depois
   do nome e dos 12 meses, em todas as linhas (a copa, o cabeçalho e o corpo). */
.planilha tr > :nth-child(n + 14) {
  background-image: linear-gradient(var(--tinta-indicadores), var(--tinta-indicadores));
}

.planilha tr > :nth-child(14) {
  border-left: 3px double var(--pedra-forte);
}

.planilha tbody tr > td:nth-child(n + 15) {
  font-size: var(--t-xs);
  font-weight: 500;
  color: var(--texto-2);
}

/* Nos itens de uma categoria aberta, o total acompanha o tom discreto da linha. */
.planilha .linha-item td.total {
  font-weight: 600;
}

/* Os meses no cabeçalho em tom cheio; os indicadores ficam no cinza de antes. */
.meses th:nth-child(-n + 13):not(.atual):not(.canto) {
  color: var(--texto);
}

/* ——— Faixa de edição ———
   Abre logo abaixo da linha clicada. Fica presa à esquerda e com a largura da área visível da tabela, para não
   rolar junto com os meses. A célula de onde ela saiu ganha o contorno de foco. */
.linha-edicao > td {
  height: auto;
  padding: 0;
  text-align: left;
  white-space: normal;
  background: var(--areia);
  border-bottom: 2px solid var(--pedra-forte);
  box-shadow: inset 0 2px 0 var(--acao);
}

.faixa-dentro {
  position: sticky;
  left: 0;
  box-sizing: border-box;
  max-width: 100%;
  padding: var(--e-4) var(--e-5) var(--e-5);
}

td.editando {
  box-shadow: inset 0 0 0 2px var(--acao);
}
</style>
