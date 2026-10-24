<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { categorias as apiCategorias, recorrencias as apiRecorrencias } from '../api/servicos'
import type { BaseEstimativa, Estimativa, Recorrencia } from '../api/tipos'
import { mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import { catalogo } from '../estado/catalogo'
import {
  ESTIMATIVAS, ORDEM_ESTIMATIVAS, ORIGENS_ESTIMATIVA, anoAtual, competencia, competenciaCurta, competenciaPorExtenso,
  explicarTrecho, mesAtual, previsaoCurta, reais,
} from '../util/formato'
import Mensagem from './Mensagem.vue'

// Como os próximos meses de uma despesa são previstos, e a troca da regra. Primeiro mostra a configuração atual (a
// regra, de quem ela é, os meses usados e o que muda adiante); depois deixa escolher outra regra, para todos os meses
// previstos ou a partir de um mês. Serve à conta que se repete (recorrenciaId) e à categoria (a estimativa das
// despesas avulsas e as contas dela sem regra própria).
const props = defineProps<{
  categoriaId: number
  recorrenciaId?: number | null
  /** Trechos da estimativa da categoria vindos da visão anual (com os meses usados); sem eles, só a regra. */
  previsoes?: BaseEstimativa[]
}>()
const emit = defineEmits<{ feito: [mensagem: string] }>()

const rec = ref<Recorrencia | null>(null)
const carregando = ref(false)
const ocupado = ref(false)
const erro = ref('')

/** Na categoria: as contas dela (despesa que não confirma sozinha), que passam a seguir a regra da categoria. */
const contas = ref<Recorrencia[]>([])

async function carregar() {
  rec.value = null
  contas.value = []
  carregando.value = true
  try {
    if (props.recorrenciaId == null) {
      contas.value = (await apiRecorrencias.listar())
        .filter((r) => r.categoria.id === props.categoriaId && r.tipo === 'DESPESA' && !r.confirmarSozinha)
      return
    }
    rec.value = await apiRecorrencias.buscar(props.recorrenciaId)
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    carregando.value = false
  }
}
watch(() => [props.recorrenciaId, props.categoriaId], carregar, { immediate: true })
const contasProprias = computed(() => contas.value.filter((r) => r.estimativa || r.estimativaDesde))

const conta = computed(() => props.recorrenciaId != null)
const hojeComp = competencia(anoAtual(), mesAtual())
const categoria = computed(() => catalogo.estado.grupos.flatMap((g) => g.categorias).find((c) => c.id === props.categoriaId))
const daFamilia = computed<Estimativa>(() => sessao.estado.familia?.estimativa ?? 'MEDIA_3M')

/** A regra própria (da conta ou da categoria) que vale num mês; nula = segue a de cima. */
function propriaEm(alvo: { estimativa: Estimativa | null; estimativaDesde: string | null; estimativaAntes: Estimativa | null }, comp: string) {
  return alvo.estimativaDesde && comp < alvo.estimativaDesde ? alvo.estimativaAntes : alvo.estimativa
}

/** Trechos da categoria só com a regra (na tela de Categorias, sem os meses usados). */
const trechosDaCategoria = computed<BaseEstimativa[]>(() => {
  const c = categoria.value
  if (!c) return []
  const inicios = [hojeComp, ...(c.estimativaDesde && c.estimativaDesde > hojeComp ? [c.estimativaDesde] : [])]
  return inicios.map((desde) => {
    const propria = propriaEm(c, desde)
    return { regra: propria ?? daFamilia.value, origem: propria ? 'CATEGORIA' : 'FAMILIA', meses: [], valor: null, desde }
  })
})

const trechos = computed<BaseEstimativa[]>(() => {
  if (conta.value) return rec.value?.previsoes ?? []
  return props.previsoes?.length ? props.previsoes : trechosDaCategoria.value
})
const comBase = computed(() => conta.value || !!props.previsoes?.length)

function descrever(t: BaseEstimativa): string {
  if (comBase.value) return explicarTrecho(t, conta.value, rec.value?.valor)
  return `${ESTIMATIVAS[t.regra].rotulo} (${ORIGENS_ESTIMATIVA[t.origem]}).`
}

/** Valores ajustados à mão do mês atual em diante (valem acima da regra). */
const ajustados = computed(() => (rec.value?.valoresAjustados ?? []).filter((v) => v.competencia >= hojeComp))
const listaAjustados = (lista: { competencia: string; valor: number }[]) =>
  lista.map((v) => `${competenciaCurta(v.competencia)} ${reais(v.valor)}`).join(' · ')

// ——— Mudar a regra ———
const propriaAgora = computed<Estimativa | null>(() => {
  if (conta.value) return rec.value ? propriaEm(rec.value, hojeComp) : null
  return categoria.value ? propriaEm(categoria.value, hojeComp) : null
})
const regraDeCima = computed<Estimativa>(() => {
  if (!conta.value) return daFamilia.value
  const c = categoria.value
  return (c && propriaEm(c, hojeComp)) ?? daFamilia.value
})
const escolha = ref<string>('')
const desde = ref<string>('')
watch(propriaAgora, (p) => (escolha.value = p ?? ''), { immediate: true })

const rotuloSeguir = computed(() => `${conta.value ? 'Seguir a categoria' : 'Seguir a família'} `
  + `(${ESTIMATIVAS[regraDeCima.value].rotulo.toLowerCase()})`)
function rotulo(e: Estimativa) {
  return e === 'NENHUMA' && conta.value ? 'Não estimar (valor cadastrado)' : ESTIMATIVAS[e].rotulo
}

/** O mês atual e os 17 seguintes. */
const mesesFuturos = computed(() => Array.from({ length: 18 }, (_, i) => {
  const d = new Date(anoAtual(), mesAtual() - 1 + i, 1)
  return competencia(d.getFullYear(), d.getMonth() + 1)
}))

const podeMudar = computed(() => (conta.value
  ? !!rec.value && (sessao.dono.value || rec.value.membro?.id === sessao.estado.usuario?.membroId)
  : sessao.dono.value))
const mudou = computed(() => (escolha.value || null) !== propriaAgora.value || desde.value !== ''
  || (!conta.value && contasProprias.value.length > 0))

/** O que vai acontecer com a escolha feita, antes de aplicar. */
const consequencia = computed(() => {
  const substitui = !conta.value && contasProprias.value.length
    ? ` A regra própria de ${contasProprias.value.map((r) => r.descricao).join(', ')} é substituída: todas as contas `
      + 'da categoria passam a seguir esta regra.'
    : ''
  return textoAlcance() + substitui
})

function textoAlcance(): string {
  if (!desde.value) {
    const fica = ajustados.value.length
      ? ` Os meses ajustados à mão (${listaAjustados(ajustados.value)}) ficam como estão.` : ''
    return `Vale para todos os meses previstos, deste ano e dos próximos.${fica}`
  }
  const m = competenciaCurta(desde.value)
  const saem = ajustados.value.filter((v) => v.competencia >= desde.value)
  const antes = desde.value > hojeComp ? `Os meses antes de ${m} continuam como estão hoje. ` : ''
  const nasContas = conta.value ? '' : ' Nas contas da categoria, os valores ajustados à mão '
    + `de ${m} em diante deixam de valer.`
  return `${antes}De ${m} em diante, todos os meses seguem a regra escolhida`
    + `${saem.length ? `, inclusive os ajustados à mão (${listaAjustados(saem)}), que deixam de valer.` : '.'}${nasContas}`
}

async function aplicar() {
  const nova = (escolha.value || null) as Estimativa | null
  const aPartir = desde.value || null
  ocupado.value = true
  erro.value = ''
  try {
    if (conta.value && rec.value) {
      rec.value = await apiRecorrencias.definirEstimativa(rec.value.id, nova, aPartir)
    } else {
      await apiCategorias.definirEstimativa(props.categoriaId, nova, aPartir)
      await catalogo.carregar(true)
      await carregar()
    }
    sessao.avisarMudanca()
    desde.value = ''
    const nome = conta.value ? rec.value?.descricao : categoria.value?.nome
    const regra = nova ? ESTIMATIVAS[nova].rotulo.toLowerCase() : `como ${conta.value ? 'a categoria' : 'a família'}`
    emit('feito', `${nome}: prevista ${nova ? `pela regra "${regra}"` : regra}`
      + `${aPartir ? ` a partir de ${competenciaPorExtenso(aPartir)}` : ' em todos os meses previstos'}. Já recalculado.`)
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    ocupado.value = false
  }
}
</script>

<template>
  <div class="config" :aria-busy="carregando">
    <h3>Como os próximos meses são previstos</h3>
    <div v-if="carregando" class="esqueleto carregando" />
    <template v-else>
      <dl v-if="trechos.length" class="atual">
        <template v-for="(t, i) in trechos" :key="t.desde ?? i">
          <dt>{{ i === 0 ? 'Agora' : `A partir de ${competenciaCurta(t.desde ?? '')}` }}</dt>
          <dd>{{ descrever(t) }}</dd>
        </template>
        <template v-if="ajustados.length">
          <dt>À mão</dt>
          <dd>{{ listaAjustados(ajustados) }}: valem acima da regra.</dd>
        </template>
      </dl>
      <p v-else-if="conta && rec" class="texto-2">
        {{ rec.tipo === 'RECEITA' ? 'Receita' : 'Conta que confirma sozinha' }}: não é estimada, vale o valor cadastrado
        ({{ reais(rec.valor) }}).
      </p>
      <template v-if="!conta">
        <p class="texto-2 alcance">
          A regra da categoria vale para todas as contas dela e para a estimativa das despesas avulsas. Mudar aqui
          substitui a regra própria de cada conta e recalcula todos os previstos.
        </p>
        <dl v-if="contas.length" class="atual contas">
          <template v-for="r in contas" :key="r.id">
            <dt>{{ r.descricao }}</dt>
            <dd>
              {{ previsaoCurta(r.previsoes, true) || 'valor cadastrado' }}
              <span class="texto-2">{{ r.estimativa || r.estimativaDesde ? '· regra própria' : '· segue a categoria' }}</span>
            </dd>
          </template>
        </dl>
      </template>

      <form v-if="podeMudar && trechos.length" class="mudar" @submit.prevent="aplicar">
        <label>
          <span>Prever</span>
          <select v-model="escolha" class="selecao" :disabled="ocupado">
            <option value="">{{ rotuloSeguir }}{{ propriaAgora === null ? ' · atual' : '' }}</option>
            <option v-for="e in ORDEM_ESTIMATIVAS" :key="e" :value="e">
              {{ rotulo(e) }}{{ propriaAgora === e ? ' · atual' : '' }}
            </option>
          </select>
        </label>
        <label>
          <span>A partir de</span>
          <select v-model="desde" class="selecao" :disabled="ocupado">
            <option value="">Todos os meses previstos</option>
            <option v-for="m in mesesFuturos" :key="m" :value="m">
              {{ competenciaPorExtenso(m) }}{{ m === hojeComp ? ' (este mês)' : '' }}
            </option>
          </select>
        </label>
        <p class="texto-2 consequencia">{{ consequencia }}</p>
        <div class="acoes">
          <button type="submit" class="botao botao-primario botao-pequeno" :disabled="ocupado || !mudou">
            {{ ocupado ? 'Recalculando…' : 'Aplicar e recalcular' }}
          </button>
        </div>
      </form>
      <p v-else-if="trechos.length" class="texto-2">
        {{ conta ? 'Só quem cadastrou a conta ou quem organiza a família muda a regra.' : 'Só quem organiza a família muda a regra.' }}
      </p>
    </template>
    <Mensagem v-if="erro" tipo="erro">{{ erro }}</Mensagem>
  </div>
</template>

<style scoped>
.config {
  display: grid;
  gap: var(--e-3);
  max-width: 44rem;
}

h3 {
  margin: 0;
  font-size: var(--t-sm);
}

.atual {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--e-1) var(--e-3);
  margin: 0;
  font-size: var(--t-sm);
}

.contas {
  font-size: var(--t-xs);
}

.atual dt {
  color: var(--texto-2);
  font-weight: 600;
}

.atual dd {
  margin: 0;
}

.alcance,
.consequencia {
  margin: 0;
  font-size: var(--t-xs);
}

.mudar {
  display: grid;
  gap: var(--e-2);
  padding-top: var(--e-3);
  border-top: 1px solid var(--linha);
}

.mudar label {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--e-2) var(--e-3);
  font-size: var(--t-sm);
  font-weight: 600;
}

.mudar label span {
  min-width: 6rem;
}

.mudar .selecao {
  width: auto;
  min-width: 16rem;
  max-width: 100%;
}

.carregando {
  height: 3rem;
}

.acoes {
  display: flex;
  gap: var(--e-2);
}
</style>
