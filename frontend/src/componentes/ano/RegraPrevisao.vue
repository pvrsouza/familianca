<script setup lang="ts">
import { computed } from 'vue'
import type { BaseEstimativa } from '../../api/tipos'
import { explicarTrecho } from '../../util/formato'

// Diz como o valor deste mês foi previsto: a regra, de quem ela é e os meses usados. Só explica; a regra muda no nome
// da despesa (na tabela do ano) ou em Categorias.
const props = defineProps<{
  /** O trecho da previsão que vale no mês; nulo = sem estimativa. */
  trecho: BaseEstimativa | null
  /** É uma conta que se repete (sem estimativa, vale o cadastrado). */
  conta?: boolean
  cadastrado?: number
}>()

const texto = computed(() => (props.trecho ? explicarTrecho(props.trecho, !!props.conta, props.cadastrado) : ''))
</script>

<template>
  <p v-if="texto" class="texto-2 regra">
    {{ texto }} <span class="dica">Para mudar, clique no nome da despesa na tabela.</span>
  </p>
</template>

<style scoped>
.regra {
  font-size: var(--t-xs);
}
</style>
