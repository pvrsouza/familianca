<script setup lang="ts">
import { CircleAlert, CircleCheck, Info } from 'lucide-vue-next'

// Mensagem em linha. Erro sempre com ícone e texto (nunca só cor); coral nunca é erro.
withDefaults(defineProps<{ tipo?: 'info' | 'erro' | 'sucesso' }>(), { tipo: 'info' })
</script>

<template>
  <div class="aviso" :class="{ 'aviso-erro': tipo === 'erro', 'aviso-sucesso': tipo === 'sucesso' }"
       :role="tipo === 'erro' ? 'alert' : 'status'">
    <CircleAlert v-if="tipo === 'erro'" aria-hidden="true" />
    <CircleCheck v-else-if="tipo === 'sucesso'" aria-hidden="true" />
    <Info v-else aria-hidden="true" />
    <div><slot /></div>
    <div v-if="$slots.acoes" class="acoes"><slot name="acoes" /></div>
  </div>
</template>
