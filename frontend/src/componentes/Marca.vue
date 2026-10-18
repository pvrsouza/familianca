<script setup lang="ts">
// Símbolo provisório "a copa do ano": a copa da árvore são as 12 barras do ano (as 3 últimas, previstas, em
// coral claro) sobre um tronco. O desenho final vem do designer (projeto/marca.md, próximos passos).
withDefaults(defineProps<{ claro?: boolean; tamanho?: number; comNome?: boolean }>(), {
  claro: false,
  tamanho: 28,
  comNome: true,
})

const alturas = [5, 8, 10.5, 12.5, 14, 15, 15, 14, 12.5, 10.5, 8, 5]
</script>

<template>
  <span class="marca" :class="{ claro }">
    <svg :width="tamanho" :height="tamanho" viewBox="0 0 32 32" aria-hidden="true">
      <rect
        v-for="(h, i) in alturas"
        :key="i"
        :x="3.1 + i * 2.25"
        :y="19 - h"
        width="1.5"
        :height="h"
        rx="0.75"
        :class="i >= 9 ? 'prevista' : 'realizada'"
      />
      <rect x="15.1" y="19.5" width="1.8" height="8.5" rx="0.9" class="tronco" />
    </svg>
    <span v-if="comNome" class="nome">Familiança</span>
  </span>
</template>

<style scoped>
.marca {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--petroleo);
}

.realizada,
.tronco {
  fill: currentColor;
}

.prevista {
  fill: var(--coral);
}

.claro {
  color: var(--areia);
}

.claro .prevista {
  fill: var(--coral-sobre-escuro);
}

.nome {
  font-family: var(--fonte-titulo);
  font-weight: 600;
  font-size: 1.25rem;
  letter-spacing: -0.01em;
  line-height: 1;
}
</style>
