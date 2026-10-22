<script setup lang="ts">
import { useRouter } from 'vue-router'
import { sessao } from '../estado/sessao'
import { MESES } from '../util/formato'
import Mensagem from './Mensagem.vue'

// No lugar de um formulário de lançamento ou recorrência enquanto a família não informou o saldo inicial: nada se
// lança antes dele (o servidor também barra). Quem organiza vai direto para o painel do saldo; quem participa sabe
// quem precisa fazer. "sair" fecha o painel onde o formulário estava.
const emit = defineEmits<{ sair: [] }>()
const router = useRouter()
const mesAtual = new Date().getMonth()

function informar() {
  emit('sair')
  router.push({ path: '/ano', query: { saldo: '1' } })
}
</script>

<template>
  <div class="saldo-primeiro">
    <Mensagem>
      <strong>Primeiro, o saldo de partida.</strong>
      <template v-if="sessao.dono.value">
        Antes do primeiro lançamento, informem quanto a família tinha no começo de um mês, somando contas, poupança e
        dinheiro na mão. Pode ser o mês atual ou qualquer mês que já passou, e o valor pode ser zero ou negativo. A
        partir dele, cada mês começa com a sobra do anterior.
      </template>
      <template v-else>
        Antes do primeiro lançamento, quem organiza a família precisa informar quanto vocês tinham no começo de um mês
        (o atual ou um que já passou). É o ponto de partida do fluxo de caixa; peçam para essa pessoa entrar e fazer
        isso primeiro.
      </template>
    </Mensagem>
    <ul v-if="sessao.dono.value" class="exemplos">
      <li>
        <strong>Começando agora:</strong> informem o saldo de 1º de {{ MESES[mesAtual] }} e lancem o que entrou e saiu
        desde então.
      </li>
      <li v-if="mesAtual > 0">
        <strong>Querem o ano inteiro:</strong> informem o saldo de 1º de janeiro e lancem a partir de janeiro.
      </li>
      <li>
        <strong>No cheque especial:</strong> o saldo entra negativo, por exemplo −R$ 350,00.
      </li>
    </ul>
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

.exemplos {
  display: grid;
  gap: var(--e-2);
  margin: 0;
  padding-left: var(--e-5);
}
</style>
