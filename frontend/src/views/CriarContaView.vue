<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { auth } from '../api/servicos'
import { ErroApi, mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import { primeiroNome } from '../util/formato'
import LayoutAcesso from '../componentes/LayoutAcesso.vue'
import CampoSenha from '../componentes/CampoSenha.vue'
import Mensagem from '../componentes/Mensagem.vue'

const router = useRouter()
const form = reactive({ nome: '', email: '', senha: '', nomeFamilia: '' })
const enviando = ref(false)
const erro = ref('')
const tentou = ref(false)
const emailEmUso = ref(false)

const sugestaoFamilia = computed(() =>
  form.nome.trim() ? `Família de ${primeiroNome(form.nome)}` : 'Ex.: Família Souza',
)
const senhaCurta = computed(() => form.senha.length > 0 && form.senha.length < 12)
const emailValido = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()))

async function criar() {
  tentou.value = true
  erro.value = ''
  emailEmUso.value = false
  if (!form.nome.trim() || !emailValido.value || form.senha.length < 12) return
  enviando.value = true
  try {
    await auth.cadastrar({
      nome: form.nome.trim(),
      email: form.email.trim(),
      senha: form.senha,
      nomeFamilia: form.nomeFamilia.trim() || undefined,
    })
    await sessao.entrar(form.email.trim(), form.senha)
    router.replace({ path: '/ano', query: { bemvindo: '1' } })
  } catch (e) {
    if (e instanceof ErroApi && e.status === 409) emailEmUso.value = true
    else erro.value = mensagemDeErro(e)
  } finally {
    enviando.value = false
  }
}
</script>

<template>
  <LayoutAcesso titulo="Criar a conta da família"
                subtitulo="Vocês começam com o plano completo por 30 dias, sem cartão. As categorias básicas já vêm prontas.">
    <form class="formulario" novalidate @submit.prevent="criar">
      <div class="campo">
        <label for="nome">Seu nome</label>
        <input id="nome" v-model="form.nome" class="entrada" autocomplete="name" maxlength="200" required
               :aria-invalid="(tentou && !form.nome.trim()) || undefined" />
        <span v-if="tentou && !form.nome.trim()" class="erro-campo">Como podemos chamar você?</span>
      </div>
      <div class="campo">
        <label for="email">E-mail</label>
        <input id="email" v-model="form.email" class="entrada" type="email" autocomplete="email" inputmode="email"
               maxlength="200" required :aria-invalid="(tentou && !emailValido) || undefined" />
        <span v-if="tentou && !emailValido" class="erro-campo">Confira o e-mail: ele será o seu login.</span>
      </div>
      <div class="campo">
        <label for="senha">Senha</label>
        <CampoSenha id="senha" v-model="form.senha" autocomplete="new-password" :minimo="12"
                    :invalido="(tentou && form.senha.length < 12) || undefined" />
        <span class="ajuda" :class="{ 'erro-campo': tentou && form.senha.length < 12 }">
          De 12 a 64 caracteres.<template v-if="senhaCurta"> Faltam {{ 12 - form.senha.length }}.</template>
          Uma frase que só vocês lembram funciona bem.
        </span>
      </div>
      <div class="campo">
        <label for="familia">Nome da família <span class="texto-2">(opcional)</span></label>
        <input id="familia" v-model="form.nomeFamilia" class="entrada" maxlength="100" :placeholder="sugestaoFamilia" />
        <span class="ajuda">Dá para mudar depois.</span>
      </div>

      <Mensagem v-if="emailEmUso" tipo="erro">
        Este e-mail já tem conta no Familiança. <RouterLink to="/entrar">Entrar com ele</RouterLink>
      </Mensagem>
      <Mensagem v-if="erro" tipo="erro">{{ erro }}</Mensagem>

      <button type="submit" class="botao botao-primario largo" :disabled="enviando">
        {{ enviando ? 'Criando a conta…' : 'Criar conta' }}
      </button>
      <p class="rodape">Já têm conta? <RouterLink to="/entrar">Entrar</RouterLink></p>
    </form>
  </LayoutAcesso>
</template>

<style scoped>
.largo {
  width: 100%;
  min-height: 3rem;
  font-size: var(--t-md);
}

.rodape {
  font-size: var(--t-sm);
  text-align: center;
}
</style>
