<script setup lang="ts">
import { useRouter } from 'vue-router'
import { sessao } from '../estado/sessao'
import Mensagem from './Mensagem.vue'

// No lugar de um formulário de lançamento ou recorrência enquanto a família não informou o saldo inicial: nada se
// lança antes dele (o servidor também barra). Quem organiza vai direto para o painel do saldo; quem participa sabe
// quem precisa fazer. "sair" fecha o painel onde o formulário estava.
const emit = defineEmits<{ sair: [] }>()
const router = useRouter()

function informar() {
  emit('sair')
  router.push({ path: '/ano', query: { saldo: '1' } })
}
</script>

<template>
  <div class="saldo-primeiro">
    <Mensagem>
      <strong>Primeiro, o saldo de hoje.</strong>
      <template v-if="sessao.dono.value">
        Antes do primeiro lançamento, informem quanto a família tem hoje: contas, poupança, dinheiro na mão. Pode ser
        zero ou negativo. Ele é o ponto de partida do fluxo de caixa.
      </template>
      <template v-else>
        Antes do primeiro lançamento, quem organiza a família precisa informar quanto vocês têm hoje. É o ponto de
        partida do fluxo de caixa; peçam para essa pessoa entrar e fazer isso primeiro.
      </template>
    </Mensagem>
    <div class="acoes">
      <button v-if="sessao.dono.value" type="button" class="botao botao-primario" @click="informar">Informar saldo</button>
      <button type="button" class="botao" @click="emit('sair')">Fechar</button>
    </div>
  </div>
</template>

<style scoped>
.saldo-primeiro {
  display: grid;
  gap: var(--e-4);
}
</style>
