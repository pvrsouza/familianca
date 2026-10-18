<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { auth } from '../api/servicos'
import type { ConviteAberto } from '../api/tipos'
import { ErroApi, mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import { catalogo } from '../estado/catalogo'
import { dataPorExtenso } from '../util/formato'
import LayoutAcesso from '../componentes/LayoutAcesso.vue'
import CampoSenha from '../componentes/CampoSenha.vue'
import Mensagem from '../componentes/Mensagem.vue'

// A primeira tela de quem foi convidado: tão importante quanto a de quem criou a conta (PRODUCT.md).
const route = useRoute()
const router = useRouter()
const token = String(route.params.token ?? '')
const convite = ref<ConviteAberto | null>(null)
const estado = ref<'carregando' | 'aberto' | 'invalido' | 'erro'>('carregando')
const erroCarga = ref('')
const form = reactive({ nome: '', senha: '' })
const enviando = ref(false)
const erro = ref('')
const jaTemConta = ref(false)
const tentou = ref(false)
const logadoComOutraConta = computed(() => sessao.logado.value)

onMounted(async () => {
  try {
    convite.value = await auth.convite(token)
    estado.value = 'aberto'
  } catch (e) {
    if (e instanceof ErroApi && e.status === 404) estado.value = 'invalido'
    else {
      estado.value = 'erro'
      erroCarga.value = mensagemDeErro(e)
    }
  }
})

async function sairDaOutraConta() {
  await sessao.sair()
  catalogo.limpar()
}

async function aceitar() {
  tentou.value = true
  erro.value = ''
  jaTemConta.value = false
  if (!form.nome.trim() || form.senha.length < 12 || !convite.value) return
  enviando.value = true
  try {
    await auth.aceitarConvite(token, form.nome.trim(), form.senha)
    await sessao.entrar(convite.value.email, form.senha)
    router.replace({ path: '/ano', query: { bemvindo: '1' } })
  } catch (e) {
    if (e instanceof ErroApi && e.status === 409) jaTemConta.value = true
    else if (e instanceof ErroApi && e.status === 402)
      erro.value = 'A família já está com o número máximo de pessoas do plano. Avise quem convidou você.'
    else if (e instanceof ErroApi && e.status === 404) estado.value = 'invalido'
    else erro.value = mensagemDeErro(e)
  } finally {
    enviando.value = false
  }
}
</script>

<template>
  <LayoutAcesso v-if="estado === 'carregando'" titulo="Abrindo o convite…">
    <div class="formulario" aria-busy="true">
      <div class="esqueleto" style="height: 2.75rem" />
      <div class="esqueleto" style="height: 2.75rem" />
    </div>
  </LayoutAcesso>

  <LayoutAcesso v-else-if="estado === 'invalido'" titulo="Este convite não vale mais"
                subtitulo="Ele pode ter sido usado, cancelado ou passado dos 7 dias de validade.">
    <div class="formulario">
      <p>Peça um link novo para quem convidou você. Leva um minuto.</p>
      <p class="texto-2">Se você já criou sua conta por este link, é só <RouterLink to="/entrar">entrar</RouterLink>.</p>
    </div>
  </LayoutAcesso>

  <LayoutAcesso v-else-if="estado === 'erro'" titulo="Não conseguimos abrir o convite">
    <div class="formulario">
      <Mensagem tipo="erro">{{ erroCarga }}</Mensagem>
    </div>
  </LayoutAcesso>

  <LayoutAcesso v-else-if="convite" :titulo="`Boas-vindas à ${convite.familia}`"
                subtitulo="Vocês vão ver juntos o ano do orçamento da casa: o que entra, o que sai e o peso de cada gasto. Todo mundo vê o mesmo quadro.">
    <form class="formulario" novalidate @submit.prevent="aceitar">
      <Mensagem v-if="logadoComOutraConta">
        Você está conectado com outra conta neste navegador.
        <template #acoes>
          <button type="button" class="botao botao-pequeno" @click="sairDaOutraConta">Sair dela</button>
        </template>
      </Mensagem>

      <dl class="convite-dados">
        <div>
          <dt>Seu e-mail</dt>
          <dd>{{ convite.email }}</dd>
        </div>
        <div>
          <dt>Você entra como</dt>
          <dd>{{ convite.papel === 'DONO' ? 'quem organiza (pode mudar categorias e convidar)' : 'parte da família' }}</dd>
        </div>
      </dl>

      <div class="campo">
        <label for="nome">Seu nome</label>
        <input id="nome" v-model="form.nome" class="entrada" autocomplete="name" maxlength="200" required autofocus
               :aria-invalid="(tentou && !form.nome.trim()) || undefined" />
        <span v-if="tentou && !form.nome.trim()" class="erro-campo">Como a família chama você?</span>
      </div>
      <div class="campo">
        <label for="senha">Crie uma senha</label>
        <CampoSenha id="senha" v-model="form.senha" autocomplete="new-password" :minimo="12"
                    :invalido="(tentou && form.senha.length < 12) || undefined" />
        <span class="ajuda" :class="{ 'erro-campo': tentou && form.senha.length < 12 }">
          De 12 a 64 caracteres. Uma frase que só você lembra funciona bem.
        </span>
      </div>

      <Mensagem v-if="jaTemConta" tipo="erro">
        Este e-mail já tem conta no Familiança, e por enquanto cada pessoa participa de uma família só.
        <RouterLink to="/entrar">Entrar com ele</RouterLink>
      </Mensagem>
      <Mensagem v-if="erro" tipo="erro">{{ erro }}</Mensagem>

      <button type="submit" class="botao botao-primario largo" :disabled="enviando || logadoComOutraConta">
        {{ enviando ? 'Entrando na família…' : 'Entrar na família' }}
      </button>
      <p class="rodape texto-2">O convite vale até {{ dataPorExtenso(convite.expiraEm, true) }}.</p>
    </form>
  </LayoutAcesso>
</template>

<style scoped>
.convite-dados {
  display: grid;
  gap: var(--e-3);
  margin: 0;
  padding: var(--e-4);
  border-radius: var(--raio);
  background: var(--papel);
  border: 1px solid var(--linha);
}

.convite-dados dt {
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.convite-dados dd {
  margin: 0;
  font-weight: 600;
  overflow-wrap: anywhere;
}

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
