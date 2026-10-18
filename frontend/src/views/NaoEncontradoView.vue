<script setup lang="ts">
import { RouterLink } from 'vue-router'
import EstadoVazio from '../componentes/EstadoVazio.vue'
</script>

<template>
  <div class="pagina">
    <EstadoVazio titulo="Esta página não existe">
      O endereço pode ter mudado ou estar incompleto.
      <template #acoes>
        <RouterLink to="/ano" class="botao botao-primario">Voltar para o ano</RouterLink>
      </template>
    </EstadoVazio>
  </div>
</template>
