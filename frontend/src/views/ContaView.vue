<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import QRCode from 'qrcode'
import { Monitor, Moon, ShieldCheck, Sun } from 'lucide-vue-next'
import { conta } from '../api/servicos'
import type { SituacaoMfa } from '../api/tipos'
import { ErroApi, mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import { avisar } from '../estado/avisos'
import { centavos } from '../estado/centavos'
import { tema, type Tema } from '../estado/tema'
import CampoSenha from '../componentes/CampoSenha.vue'
import Mensagem from '../componentes/Mensagem.vue'

const usuario = sessao.estado.usuario

const temas: { valor: Tema; rotulo: string; icone: typeof Sun }[] = [
  { valor: 'automatico', rotulo: 'Automático', icone: Monitor },
  { valor: 'claro', rotulo: 'Claro', icone: Sun },
  { valor: 'escuro', rotulo: 'Escuro', icone: Moon },
]

// ——— Nome ———
const nome = ref(usuario?.nome ?? '')
const erroNome = ref('')
async function salvarNome() {
  erroNome.value = ''
  if (!nome.value.trim()) {
    erroNome.value = 'Informe seu nome.'
    return
  }
  try {
    await conta.renomear(nome.value.trim())
    if (sessao.estado.usuario) sessao.estado.usuario.nome = nome.value.trim()
    await sessao.recarregarFamilia()
    avisar('Nome atualizado.')
  } catch (e) {
    erroNome.value = mensagemDeErro(e)
  }
}

// ——— Senha ———
const senha = reactive({ atual: '', nova: '', erro: '', salvando: false })
async function trocarSenha() {
  senha.erro = ''
  if (!senha.atual || senha.nova.length < 12) {
    senha.erro = 'Informe a senha atual e uma nova com 12 a 64 caracteres.'
    return
  }
  senha.salvando = true
  try {
    await conta.trocarSenha(senha.atual, senha.nova)
    Object.assign(senha, { atual: '', nova: '' })
    avisar('Senha trocada.')
  } catch (e) {
    senha.erro = e instanceof ErroApi && (e.status === 401 || e.status === 422 || e.status === 400)
      ? 'A senha atual não confere, ou a nova não segue a regra de 12 a 64 caracteres.'
      : mensagemDeErro(e)
  } finally {
    senha.salvando = false
  }
}

// ——— Verificação em duas etapas ———
const mfa = ref<SituacaoMfa | null>(null)
const etapa = ref<'parado' | 'configurando' | 'codigos'>('parado')
const qr = ref('')
const segredo = ref('')
const codigo = ref('')
const codigos = ref<string[]>([])
const erroMfa = ref('')
const ocupado = ref(false)
const desativando = reactive({ aberto: false, senha: '', codigo: '' })
const gerandoNovos = reactive({ aberto: false, codigo: '' })

onMounted(async () => {
  try {
    mfa.value = await conta.mfa()
  } catch {
    mfa.value = null
  }
})

async function comecarMfa() {
  erroMfa.value = ''
  ocupado.value = true
  try {
    const c = await conta.configurarMfa()
    segredo.value = c.segredo
    qr.value = await QRCode.toDataURL(c.uri, { margin: 1, width: 220, color: { dark: '#1E2A2E', light: '#FFFFFF' } })
    etapa.value = 'configurando'
  } catch (e) {
    erroMfa.value = mensagemDeErro(e)
  } finally {
    ocupado.value = false
  }
}

async function ativarMfa() {
  erroMfa.value = ''
  if (!/^\d{6}$/.test(codigo.value.trim())) {
    erroMfa.value = 'Digite os 6 números que aparecem no aplicativo.'
    return
  }
  ocupado.value = true
  try {
    const r = await conta.ativarMfa(codigo.value.trim())
    codigos.value = r.codigosRecuperacao
    etapa.value = 'codigos'
    qr.value = ''
    segredo.value = ''
    codigo.value = ''
    mfa.value = { ativo: true, disponivel: true }
  } catch (e) {
    erroMfa.value = e instanceof ErroApi && e.status < 500 ? 'O código não confere. Confira o horário do celular e tente o código novo.' : mensagemDeErro(e)
  } finally {
    ocupado.value = false
  }
}

async function novosCodigos() {
  erroMfa.value = ''
  ocupado.value = true
  try {
    const r = await conta.novosCodigos(gerandoNovos.codigo.trim())
    codigos.value = r.codigosRecuperacao
    etapa.value = 'codigos'
    Object.assign(gerandoNovos, { aberto: false, codigo: '' })
  } catch (e) {
    erroMfa.value = mensagemDeErro(e)
  } finally {
    ocupado.value = false
  }
}

async function desativarMfa() {
  erroMfa.value = ''
  ocupado.value = true
  try {
    await conta.desativarMfa(desativando.senha, desativando.codigo.trim())
    Object.assign(desativando, { aberto: false, senha: '', codigo: '' })
    mfa.value = { ativo: false, disponivel: true }
    avisar('Verificação em duas etapas desligada.')
  } catch (e) {
    erroMfa.value = e instanceof ErroApi && e.status < 500 ? 'Senha ou código não conferem.' : mensagemDeErro(e)
  } finally {
    ocupado.value = false
  }
}

async function copiarCodigos() {
  try {
    await navigator.clipboard.writeText(codigos.value.join('\n'))
    avisar('Códigos copiados.')
  } catch {
    avisar('Não deu para copiar. Anote os códigos à mão.')
  }
}
</script>

<template>
  <div class="pagina estreita">
    <header class="pagina-cabecalho">
      <div>
        <h1>Minha conta</h1>
        <p>{{ usuario?.email }}</p>
      </div>
    </header>

    <section class="painel painel-corpo">
      <h2>Seu nome</h2>
      <form class="linha-form" novalidate @submit.prevent="salvarNome">
        <label for="c-nome" class="sr">Nome</label>
        <input id="c-nome" v-model="nome" class="entrada" maxlength="200" autocomplete="name" />
        <button type="submit" class="botao">Salvar</button>
      </form>
      <Mensagem v-if="erroNome" tipo="erro">{{ erroNome }}</Mensagem>
    </section>

    <section class="painel painel-corpo">
      <h2>Como ver os valores</h2>
      <label class="caixa-marcar">
        <input type="checkbox" :checked="centavos.ligados.value" @change="centavos.alternar()" aria-describedby="c-centavos-ajuda" />
        Mostrar centavos nas tabelas do ano
      </label>
      <p id="c-centavos-ajuda" class="ajuda preferencia">
        Desligado, a visão anual, o fluxo de caixa e a carteira mostram o valor arredondado para o real mais próximo
        (R$ 1.234,56 aparece como 1.235). Vale só neste aparelho.
      </p>
    </section>

    <section class="painel painel-corpo">
      <h2 id="c-tema-titulo">Aparência</h2>
      <div class="segmentos" role="group" aria-labelledby="c-tema-titulo" aria-describedby="c-tema-ajuda">
        <button v-for="t in temas" :key="t.valor" type="button" :aria-pressed="tema.escolha.value === t.valor"
                @click="tema.escolher(t.valor)">
          <component :is="t.icone" aria-hidden="true" />
          {{ t.rotulo }}
        </button>
      </div>
      <p id="c-tema-ajuda" class="ajuda tema-ajuda">
        No automático, o tema acompanha o do celular ou do computador. Vale só neste aparelho.
      </p>
    </section>

    <section class="painel painel-corpo">
      <h2>Senha</h2>
      <form class="formulario" novalidate @submit.prevent="trocarSenha">
        <input type="email" :value="usuario?.email" autocomplete="username" hidden />
        <div class="grade-campos duas">
          <div class="campo">
            <label for="s-atual">Senha atual</label>
            <CampoSenha id="s-atual" v-model="senha.atual" autocomplete="current-password" />
          </div>
          <div class="campo">
            <label for="s-nova">Nova senha</label>
            <CampoSenha id="s-nova" v-model="senha.nova" autocomplete="new-password" :minimo="12" />
            <span class="ajuda">De 12 a 64 caracteres.</span>
          </div>
        </div>
        <Mensagem v-if="senha.erro" tipo="erro">{{ senha.erro }}</Mensagem>
        <div class="acoes">
          <button type="submit" class="botao" :disabled="senha.salvando">Trocar senha</button>
        </div>
      </form>
    </section>

    <section v-if="mfa?.disponivel" class="painel painel-corpo">
      <div class="mfa-titulo">
        <h2>Verificação em duas etapas</h2>
        <span v-if="mfa.ativo" class="selo selo-verde"><ShieldCheck aria-hidden="true" /> ligada</span>
      </div>
      <p class="texto-2 explica">
        Além da senha, o login pede um código do aplicativo autenticador do celular (Google Authenticator, Microsoft
        Authenticator, 2FAS e outros). Mesmo que alguém descubra a senha, não entra sem o celular.
      </p>

      <Mensagem v-if="erroMfa" tipo="erro">{{ erroMfa }}</Mensagem>

      <template v-if="etapa === 'codigos'">
        <Mensagem tipo="sucesso">
          Guarde estes códigos de recuperação num lugar seguro. Cada um vale uma vez, no lugar do código do aplicativo,
          se você perder o celular. Eles não aparecem de novo.
        </Mensagem>
        <ol class="codigos num">
          <li v-for="c in codigos" :key="c">{{ c }}</li>
        </ol>
        <div class="acoes">
          <button type="button" class="botao" @click="copiarCodigos">Copiar os códigos</button>
          <button type="button" class="botao botao-primario" @click="etapa = 'parado'; codigos = []">Já guardei</button>
        </div>
      </template>

      <template v-else-if="etapa === 'configurando'">
        <div class="configurar">
          <img :src="qr" width="220" height="220" alt="QR code para o aplicativo autenticador" class="qr" />
          <div class="formulario">
            <ol class="passos">
              <li>Abra o aplicativo autenticador e escolha adicionar uma conta.</li>
              <li>Leia o QR code ou digite a chave: <code class="chave">{{ segredo }}</code></li>
              <li>Digite o código de 6 números que aparece.</li>
            </ol>
            <form class="linha-form" novalidate @submit.prevent="ativarMfa">
              <label for="m-codigo" class="sr">Código</label>
              <input id="m-codigo" v-model="codigo" class="entrada num codigo" inputmode="numeric" autocomplete="one-time-code"
                     maxlength="6" placeholder="000000" />
              <button type="submit" class="botao botao-primario" :disabled="ocupado">Ligar</button>
            </form>
            <button type="button" class="botao botao-sutil botao-pequeno" @click="etapa = 'parado'">Agora não</button>
          </div>
        </div>
      </template>

      <template v-else-if="!mfa.ativo">
        <button type="button" class="botao botao-primario" :disabled="ocupado" @click="comecarMfa">Ligar a verificação</button>
      </template>

      <template v-else>
        <div class="acoes">
          <button v-if="!gerandoNovos.aberto" type="button" class="botao" @click="gerandoNovos.aberto = true; desativando.aberto = false">
            Gerar novos códigos de recuperação
          </button>
          <button v-if="!desativando.aberto" type="button" class="botao botao-perigo" @click="desativando.aberto = true; gerandoNovos.aberto = false">
            Desligar
          </button>
        </div>
        <form v-if="gerandoNovos.aberto" class="formulario sub-form" novalidate @submit.prevent="novosCodigos">
          <p class="texto-2">Os códigos antigos deixam de valer.</p>
          <div class="campo">
            <label for="n-codigo">Código do aplicativo</label>
            <input id="n-codigo" v-model="gerandoNovos.codigo" class="entrada num" inputmode="numeric" maxlength="20" autocomplete="one-time-code" />
          </div>
          <div class="acoes">
            <button type="submit" class="botao botao-primario" :disabled="ocupado">Gerar</button>
            <button type="button" class="botao" @click="gerandoNovos.aberto = false">Cancelar</button>
          </div>
        </form>
        <form v-if="desativando.aberto" class="formulario sub-form" novalidate @submit.prevent="desativarMfa">
          <div class="grade-campos duas">
            <div class="campo">
              <label for="d-senha">Sua senha</label>
              <CampoSenha id="d-senha" v-model="desativando.senha" autocomplete="current-password" />
            </div>
            <div class="campo">
              <label for="d-codigo">Código do aplicativo ou de recuperação</label>
              <input id="d-codigo" v-model="desativando.codigo" class="entrada num" maxlength="20" autocomplete="one-time-code" />
            </div>
          </div>
          <div class="acoes">
            <button type="submit" class="botao botao-perigo" :disabled="ocupado">Desligar a verificação</button>
            <button type="button" class="botao" @click="desativando.aberto = false">Manter ligada</button>
          </div>
        </form>
      </template>
    </section>
  </div>
</template>

<style scoped>
.estreita {
  max-width: 48rem;
}

.painel + .painel {
  margin-top: var(--e-5);
}

.painel h2 {
  margin-bottom: var(--e-3);
}

.segmentos svg {
  width: 1rem;
  height: 1rem;
}

.tema-ajuda {
  max-width: 60ch;
  margin-top: var(--e-2);
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.preferencia {
  max-width: 60ch;
  margin-top: var(--e-1);
  padding-left: 1.75rem;
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.linha-form {
  display: flex;
  gap: var(--e-2);
  margin-bottom: var(--e-2);
}

.linha-form .entrada {
  flex: 1;
}

.mfa-titulo {
  display: flex;
  align-items: center;
  gap: var(--e-3);
  margin-bottom: var(--e-2);
}

.mfa-titulo h2 {
  margin: 0;
}

.explica {
  max-width: 64ch;
  margin-bottom: var(--e-4);
  font-size: var(--t-sm);
}

.configurar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--e-6);
  align-items: flex-start;
}

/* Fundo branco também no tema escuro: o leitor do autenticador precisa do contraste original do QR. */
.qr {
  border: 1px solid var(--linha);
  border-radius: var(--raio);
  background: #fff;
}

.configurar .formulario {
  flex: 1;
  min-width: 16rem;
  gap: var(--e-3);
}

.passos {
  margin: 0;
  padding-left: var(--e-5);
  display: grid;
  gap: var(--e-2);
  font-size: var(--t-sm);
}

.chave {
  display: inline-block;
  margin-top: var(--e-1);
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--areia);
  font-size: var(--t-sm);
  overflow-wrap: anywhere;
  user-select: all;
}

.codigo {
  max-width: 10rem;
  font-size: var(--t-lg);
  letter-spacing: 0.2em;
  text-align: center;
}

.codigos {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 12rem));
  gap: var(--e-2) var(--e-6);
  margin: var(--e-4) 0;
  padding-left: var(--e-6);
  font-weight: 600;
  letter-spacing: 0.05em;
  user-select: all;
}

.sub-form {
  margin-top: var(--e-5);
  padding-top: var(--e-4);
  border-top: 1px solid var(--linha);
}

.painel :deep(.aviso) {
  margin-bottom: var(--e-3);
}
</style>
