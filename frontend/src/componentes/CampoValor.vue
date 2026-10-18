<script setup lang="ts">
import { ref, watch } from 'vue'
import { lerValor, numeroBr } from '../util/formato'

// Valor em reais: aceita "1.234,56" ou "1234.56"; o v-model é o número (ou nulo).
const props = defineProps<{ modelValue: number | null; id?: string; obrigatorio?: boolean; invalido?: boolean; negativo?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [number | null] }>()
const texto = ref(numeroBr(props.modelValue))

watch(
  () => props.modelValue,
  (v) => {
    if (lerValor(texto.value) !== v) texto.value = numeroBr(v)
  },
)

function digitar(e: Event) {
  const bruto = (e.target as HTMLInputElement).value
  texto.value = bruto
  const negativo = props.negativo && bruto.trim().startsWith('-')
  const v = lerValor(bruto.replace('-', ''))
  emit('update:modelValue', v === null ? null : negativo ? -v : v)
}

function formatar() {
  if (props.modelValue !== null) texto.value = numeroBr(props.modelValue)
}
</script>

<template>
  <div class="entrada-valor">
    <input
      :id="id"
      class="entrada"
      type="text"
      inputmode="decimal"
      autocomplete="off"
      placeholder="0,00"
      :value="texto"
      :required="obrigatorio"
      :aria-invalid="invalido || undefined"
      maxlength="16"
      @input="digitar"
      @blur="formatar"
    />
  </div>
</template>
