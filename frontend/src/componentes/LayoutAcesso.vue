<script setup lang="ts">
import Marca from './Marca.vue'

// Moldura das telas sem login: a marca à esquerda (no celular, em cima) e o formulário à direita.
defineProps<{ titulo: string; subtitulo?: string }>()
const alturas = [38, 52, 64, 74, 82, 88, 88, 82, 74, 64, 52, 38]
const meses = ['j', 'f', 'm', 'a', 'm', 'j', 'j', 'a', 's', 'o', 'n', 'd']
</script>

<template>
  <div class="acesso">
    <aside class="lado-marca">
      <Marca claro :tamanho="34" />
      <div class="copa" aria-hidden="true">
        <div v-for="(h, i) in alturas" :key="i" class="coluna">
          <span class="barra" :class="{ prevista: i >= 9 }" :style="{ height: h + '%', animationDelay: i * 40 + 'ms' }" />
          <span class="mes">{{ meses[i] }}</span>
        </div>
      </div>
      <div class="frase">
        <p class="lema">O orçamento é de todos.</p>
        <p>A família inteira vê o ano, entende o peso de cada gasto e decide junto.</p>
      </div>
    </aside>
    <section class="lado-form">
      <div class="form-caixa">
        <h1>{{ titulo }}</h1>
        <p v-if="subtitulo" class="subtitulo">{{ subtitulo }}</p>
        <slot />
      </div>
    </section>
  </div>
</template>

<style scoped>
.acesso {
  display: grid;
  grid-template-columns: minmax(20rem, 5fr) 7fr;
  min-height: 100dvh;
}

.lado-marca {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: var(--e-10);
  padding: var(--e-10);
  background: var(--petroleo);
  color: var(--areia);
}

.copa {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  align-items: end;
  gap: clamp(4px, 1vw, 12px);
  height: min(36vh, 18rem);
  max-width: 26rem;
}

.coluna {
  display: grid;
  grid-template-rows: 1fr auto;
  height: 100%;
  gap: var(--e-2);
}

.barra {
  align-self: end;
  width: 100%;
  border-radius: 999px;
  background: var(--areia);
  animation: crescer 700ms var(--ease) both;
}

.barra.prevista {
  background: transparent;
  border: 1.5px dashed var(--coral-sobre-escuro);
  background-color: rgb(244 181 163 / 0.18);
}

.mes {
  text-align: center;
  font-size: var(--t-xs);
  color: rgb(246 244 240 / 0.7);
  text-transform: uppercase;
}

@keyframes crescer {
  from {
    transform: scaleY(0.2);
    transform-origin: bottom;
    opacity: 0;
  }
}

.lema {
  font-family: var(--fonte-titulo);
  font-weight: 500;
  font-size: var(--t-3xl);
  line-height: 1.1;
  margin-bottom: var(--e-3);
  text-wrap: balance;
}

.frase p:last-child {
  max-width: 34ch;
  color: rgb(246 244 240 / 0.85);
}

.lado-form {
  display: grid;
  place-items: center;
  padding: var(--e-10) var(--e-6);
}

.form-caixa {
  width: 100%;
  max-width: 26rem;
}

.form-caixa h1 {
  font-size: var(--t-2xl);
}

.subtitulo {
  margin-top: var(--e-2);
  color: var(--texto-2);
}

.form-caixa :deep(.formulario) {
  margin-top: var(--e-8);
}

@media (max-width: 860px) {
  .acesso {
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr;
  }

  .lado-marca {
    padding: var(--e-5) var(--e-5) var(--e-6);
    gap: var(--e-5);
  }

  .copa {
    height: 4.5rem;
    max-width: 16rem;
    gap: 4px;
  }

  .mes {
    display: none;
  }

  .lema {
    font-size: var(--t-xl);
    margin-bottom: var(--e-1);
  }

  .frase p:last-child {
    font-size: var(--t-sm);
  }

  .lado-form {
    place-items: start center;
    padding: var(--e-8) var(--e-5) var(--e-10);
  }
}
</style>
