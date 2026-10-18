<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { lancamentos, recorrencias } from '../api/servicos'
import type { Frequencia, Lancamento, Tipo } from '../api/tipos'
import { mensagemDeErro } from '../api/http'
import { catalogo } from '../estado/catalogo'
import { sessao } from '../estado/sessao'
import { FREQUENCIAS, competenciaPorExtenso, hoje, reais } from '../util/formato'
import CampoValor from './CampoValor.vue'
import Mensagem from './Mensagem.vue'
import SeletorCategoria from './SeletorCategoria.vue'

const props = defineProps<{ lancamento?: Lancamento | null; tipoInicial?: Tipo; categoriaInicial?: number; dataInicial?: string }>()
const emit = defineEmits<{ salvo: [mensagem: string]; cancelar: [] }>()

const editando = computed(() => !!props.lancamento)
const l = props.lancamento
const form = reactive({
  tipo: (l?.tipo ?? props.tipoInicial ?? 'DESPESA') as Tipo,
  valor: l?.valor ?? (null as number | null),
  descricao: l?.descricao ?? '',
  categoriaId: l?.categoria.id ?? props.categoriaInicial ?? (null as number | null),
  data: l?.data ?? props.dataInicial ?? hoje(),
  membroId: l?.membro?.id ?? sessao.estado.usuario?.membroId ?? null,
  parcelado: false,
  parcelas: 2,
  observacao: l?.observacao ?? '',
  repetir: false,
  frequencia: 'MENSAL' as Frequencia,
})
const enviando = ref(false)
const erro = ref('')
const tentou = ref(false)

const membros = computed(() => (sessao.estado.familia?.membros ?? []).filter((m) => m.ativo))
const podeEscolherPessoa = computed(() => sessao.dono.value && membros.value.length > 1)
const valorParcela = computed(() =>
  form.valor && form.parcelas >= 2 ? Math.floor((form.valor / form.parcelas) * 100) / 100 : null,
)

onMounted(() => catalogo.carregar())

// Categoria recorrente (aluguel, telefone, salário): o lançamento já sugere repetir no ano. Repetir cria a
// recorrência a partir do mês da data e confirma este mês com o valor e a data informados.
const categoria = computed(() => catalogo.estado.grupos.flatMap((g) => g.categorias).find((c) => c.id === form.categoriaId) ?? null)
const podeRepetir = computed(() => !editando.value && !form.parcelado)
const competenciaDaData = computed(() => form.data.slice(0, 7))
const repetirNoAno = computed(() => (form.valor ? form.valor * FREQUENCIAS[form.frequencia].vezes : null))

watch(() => [form.categoriaId, catalogo.estado.carregado] as const, () => {
  if (!editando.value) form.repetir = !!categoria.value?.recorrente
}, { immediate: true })

watch(() => form.parcelado, (p) => {
  if (p) form.repetir = false
})

function trocarTipo(t: Tipo) {
  if (form.tipo === t) return
  form.tipo = t
  form.categoriaId = null
  if (t === 'RECEITA') form.parcelado = false
}

async function lancarRepetindo(base: { categoriaId: number; membroId: number | null; descricao: string; observacao: string | null }) {
  const r = await recorrencias.criar({
    ...base,
    valor: form.valor!,
    dia: Number(form.data.slice(8, 10)),
    frequencia: form.frequencia,
    inicio: competenciaDaData.value,
    fim: null,
  })
  // O que já aconteceu entra como confirmado; data futura fica prevista até o dia chegar.
  if (form.data <= hoje()) {
    try {
      await recorrencias.confirmar(r.id, competenciaDaData.value, form.valor!, form.data)
    } catch (e) {
      // A recorrência já existe: não deixa salvar de novo (duplicaria). Fecha avisando o que falta.
      emit('salvo', `A recorrência foi criada, mas ${competenciaPorExtenso(competenciaDaData.value)} não foi `
        + `confirmado (${mensagemDeErro(e)}). Confira em Recorrências.`)
      return
    }
  }
  emit('salvo', `${form.tipo === 'RECEITA' ? 'Receita lançada' : 'Despesa lançada'} e repetindo ${FREQUENCIAS[form.frequencia].rotulo.toLowerCase()}: os próximos meses já aparecem como previstos no ano.`)
}

