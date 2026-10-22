<script setup lang="ts">
import { watch } from 'vue'

// Competência e emissão da nota fiscal (receita tributável). Os dois são opcionais e só da nota: o lançamento conta
// no mês da data do pagamento. Com a emissão e sem competência, a competência sugere o mês da emissão.
const competencia = defineModel<string>('competencia', { required: true })
const emissao = defineModel<string>('emissao', { required: true })
defineProps<{ idBase: string }>()

watch(emissao, (e) => {
  if (e && !competencia.value) competencia.value = e.slice(0, 7)
})
</script>

<template>
  <div class="grade-campos duas">
    <div class="campo">
      <label :for="`${idBase}-nf-competencia`">Competência <span class="texto-2">(opcional)</span></label>
      <input :id="`${idBase}-nf-competencia`" v-model="competencia" class="entrada" type="month"
             :aria-describedby="`${idBase}-nf-ajuda`" />
    </div>
    <div class="campo">
      <label :for="`${idBase}-nf-emissao`">Emissão da nota <span class="texto-2">(opcional)</span></label>
      <input :id="`${idBase}-nf-emissao`" v-model="emissao" class="entrada" type="date"
             :aria-describedby="`${idBase}-nf-ajuda`" />
    </div>
  </div>
  <p :id="`${idBase}-nf-ajuda`" class="ajuda">
    Competência é o mês do serviço da nota. Na conta do mês vale o dia em que o dinheiro entrou na conta: é nele que a
    nota entra na renda e na base dos impostos, mesmo que tenha sido emitida antes.
  </p>
</template>

<style scoped>
.ajuda {
  margin-top: calc(-1 * var(--e-2));
  font-size: var(--t-xs);
  line-height: 1.45;
  color: var(--texto-2);
}
</style>
