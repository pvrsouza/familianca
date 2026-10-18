<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ErroApi, mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import LayoutAcesso from '../componentes/LayoutAcesso.vue'
import CampoSenha from '../componentes/CampoSenha.vue'
import Mensagem from '../componentes/Mensagem.vue'

const route = useRoute()
const router = useRouter()
const email = ref('')
const senha = ref('')
const codigo = ref('')
const pedirCodigo = ref(false)
const enviando = ref(false)
const erro = ref('')
const expirou = computed(() => route.query.expirou === '1')
const campoCodigo = ref<HTMLInputElement | null>(null)

/** Só caminhos internos: nada de redirecionar para outro site. */
function destino(): string {
  const volta = route.query.volta
  return typeof volta === 'string' && volta.startsWith('/') && !volta.startsWith('//') ? volta : '/ano'
}

async function entrar() {
  erro.value = ''
  if (!email.value.trim() || !senha.value) {
    erro.value = 'Informe o e-mail e a senha.'
    return
  }
  enviando.value = true
  try {
    await sessao.entrar(email.value.trim(), senha.value, pedirCodigo.value ? codigo.value.trim() : undefined)
    router.replace(destino())
  } catch (e) {
    if (e instanceof ErroApi && e.problema.mfaObrigatorio) {
      pedirCodigo.value = true
      await nextTick()
      campoCodigo.value?.focus()
    } else {
      erro.value = e instanceof ErroApi && e.status === 401
        ? 'E-mail ou senha não conferem. Confira e tente de novo.'
        : mensagemDeErro(e)
      if (pedirCodigo.value) codigo.value = ''
    }
  } finally {
    enviando.value = false
  }
}

function voltarParaSenha() {
  pedirCodigo.value = false
  codigo.value = ''
  erro.value = ''
}
</script>

<template>
  <LayoutAcesso :titulo="pedirCodigo ? 'Código de verificação' : 'Entrar'"
                :subtitulo="pedirCodigo ? 'Abra o aplicativo autenticador e digite o código de 6 dígitos. Sem o celular? Use um dos códigos de recuperação.' : 'Que bom ver vocês de novo.'">
    <form class="formulario" novalidate @submit.prevent="entrar">
      <Mensagem v-if="expirou && !erro">Sua sessão terminou. Entre de novo para continuar de onde parou.</Mensagem>

      <template v-if="!pedirCodigo">
        <div class="campo">
          <label for="email">E-mail</label>
          <input id="email" v-model="email" class="entrada" type="email" autocomplete="username" inputmode="email"
                 maxlength="200" required autofocus />
        </div>
        <div class="campo">
          <label for="senha">Senha</label>
          <CampoSenha id="senha" v-model="senha" autocomplete="current-password" />
        </div>
      </template>
      <div v-else class="campo">
        <label for="codigo">Código</label>
        <input id="codigo" ref="campoCodigo" v-model="codigo" class="entrada num codigo" autocomplete="one-time-code"
               inputmode="text" maxlength="20" required />
      </div>

      <Mensagem v-if="erro" tipo="erro">{{ erro }}</Mensagem>

      <button type="submit" class="botao botao-primario largo" :disabled="enviando">
        {{ enviando ? 'Entrando…' : pedirCodigo ? 'Confirmar e entrar' : 'Entrar' }}
      </button>
      <button v-if="pedirCodigo" type="button" class="botao botao-sutil" @click="voltarParaSenha">
        Voltar e usar outro e-mail
      </button>

      <p v-if="!pedirCodigo" class="rodape">
        Ainda não têm conta? <RouterLink to="/criar-conta">Criar a conta da família</RouterLink>
      </p>
      <p v-if="!pedirCodigo" class="rodape texto-2">
        Recebeu um convite? Abra o link que mandaram para você.
      </p>
    </form>
  </LayoutAcesso>
</template>

<style scoped>
.largo {
  width: 100%;
  min-height: 3rem;
  font-size: var(--t-md);
}

.codigo {
  font-size: var(--t-xl);
  letter-spacing: 0.2em;
  text-align: center;
}

.rodape {
  font-size: var(--t-sm);
  text-align: center;
}

.rodape + .rodape {
  margin-top: calc(var(--e-3) * -1);
}
</style>