async function salvar() {
  tentou.value = true
  erro.value = ''
  if (!form.valor || form.valor <= 0 || !form.categoriaId || !form.descricao.trim() || !form.data) return
  if (form.parcelado && (form.parcelas < 2 || form.parcelas > 60)) return
  enviando.value = true
  const base = {
    categoriaId: form.categoriaId,
    membroId: form.membroId,
    descricao: form.descricao.trim(),
    observacao: form.observacao.trim() || null,
  }
  try {
    if (props.lancamento) {
      await lancamentos.atualizar(props.lancamento.id, { ...base, valor: form.valor, data: form.data })
      emit('salvo', 'Lançamento atualizado.')
    } else if (form.repetir) {
      await lancarRepetindo(base)
    } else if (form.parcelado) {
      await lancamentos.lancarParcelado({ ...base, valorTotal: form.valor, parcelas: form.parcelas, primeiraData: form.data })
      emit('salvo', `Compra lançada em ${form.parcelas} parcelas.`)
    } else {
      await lancamentos.lancar({ ...base, valor: form.valor, data: form.data })
      emit('salvo', form.tipo === 'RECEITA' ? 'Receita lançada.' : 'Despesa lançada.')
    }
    sessao.avisarMudanca()
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    enviando.value = false
  }
}
</script>

