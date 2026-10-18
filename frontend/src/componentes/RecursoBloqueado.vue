<script setup lang="ts">
import { Lock } from 'lucide-vue-next'

// O que aparece quando a família está num plano sem o recurso (a API responde 402). Os dados ficam guardados.
defineProps<{ recurso: string; plano: string }>()
</script>

<template>
  <div class="bloqueado painel">
    <span class="icone"><Lock aria-hidden="true" /></span>
    <div>
      <h2>{{ recurso }} faz parte do plano {{ plano }}</h2>
      <p>
        No plano de vocês hoje, esta parte fica fechada. Nada do que já foi cadastrado se perde: quando a família
        mudar de plano, tudo volta a aparecer.
      </p>
      <p class="texto-2">Por enquanto a mudança de plano é feita pela equipe do Familiança.</p>
    </div>
  </div>
</template>

<style scoped>
.bloqueado {
  display: flex;
  gap: var(--e-4);
  padding: var(--e-6);
  max-width: 44rem;
}

.icone {
  display: grid;
  place-items: center;
  flex: none;
  width: 2.75rem;
  height: 2.75rem;
  border-radius: 50%;
  background: var(--petroleo-tinta);
  color: var(--petroleo);
}

.icone svg {
  width: 1.25rem;
  height: 1.25rem;
}

h2 {
  margin-bottom: var(--e-2);
}

p + p {
  margin-top: var(--e-2);
  font-size: var(--t-sm);
}
</style>
