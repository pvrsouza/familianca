<script setup lang="ts">
import { TriangleAlert } from 'lucide-vue-next'

// Aviso dos lançamentos que ficaram na categoria "A classificar" (vindos da importação de extrato). Usado em
// Lançamentos e em O ano; a ação (ver só esses / abrir a lista) é de quem usa.
defineProps<{ quantidade: number; periodo: string }>()
</script>

<template>
  <div class="aviso aviso-classificar" role="status">
    <TriangleAlert aria-hidden="true" />
    <p v-if="quantidade">
      <strong>{{ quantidade }} {{ quantidade === 1 ? 'lançamento está' : 'lançamentos estão' }} sem categoria</strong>
      {{ periodo }}. Abra cada um e escolha a categoria certa para entrar nas contas do ano.
    </p>
    <p v-else>Nenhum lançamento sem categoria {{ periodo }}.</p>
    <div v-if="$slots.default" class="acoes"><slot /></div>
  </div>
</template>

<style scoped>
.aviso-classificar { align-items: center; margin-bottom: var(--e-3); }
.aviso-classificar p { flex: 1; }
</style>
