<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { recorrencias } from '../api/servicos'
import type { Frequencia, Recorrencia, Tipo } from '../api/tipos'
import { mensagemDeErro } from '../api/http'
import { catalogo } from '../estado/catalogo'
import { sessao } from '../estado/sessao'
import { FREQUENCIAS, anoAtual, mesAtual, competencia, dataPorExtenso, reais } from '../util/formato'
import CampoValor from './CampoValor.vue'
import Mensagem from './Mensagem.vue'
import SeletorCategoria from './SeletorCategoria.vue'

const props = defineProps<{ recorrencia?: Recorrencia | null; tipoInicial?: Tipo }>()
const emit = defineEmits<{ salvo: [mensagem: string]; cancelar: [] }>()

const r = props.recorrencia
const editando = !!r
const form = reactive({
  tipo: (r?.tipo ?? props.tipoInicial ?? 'DESPESA') as Tipo,
  valor: r?.valor ?? (null as number | null),
  descricao: r?.descricao ?? '',
  categoriaId: r?.categoria.id ?? (null as number | null),
  frequencia: (r?.frequencia ?? 'MENSAL') as Frequencia,
  dia: r?.dia ?? 10,
  inicio: r?.inicio ?? competencia(anoAtual(), mesAtual()),
  temFim: !!r?.fim,
  fim: r?.fim ?? competencia(anoAtual(), 12),
  membroId: r?.membro?.id ?? sessao.estado.usuario?.membroId ?? null,
  observacao: r?.observacao ?? '',
  confirmarSozinha: r?.confirmarSozinha ?? false,
})
const enviando = ref(false)
const erro = ref('')
const tentou = ref(false)

const membros = computed(() => (sessao.estado.familia?.membros ?? []).filter((m) => m.ativo))
const podeEscolherPessoa = computed(() => sessao.dono.value && membros.value.length > 1)
const noAno = computed(() => (form.valor ? form.valor * FREQUENCIAS[form.frequencia].vezes : null))

onMounted(() => catalogo.carregar())

function trocarTipo(t: Tipo) {
  if (form.tipo === t) return
  form.tipo = t
  form.categoriaId = null
}