<template>
  <form id="form-lancamento" class="formulario" novalidate @submit.prevent="salvar">
    <div v-if="!editando" class="segmentos" role="group" aria-label="Tipo">
      <button type="button" :aria-pressed="form.tipo === 'DESPESA'" @click="trocarTipo('DESPESA')">Despesa</button>
      <button type="button" :aria-pressed="form.tipo === 'RECEITA'" @click="trocarTipo('RECEITA')">Receita</button>
    </div>

    <div class="campo">
      <label for="l-valor">{{ form.parcelado ? 'Valor total da compra' : 'Valor' }}</label>
      <CampoValor id="l-valor" v-model="form.valor" obrigatorio :invalido="tentou && !form.valor" />
      <span v-if="tentou && !form.valor" class="erro-campo">Informe o valor.</span>
    </div>

    <div class="campo">
      <label for="l-descricao">Descrição</label>
      <input id="l-descricao" v-model="form.descricao" class="entrada" maxlength="200" required
             :placeholder="form.tipo === 'RECEITA' ? 'Ex.: salário de outubro' : 'Ex.: mercado da semana'"
             :aria-invalid="(tentou && !form.descricao.trim()) || undefined" />
      <span v-if="tentou && !form.descricao.trim()" class="erro-campo">Conte o que foi, em poucas palavras.</span>
    </div>

    <div class="campo">
      <label for="l-categoria">Categoria</label>
      <SeletorCategoria id="l-categoria" v-model="form.categoriaId" :tipo="form.tipo"
                        :invalido="tentou && !form.categoriaId" />
      <span v-if="tentou && !form.categoriaId" class="erro-campo">Escolha a categoria.</span>
    </div>

    <div class="grade-campos duas">
      <div class="campo">
        <label for="l-data">{{ form.parcelado ? 'Data da 1ª parcela' : 'Data' }}</label>
        <input id="l-data" v-model="form.data" class="entrada" type="date" required />
      </div>
      <div class="campo">
        <label for="l-pessoa">De quem é</label>
        <select v-if="podeEscolherPessoa" id="l-pessoa" v-model="form.membroId" class="selecao">
          <option v-for="m in membros" :key="m.id" :value="m.id">{{ m.nome }}</option>
        </select>
        <input v-else id="l-pessoa" class="entrada" disabled
               :value="props.lancamento?.membro?.nome ?? sessao.estado.usuario?.nome" />
      </div>
    </div>

    <div v-if="podeRepetir && categoria" class="repetir" :class="{ ligado: form.repetir }">
      <label class="caixa-marcar">
        <input v-model="form.repetir" type="checkbox" aria-describedby="l-repetir-ajuda" />
        Repetir no ano
      </label>
      <div v-if="form.repetir" class="grade-campos duas">
        <div class="campo">
          <label for="l-frequencia">Repete</label>
          <select id="l-frequencia" v-model="form.frequencia" class="selecao">
            <option v-for="(f, chave) in FREQUENCIAS" :key="chave" :value="chave">{{ f.rotulo }}</option>
          </select>
        </div>
        <p v-if="repetirNoAno" class="resumo-parcela num">
          {{ reais(repetirNoAno) }} num ano cheio<br />
          <span class="texto-2">no dia {{ Number(form.data.slice(8, 10)) }}, sem data para acabar</span>
        </p>
      </div>
      <p id="l-repetir-ajuda" class="ajuda">
        <template v-if="form.repetir">
          {{ competenciaPorExtenso(competenciaDaData) }} entra {{ form.data <= hoje() ? 'como confirmado' : 'como previsto' }}
          e os próximos meses aparecem como previstos na visão anual, para confirmar quando acontecerem. Para mudar o
          valor ou parar, edite em Recorrências.
        </template>
        <template v-else-if="categoria.recorrente">
          {{ categoria.nome }} costuma se repetir. Desmarcado, vale só para este mês.
        </template>
        <template v-else>Marque se isto vai acontecer de novo nos próximos meses.</template>
      </p>
    </div>

    <div v-if="!editando && form.tipo === 'DESPESA' && !form.repetir" class="parcelas">
      <label class="caixa-marcar">
        <input v-model="form.parcelado" type="checkbox" />
        Compra parcelada
      </label>
      <div v-if="form.parcelado" class="grade-campos duas">
        <div class="campo">
          <label for="l-parcelas">Parcelas</label>
          <input id="l-parcelas" v-model.number="form.parcelas" class="entrada num" type="number" min="2" max="60"
                 inputmode="numeric" />
        </div>
        <p v-if="valorParcela" class="resumo-parcela num">
          {{ form.parcelas }}× de {{ reais(valorParcela) }}<br />
          <span class="texto-2">os centavos que sobram vão na 1ª</span>
        </p>
      </div>
    </div>

    <div class="campo">
      <label for="l-obs">Observação <span class="texto-2">(opcional)</span></label>
      <textarea id="l-obs" v-model="form.observacao" class="entrada" maxlength="500" rows="2" />
    </div>

    <Mensagem v-if="erro" tipo="erro">{{ erro }}</Mensagem>

    <div class="acoes rodape-form">
      <button type="button" class="botao" @click="emit('cancelar')">Cancelar</button>
      <button type="submit" class="botao botao-primario" :disabled="enviando">
        {{ enviando ? 'Salvando…' : editando ? 'Salvar' : 'Lançar' }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.parcelas {
  display: grid;
  gap: var(--e-3);
  padding: var(--e-4);
  border-radius: var(--raio);
  background: var(--areia);
}

.repetir {
  display: grid;
  gap: var(--e-3);
  padding: var(--e-4);
  border-radius: var(--raio);
  background: var(--areia);
}

.repetir.ligado {
  background: var(--coral-claro);
  outline: 1px dashed var(--coral-borda);
  outline-offset: -1px;
}

.repetir .ajuda {
  max-width: 52ch;
  font-size: var(--t-xs);
  line-height: 1.5;
  color: var(--texto-2);
}

.resumo-parcela {
  align-self: end;
  font-weight: 600;
  font-size: var(--t-sm);
  line-height: 1.4;
  padding-bottom: var(--e-1);
}

.resumo-parcela .texto-2 {
  font-weight: 400;
  font-size: var(--t-xs);
}

.rodape-form {
  justify-content: flex-end;
  padding-top: var(--e-2);
}
</style>
