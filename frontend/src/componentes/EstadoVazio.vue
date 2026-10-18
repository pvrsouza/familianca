<script setup lang="ts">
defineProps<{ titulo: string }>()
</script>

<template>
  <div class="vazio">
    <svg width="64" height="40" viewBox="0 0 64 40" aria-hidden="true">
      <rect v-for="i in 12" :key="i" :x="(i - 1) * 5.4" :y="40 - (8 + ((i * 7) % 5) * 5)" width="3.4"
            :height="8 + ((i * 7) % 5) * 5" rx="1.7" :class="i > 9 ? 'p' : 'r'" />
    </svg>
    <h3>{{ titulo }}</h3>
    <p><slot /></p>
    <div v-if="$slots.acoes" class="acoes"><slot name="acoes" /></div>
  </div>
</template>

<style scoped>
.vazio {
  display: grid;
  justify-items: center;
  gap: var(--e-2);
  padding: var(--e-10) var(--e-6);
  text-align: center;
}

.vazio svg {
  margin-bottom: var(--e-2);
}

.r {
  fill: var(--petroleo-tinta-forte);
}

.p {
  fill: var(--coral-claro);
  stroke: var(--coral-borda);
  stroke-width: 0.75;
  stroke-dasharray: 2 1.5;
}

h3 {
  font-size: var(--t-lg);
}

p {
  max-width: 46ch;
  color: var(--texto-2);
  font-size: var(--t-sm);
}

.acoes {
  margin-top: var(--e-3);
  justify-content: center;
}
</style>
