<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { X } from 'lucide-vue-next'

// Painel que entra pela direita (no celular, sobe de baixo), feito com <dialog>: foco preso, Esc fecha e o
// resto da página fica inerte.
const props = defineProps<{ aberto: boolean; titulo: string; descricao?: string }>()
const emit = defineEmits<{ fechar: [] }>()
const dialogo = ref<HTMLDialogElement | null>(null)

watch(
  () => props.aberto,
  async (aberto) => {
    await nextTick()
    const d = dialogo.value
    if (!d) return
    if (aberto && !d.open) {
      d.showModal()
      document.documentElement.style.overflow = 'hidden'
    } else if (!aberto && d.open) {
      d.close()
    }
  },
  { immediate: true },
)

function aoFechar() {
  document.documentElement.style.overflow = ''
  if (props.aberto) emit('fechar')
}

function cliqueFora(e: MouseEvent) {
  if (e.target === dialogo.value) emit('fechar')
}

onBeforeUnmount(() => {
  document.documentElement.style.overflow = ''
})
</script>

<template>
  <dialog ref="dialogo" class="painel-lateral" :aria-label="titulo" @close="aoFechar" @click="cliqueFora">
    <div class="conteudo">
      <header>
        <div>
          <h2>{{ titulo }}</h2>
          <p v-if="descricao">{{ descricao }}</p>
        </div>
        <button type="button" class="botao botao-sutil botao-icone" aria-label="Fechar" @click="emit('fechar')">
          <X />
        </button>
      </header>
      <div class="corpo">
        <slot />
      </div>
      <footer v-if="$slots.rodape">
        <slot name="rodape" />
      </footer>
    </div>
  </dialog>
</template>

<style scoped>
.painel-lateral {
  margin: 0 0 0 auto;
  padding: 0;
  width: min(30rem, 100vw);
  max-width: 100vw;
  height: 100dvh;
  max-height: 100dvh;
  border: 0;
  background: var(--papel);
  color: var(--texto);
  box-shadow: var(--sombra-painel);
}

.painel-lateral[open] {
  animation: entrar 260ms var(--ease);
}

.painel-lateral::backdrop {
  background: rgb(18 58 65 / 0.32);
  animation: escurecer 200ms ease-out;
}

.conteudo {
  display: flex;
  flex-direction: column;
  height: 100%;
}

header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--e-3);
  padding: var(--e-5) var(--e-5) var(--e-4) var(--e-6);
  border-bottom: 1px solid var(--linha);
}

header h2 {
  font-family: var(--fonte-titulo);
  font-weight: 500;
  font-size: var(--t-xl);
}

header p {
  margin-top: var(--e-1);
  color: var(--texto-2);
  font-size: var(--t-sm);
}

.corpo {
  flex: 1;
  overflow-y: auto;
  padding: var(--e-6);
  overscroll-behavior: contain;
}

footer {
  display: flex;
  gap: var(--e-2);
  justify-content: flex-end;
  padding: var(--e-4) var(--e-6) calc(var(--e-4) + env(safe-area-inset-bottom));
  border-top: 1px solid var(--linha);
  background: var(--papel);
}

@keyframes entrar {
  from {
    transform: translateX(2.5rem);
    opacity: 0;
  }
}

@keyframes escurecer {
  from {
    opacity: 0;
  }
}

@media (max-width: 640px) {
  .painel-lateral {
    margin: auto 0 0;
    width: 100vw;
    height: auto;
    max-height: 92dvh;
    border-radius: var(--raio-g) var(--raio-g) 0 0;
  }

  .painel-lateral[open] {
    animation-name: subir;
  }

  footer > :deep(*) {
    flex: 1;
  }

  @keyframes subir {
    from {
      transform: translateY(2.5rem);
      opacity: 0;
    }
  }
}
</style>
