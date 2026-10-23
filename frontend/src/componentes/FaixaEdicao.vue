<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { X } from 'lucide-vue-next'

// Faixa que abre dentro da tabela, logo abaixo da linha clicada, em vez do painel lateral: a pessoa continua vendo
// a linha e o mês, e nada fica por cima da tabela. Fecha no X ou com Esc; o foco volta para onde estava.
// Mesma interface do PainelLateral (aberto, titulo, descricao, fechar), para quem usa escolher um ou outro.
const props = defineProps<{ aberto: boolean; titulo: string; descricao?: string }>()
const emit = defineEmits<{ fechar: [] }>()

const cabecalho = ref<HTMLElement | null>(null)
let antes: HTMLElement | null = null

function tecla(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    e.preventDefault()
    emit('fechar')
  }
}

watch(
  () => props.aberto,
  async (aberto) => {
    document.removeEventListener('keydown', tecla)
    if (!aberto) {
      if (antes?.isConnected) antes.focus()
      antes = null
      return
    }
    antes = document.activeElement instanceof HTMLElement ? document.activeElement : null
    document.addEventListener('keydown', tecla)
    await nextTick()
    cabecalho.value?.focus({ preventScroll: true })
    cabecalho.value?.closest('section')?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', tecla)
  if (antes?.isConnected) antes.focus()
})
</script>

<template>
  <section v-if="aberto" class="faixa-edicao" role="region" aria-labelledby="faixa-titulo">
    <header>
      <div>
        <h2 id="faixa-titulo" ref="cabecalho" tabindex="-1">{{ titulo }}</h2>
        <p v-if="descricao">{{ descricao }}</p>
      </div>
      <button type="button" class="botao botao-sutil botao-icone" aria-label="Fechar" @click="emit('fechar')">
        <X />
      </button>
    </header>
    <div class="corpo">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.faixa-edicao {
  text-align: left;
  white-space: normal;
  font-size: var(--t-sm);
  animation: abrir 160ms var(--ease);
}

header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--e-2);
  margin-bottom: var(--e-3);
}

header h2 {
  font-family: var(--fonte-titulo);
  font-weight: 500;
  font-size: var(--t-lg);
  outline: none;
}

header p {
  margin-top: 2px;
  color: var(--texto-2);
  font-size: var(--t-xs);
}

@keyframes abrir {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .faixa-edicao {
    animation: none;
  }
}
</style>