async function salvar() {
  tentou.value = true
  erro.value = ''
  if (!form.valor || !form.categoriaId || !form.descricao.trim() || !form.inicio) return
  if (form.dia < 1 || form.dia > 31) return
  if (form.temFim && form.fim < form.inicio) {
    erro.value = 'O último mês precisa ser depois do primeiro.'
    return
  }
  enviando.value = true
  const dados = {
    categoriaId: form.categoriaId,
    membroId: form.membroId,
    descricao: form.descricao.trim(),
    valor: form.valor,
    dia: form.dia,
    frequencia: form.frequencia,
    inicio: form.inicio,
    fim: form.temFim ? form.fim : null,
    observacao: form.observacao.trim() || null,
    confirmarSozinha: form.tipo === 'DESPESA' && form.confirmarSozinha,
  }
  try {
    if (props.recorrencia) {
      await recorrencias.atualizar(props.recorrencia.id, dados)
      emit('salvo', 'Recorrência atualizada. As ocorrências ainda não confirmadas já seguem o novo valor.')
    } else {
      await recorrencias.criar(dados)
      emit('salvo', 'Pronto: ela já aparece como prevista no ano.')
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
  <form class="formulario" novalidate @submit.prevent="salvar">
    <div v-if="!editando" class="segmentos" role="group" aria-label="Tipo">
      <button type="button" :aria-pressed="form.tipo === 'DESPESA'" @click="trocarTipo('DESPESA')">Despesa</button>
      <button type="button" :aria-pressed="form.tipo === 'RECEITA'" @click="trocarTipo('RECEITA')">Receita</button>
    </div>

    <div class="campo">
      <label for="r-descricao">Descrição</label>
      <input id="r-descricao" v-model="form.descricao" class="entrada" maxlength="200" required
             :placeholder="form.tipo === 'RECEITA' ? 'Ex.: salário' : 'Ex.: escola, condomínio, streaming'"
             :aria-invalid="(tentou && !form.descricao.trim()) || undefined" />
      <span v-if="tentou && !form.descricao.trim()" class="erro-campo">Dê um nome para reconhecer depois.</span>
    </div>

    <div class="grade-campos duas">
      <div class="campo">
        <label for="r-valor">Valor de cada vez</label>
        <CampoValor id="r-valor" v-model="form.valor" obrigatorio :invalido="tentou && !form.valor" />
        <span v-if="tentou && !form.valor" class="erro-campo">Informe o valor.</span>
      </div>
      <div class="campo">
        <label for="r-freq">Repete</label>
        <select id="r-freq" v-model="form.frequencia" class="selecao">
          <option v-for="(f, chave) in FREQUENCIAS" :key="chave" :value="chave">{{ f.rotulo }}</option>
        </select>
      </div>
    </div>

    <p v-if="noAno" class="peso num">
      {{ reais(form.valor) }} {{ FREQUENCIAS[form.frequencia].por }} = <strong>{{ reais(noAno) }} no ano</strong>
    </p>

    <div class="campo">
      <label for="r-categoria">Categoria</label>
      <SeletorCategoria id="r-categoria" v-model="form.categoriaId" :tipo="form.tipo"
                        :invalido="tentou && !form.categoriaId" />
      <span v-if="tentou && !form.categoriaId" class="erro-campo">Escolha a categoria.</span>
    </div>

    <div class="grade-campos duas">
      <div class="campo">
        <label for="r-dia">Dia do mês</label>
        <input id="r-dia" v-model.number="form.dia" class="entrada num" type="number" min="1" max="31"
               inputmode="numeric" />
        <span class="ajuda">31 vira o último dia nos meses curtos.</span>
      </div>
      <div class="campo">
        <label for="r-inicio">A partir de</label>
        <input id="r-inicio" v-model="form.inicio" class="entrada" type="month" required />
      </div>
    </div>

    <div class="grade-campos">
      <label class="caixa-marcar">
        <input v-model="form.temFim" type="checkbox" />
        Tem data para acabar
      </label>
      <div v-if="form.temFim" class="campo">
        <label for="r-fim">Último mês</label>
        <input id="r-fim" v-model="form.fim" class="entrada" type="month" />
      </div>
    </div>

    <div v-if="form.tipo === 'DESPESA'" class="sozinha">
      <label class="caixa-marcar">
        <input v-model="form.confirmarSozinha" type="checkbox" aria-describedby="r-sozinha-ajuda" />
        Confirmar sozinha no vencimento
      </label>
      <p id="r-sozinha-ajuda" class="ajuda">
        Para débito automático e conta de valor fixo: no dia do vencimento ela já entra como paga, com o valor
        previsto, e não fica para conferir. Veio diferente? É só abrir o mês na visão anual e corrigir.
        <template v-if="r?.confirmarSozinha && r.confirmarSozinhaDesde && form.confirmarSozinha">
          Ligada desde {{ dataPorExtenso(r.confirmarSozinhaDesde, true) }}.
        </template>
        <template v-else-if="form.confirmarSozinha">
          Vale para o que vence a partir de hoje; o que já venceu continua para conferir.
        </template>
      </p>
    </div>

    <div class="campo">
      <label for="r-pessoa">De quem é</label>
      <select v-if="podeEscolherPessoa" id="r-pessoa" v-model="form.membroId" class="selecao">
        <option v-for="m in membros" :key="m.id" :value="m.id">{{ m.nome }}</option>
      </select>
      <input v-else id="r-pessoa" class="entrada" disabled
             :value="props.recorrencia?.membro?.nome ?? sessao.estado.usuario?.nome" />
    </div>

    <div class="campo">
      <label for="r-obs">Observação <span class="texto-2">(opcional)</span></label>
      <textarea id="r-obs" v-model="form.observacao" class="entrada" maxlength="500" rows="2" />
    </div>

    <Mensagem v-if="editando" tipo="info">
      A mudança vale para as ocorrências ainda não confirmadas. As confirmadas já viraram lançamento e ficam como
      estão.
    </Mensagem>
    <Mensagem v-if="erro" tipo="erro">{{ erro }}</Mensagem>

    <div class="acoes rodape-form">
      <button type="button" class="botao" @click="emit('cancelar')">Cancelar</button>
      <button type="submit" class="botao botao-primario" :disabled="enviando">
        {{ enviando ? 'Salvando…' : 'Salvar' }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.peso {
  margin-top: calc(var(--e-2) * -1);
  padding: var(--e-3) var(--e-4);
  border-radius: var(--raio);
  background: var(--coral-claro);
  outline: 1px dashed var(--coral-borda);
  outline-offset: -1px;
  font-size: var(--t-sm);
}

.sozinha {
  display: grid;
  gap: var(--e-1);
  padding: var(--e-3) var(--e-4);
  border-radius: var(--raio);
  background: var(--areia);
}

.sozinha .ajuda {
  max-width: 52ch;
  padding-left: 1.75rem;
  font-size: var(--t-xs);
  line-height: 1.5;
  color: var(--texto-2);
}

.rodape-form {
  justify-content: flex-end;
  padding-top: var(--e-2);
}
</style>
