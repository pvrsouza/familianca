<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { lugaresSaldo } from '../api/servicos'
import type { LugarSaldo } from '../api/tipos'
import { sessao } from '../estado/sessao'
import { ultimaConta } from '../util/ultimaConta'

// "Conta (opcional)": o lugar onde está o dinheiro de um lançamento ou de uma recorrência. Só aparece se a família já
// cadastrou alguma conta que a pessoa pode usar (ativa, e, para MEMBRO, dela ou da família toda).
// contaAtual: a que o registro já tem; aparece mesmo desativada, para não sumir sem aviso.
// lembrar: no registro novo, já vem a última conta usada por esta pessoa neste navegador.
const props = defineProps<{
  id: string; contaAtual?: number | null; lembrar?: boolean; rotulo?: string; ajuda?: string
  /** Sem a opção "Sem conta" (ex.: trocar a conta de um mês só vale para outra conta). */
  obrigatoria?: boolean
}>()
const conta = defineModel<number | null>({ default: null })

const contas = ref<LugarSaldo[]>([])
const eu = computed(() => sessao.estado.usuario)
const permitidas = computed(() => contas.value.filter((c) =>
  (c.ativo || c.id === props.contaAtual) && (sessao.dono.value || c.membroId === null || c.membroId === eu.value?.membroId)))

onMounted(async () => {
  try {
    contas.value = await lugaresSaldo.listar()
  } catch {
    contas.value = [] // sem a lista, o registro segue sem conta
  }
  if (props.lembrar && conta.value === null) {
    const ultima = ultimaConta(eu.value?.membroId)
    // A última pode ter sido desativada ou ser de outra pessoa: então começa vazio.
    if (ultima && permitidas.value.some((c) => c.id === ultima)) conta.value = ultima
  }
})
</script>

<template>
  <div v-if="permitidas.length" class="campo">
    <label :for="id">{{ rotulo ?? 'Conta' }} <span v-if="!obrigatoria" class="texto-2">(opcional)</span></label>
    <select :id="id" v-model="conta" class="selecao" :aria-describedby="`${id}-ajuda`">
      <option v-if="!obrigatoria" :value="null">Sem conta</option>
      <option v-for="c in permitidas" :key="c.id" :value="c.id">{{ c.nome }}{{ c.ativo ? '' : ' (desativada)' }}</option>
    </select>
    <small :id="`${id}-ajuda`" class="texto-2">
      {{ ajuda ?? 'De qual conta o dinheiro saiu ou entrou. Ajuda a conferir com o extrato do banco.' }}
    </small>
  </div>
</template>
