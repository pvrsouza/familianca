<script setup lang="ts">
import { computed, ref } from 'vue'
import { lugaresSaldo } from '../api/servicos'
import type { LugarSaldo, TipoLugarSaldo } from '../api/tipos'
import { mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import { TIPOS_LUGAR_SALDO } from '../util/formato'
import Mensagem from './Mensagem.vue'

// Incluir ou editar um lugar onde está o dinheiro (conta, poupança, dinheiro na mão, cofrinho...). Não é um <form>:
// aparece dentro do formulário do saldo, e formulário dentro de formulário não vale. Enter no nome salva.
const props = defineProps<{ lugar?: LugarSaldo | null; idBase: string }>()
const emit = defineEmits<{ salvo: [LugarSaldo]; cancelar: [] }>()

const nome = ref(props.lugar?.nome ?? '')
const tipo = ref<TipoLugarSaldo>(props.lugar?.tipo ?? 'CONTA_CORRENTE')
const membroId = ref<number | null>(props.lugar?.membroId ?? null)
const erro = ref('')
const salvando = ref(false)

// Quem já saiu da família continua aparecendo se já era o dono do lugar.
const membros = computed(() =>
  (sessao.estado.familia?.membros ?? []).filter((m) => m.ativo || m.id === props.lugar?.membroId),
)
const explica = computed(() => TIPOS_LUGAR_SALDO[tipo.value])

async function salvar() {
  if (!nome.value.trim()) {
    erro.value = 'Dê um nome para reconhecer o lugar.'
    return
  }
  salvando.value = true
  erro.value = ''
  try {
    const dados = { nome: nome.value.trim(), tipo: tipo.value, membroId: membroId.value }
    const salvo = props.lugar
      ? await lugaresSaldo.atualizar(props.lugar.id, { ...dados, ativo: props.lugar.ativo })
      : await lugaresSaldo.criar(dados)
    emit('salvo', salvo)
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    salvando.value = false
  }
}
</script>

<template>
  <div class="form-lugar" role="group" :aria-label="lugar ? `Editar ${lugar.nome}` : 'Incluir lugar'">
    <div class="campo">
      <label :for="`${idBase}-tipo`">O que é</label>
      <select :id="`${idBase}-tipo`" v-model="tipo" class="entrada selecao">
        <option v-for="(t, chave) in TIPOS_LUGAR_SALDO" :key="chave" :value="chave">{{ t.rotulo }}</option>
      </select>
      <span class="ajuda">{{ explica.explica }}</span>
    </div>
    <div class="campo">
      <label :for="`${idBase}-nome`">Nome</label>
      <input :id="`${idBase}-nome`" v-model="nome" class="entrada" maxlength="80" :placeholder="explica.exemplo"
             @keydown.enter.prevent="salvar" />
    </div>
    <div class="campo">
      <label :for="`${idBase}-dono`">De quem é</label>
      <select :id="`${idBase}-dono`" v-model="membroId" class="entrada selecao">
        <option :value="null">Da família toda (ou de quem não usa o Familiança)</option>
        <option v-for="m in membros" :key="m.id" :value="m.id">{{ m.nome }}</option>
      </select>
      <span class="ajuda">Para uma criança ou alguém sem acesso, escolham "da família toda" e ponham o nome da pessoa no
        nome do lugar.</span>
    </div>
    <Mensagem v-if="erro" tipo="erro">{{ erro }}</Mensagem>
    <div class="acoes">
      <button type="button" class="botao botao-primario botao-pequeno" :disabled="salvando" @click="salvar">
        {{ lugar ? 'Salvar' : 'Incluir' }}
      </button>
      <button type="button" class="botao botao-pequeno" @click="emit('cancelar')">Cancelar</button>
    </div>
  </div>
</template>

<style scoped>
.form-lugar {
  display: grid;
  gap: var(--e-3);
  padding: var(--e-3);
  border: 1px dashed var(--pedra-forte);
  border-radius: var(--raio);
  background: var(--papel);
}
</style>
