<script setup lang="ts">
import { computed, ref } from 'vue'
import { categorias as apiCategorias, recorrencias as apiRecorrencias } from '../../api/servicos'
import type { BaseEstimativa, Estimativa, Recorrencia } from '../../api/tipos'
import { mensagemDeErro } from '../../api/http'
import { sessao } from '../../estado/sessao'
import { catalogo } from '../../estado/catalogo'
import { ESTIMATIVAS, ORDEM_ESTIMATIVAS, ORIGENS_ESTIMATIVA, mesesDaBase, previstoPelaRegra, reais } from '../../util/formato'
import Mensagem from '../Mensagem.vue'

// Diz como os próximos meses estão sendo previstos (a regra, de quem ela é e os meses usados) e deixa mudar a regra
// ali mesmo: da própria conta que se repete (recorrencia) ou da categoria (categoriaId, a estimativa dos avulsos).
const props = defineProps<{
  base: BaseEstimativa | null
  recorrencia?: Recorrencia
  categoriaId: number
}>()
const emit = defineEmits<{ feito: [mensagem: string] }>()

const categoria = computed(() => catalogo.estado.grupos.flatMap((g) => g.categorias).find((c) => c.id === props.categoriaId))
const daFamilia = computed<Estimativa>(() => sessao.estado.familia?.estimativa ?? 'MEDIA_3M')
const daCategoria = computed<Estimativa>(() => categoria.value?.estimativa ?? daFamilia.value)
/** A regra própria (nula = segue a de cima) e a que vale de fato. */
const propria = computed(() => (props.recorrencia ? props.recorrencia.estimativa : categoria.value?.estimativa ?? null))
const herdada = computed(() => (props.recorrencia ? daCategoria.value : daFamilia.value))
const vale = computed(() => props.base?.regra ?? propria.value ?? herdada.value)
const origem = computed(() => props.base?.origem
  ?? (propria.value ? (props.recorrencia ? 'DESPESA' : 'CATEGORIA') : props.recorrencia && categoria.value?.estimativa ? 'CATEGORIA' : 'FAMILIA'))

// A conta é de quem cadastrou (ou de quem organiza); a regra da categoria, só de quem organiza.
const podeMudar = computed(() => (props.recorrencia
  ? sessao.dono.value || props.recorrencia.membro?.id === sessao.estado.usuario?.membroId
  : sessao.dono.value))

const texto = computed(() => {
  const de = ORIGENS_ESTIMATIVA[origem.value]
  if (props.base) {
    const como = props.recorrencia ? previstoPelaRegra(props.base.regra, props.base.meses.length) : ESTIMATIVAS[props.base.regra].como
    return `Previsto ${como} (${de}): ${mesesDaBase(props.base.meses)} → ${reais(props.base.valor)}.`
  }
  if (vale.value === 'NENHUMA') return `Sem estimativa (${de}): vale o valor cadastrado.`
  return `Ainda sem confirmações para prever ${ESTIMATIVAS[vale.value].como} (${de}): vale o valor cadastrado.`
})

const mudando = ref(false)
const ocupado = ref(false)
const erro = ref('')
const rotuloSeguir = computed(() => props.recorrencia
  ? `Seguir a categoria (${ESTIMATIVAS[daCategoria.value].rotulo.toLowerCase()})`
  : `Seguir a família (${ESTIMATIVAS[daFamilia.value].rotulo.toLowerCase()})`)

async function escolher(valor: string) {
  const nova = valor === '' ? null : (valor as Estimativa)
  ocupado.value = true
  erro.value = ''
  try {
    if (props.recorrencia) await apiRecorrencias.definirEstimativa(props.recorrencia.id, nova)
    else {
      await apiCategorias.definirEstimativa(props.categoriaId, nova)
      await catalogo.carregar(true)
    }
    sessao.avisarMudanca()
    mudando.value = false
    const qual = props.recorrencia ? props.recorrencia.descricao : categoria.value?.nome ?? 'A categoria'
    emit('feito', `${qual}: os próximos meses agora são previstos ${ESTIMATIVAS[nova ?? herdada.value].como}`
      + `${nova ? '' : props.recorrencia ? ', como a categoria' : ', como a família'}. Já recalculado.`)
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    ocupado.value = false
  }
}
</script>

<template>
  <div class="regra">
    <p class="texto-2">
      {{ texto }}
      <button v-if="podeMudar && !mudando" type="button" class="link" @click="mudando = true">Mudar a regra</button>
    </p>
    <label v-if="mudando" class="escolha">
      <span>{{ recorrencia ? 'Prever esta conta' : 'Prever esta categoria' }}</span>
      <select class="selecao" :value="propria ?? ''" :disabled="ocupado"
              @change="escolher(($event.target as HTMLSelectElement).value)">
        <option value="">{{ rotuloSeguir }}</option>
        <option v-for="e in ORDEM_ESTIMATIVAS" :key="e" :value="e">{{ ESTIMATIVAS[e].rotulo }}</option>
      </select>
      <button type="button" class="link" :disabled="ocupado" @click="mudando = false">Cancelar</button>
    </label>
    <Mensagem v-if="erro" tipo="erro">{{ erro }}</Mensagem>
  </div>
</template>

<style scoped>
.regra {
  display: grid;
  gap: var(--e-2);
  font-size: var(--t-xs);
}

.link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--petroleo-escuro);
  font: inherit;
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
}

.escolha {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--e-2) var(--e-3);
  font-weight: 600;
}

.escolha .selecao {
  width: auto;
  min-width: 14rem;
}
</style>
