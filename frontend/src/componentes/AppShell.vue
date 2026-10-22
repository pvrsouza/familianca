<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import {
  Calculator, ChevronDown, Ellipsis, Eye, EyeOff, LayoutGrid, LogOut, PiggyBank, Plus, Receipt, Repeat, Tags, UserRound, Users,
} from 'lucide-vue-next'
import { sessao } from '../estado/sessao'
import { privacidade } from '../estado/privacidade'
import { catalogo } from '../estado/catalogo'
import { avisar, avisos } from '../estado/avisos'
import { primeiroNome } from '../util/formato'
import Marca from './Marca.vue'
import PainelLateral from './PainelLateral.vue'
import FormLancamento from './FormLancamento.vue'

const route = useRoute()
const router = useRouter()
const lancando = ref(false)
const menuAberto = ref(false)
const maisAberto = ref(false)
const menu = ref<HTMLElement | null>(null)

const usuario = computed(() => sessao.estado.usuario)
const nomeFamilia = computed(() => sessao.estado.familia?.nome ?? '')

const principais = [
  { para: '/ano', rotulo: 'O ano', icone: LayoutGrid },
  { para: '/lancamentos', rotulo: 'Lançamentos', icone: Receipt },
  { para: '/recorrencias', rotulo: 'Recorrências', icone: Repeat },
  { para: '/quanto-pesa', rotulo: 'Quanto pesa?', icone: Calculator },
  { para: '/familia', rotulo: 'Família', icone: Users },
]
const secundarios = [
  { para: '/categorias', rotulo: 'Categorias', icone: Tags },
  { para: '/investimentos', rotulo: 'Investimentos', icone: PiggyBank },
  { para: '/conta', rotulo: 'Minha conta', icone: UserRound },
]
const noCelular = principais.filter((p) => p.para !== '/quanto-pesa')
const noMais = [principais.find((p) => p.para === '/quanto-pesa')!, ...secundarios]

function lancou(mensagem: string) {
  lancando.value = false
  avisar(mensagem)
}

async function sair() {
  menuAberto.value = false
  await sessao.sair()
  catalogo.limpar()
  router.replace('/entrar')
}

function cliqueFora(e: MouseEvent) {
  if (menuAberto.value && menu.value && !menu.value.contains(e.target as Node)) menuAberto.value = false
}

function tecla(e: KeyboardEvent) {
  if (e.key === 'Escape') menuAberto.value = false
}

watch(() => route.fullPath, () => {
  menuAberto.value = false
  maisAberto.value = false
})

onMounted(() => {
  document.addEventListener('click', cliqueFora)
  document.addEventListener('keydown', tecla)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', cliqueFora)
  document.removeEventListener('keydown', tecla)
})
</script>

<template>
  <a class="pular" href="#conteudo">Pular para o conteúdo</a>
  <header class="barra">
    <div class="barra-dentro">
      <RouterLink to="/ano" class="barra-marca" aria-label="Familiança, início">
        <Marca claro :tamanho="30" />
      </RouterLink>

      <nav class="nav" aria-label="Principal">
        <RouterLink v-for="item in principais" :key="item.para" :to="item.para" class="nav-item">
          {{ item.rotulo }}
        </RouterLink>
      </nav>

      <div class="barra-acoes">
        <button type="button" class="botao lancar" @click="lancando = true">
          <Plus aria-hidden="true" /> <span>Lançar</span>
        </button>

        <button type="button" class="olho" :aria-pressed="privacidade.ocultos.value"
                :aria-label="privacidade.ocultos.value ? 'Mostrar os valores' : 'Esconder os valores'"
                :title="privacidade.ocultos.value ? 'Mostrar os valores' : 'Esconder os valores'"
                @click="privacidade.alternar()">
          <EyeOff v-if="privacidade.ocultos.value" aria-hidden="true" />
          <Eye v-else aria-hidden="true" />
        </button>

        <div ref="menu" class="usuario">
          <button type="button" class="usuario-botao" :aria-expanded="menuAberto" aria-haspopup="menu"
                  @click="menuAberto = !menuAberto">
            <span class="avatar" aria-hidden="true">{{ usuario ? primeiroNome(usuario.nome).charAt(0) : '' }}</span>
            <span class="usuario-textos">
              <span class="usuario-nome">{{ usuario ? primeiroNome(usuario.nome) : '' }}</span>
              <span class="usuario-familia">{{ nomeFamilia }}</span>
            </span>
            <ChevronDown class="seta" aria-hidden="true" />
          </button>
          <div v-if="menuAberto" class="menu" role="menu">
            <RouterLink v-for="item in secundarios" :key="item.para" :to="item.para" role="menuitem" class="menu-item">
              <component :is="item.icone" aria-hidden="true" /> {{ item.rotulo }}
            </RouterLink>
            <button type="button" role="menuitem" class="menu-item" @click="sair">
              <LogOut aria-hidden="true" /> Sair
            </button>
          </div>
        </div>
      </div>
    </div>
  </header>

  <main id="conteudo" tabindex="-1">
    <RouterView />
  </main>

  <nav class="nav-celular" aria-label="Principal">
    <RouterLink v-for="item in noCelular" :key="item.para" :to="item.para" class="nav-celular-item">
      <component :is="item.icone" aria-hidden="true" />
      <span>{{ item.rotulo }}</span>
    </RouterLink>
    <button type="button" class="nav-celular-item" :aria-expanded="maisAberto" @click="maisAberto = true">
      <Ellipsis aria-hidden="true" />
      <span>Mais</span>
    </button>
  </nav>

  <PainelLateral :aberto="lancando" titulo="Lançar" descricao="Uma receita ou despesa que já aconteceu."
                 @fechar="lancando = false">
    <FormLancamento v-if="lancando" @salvo="lancou" @cancelar="lancando = false" />
  </PainelLateral>

  <PainelLateral :aberto="maisAberto" titulo="Mais" @fechar="maisAberto = false">
    <ul class="lista mais-lista">
      <li v-for="item in noMais" :key="item.para">
        <RouterLink :to="item.para" class="mais-item">
          <component :is="item.icone" aria-hidden="true" /> {{ item.rotulo }}
        </RouterLink>
      </li>
      <li>
        <button type="button" class="mais-item" @click="sair"><LogOut aria-hidden="true" /> Sair</button>
      </li>
    </ul>
  </PainelLateral>

  <div class="avisos" aria-live="polite">
    <TransitionGroup name="aviso">
      <p v-for="a in avisos.lista" :key="a.id" class="aviso-rapido">{{ a.texto }}</p>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.pular {
  position: absolute;
  left: var(--e-4);
  top: -3rem;
  z-index: 50;
  padding: var(--e-2) var(--e-4);
  background: var(--papel);
  border-radius: var(--raio);
}

.pular:focus {
  top: var(--e-2);
}

.barra {
  position: sticky;
  top: 0;
  z-index: 20;
  background: var(--barra-fundo);
  color: var(--sobre-escuro);
  padding-top: env(safe-area-inset-top);
}

.barra-dentro {
  display: flex;
  align-items: center;
  gap: var(--e-6);
  max-width: var(--largura);
  height: var(--barra-altura);
  margin: 0 auto;
  padding: 0 var(--e-6);
}

.barra-marca {
  flex: none;
  border-radius: 6px;
  text-decoration: none;
}

.barra :focus-visible {
  outline-color: var(--sobre-escuro);
}

.nav {
  display: flex;
  align-self: stretch;
  gap: var(--e-1);
  min-width: 0;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  padding: 0 var(--e-3);
  color: rgb(246 244 240 / 0.8);
  font-size: var(--t-sm);
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
  transition: color 160ms var(--ease);
}

.nav-item:hover {
  color: #fff;
}

.nav-item::after {
  content: '';
  position: absolute;
  left: var(--e-3);
  right: var(--e-3);
  bottom: 0;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: var(--sobre-escuro);
  transform: scaleX(0);
  transition: transform 200ms var(--ease);
}

.nav-item.router-link-active {
  color: #fff;
}

.nav-item.router-link-active::after {
  transform: scaleX(1);
}

.barra-acoes {
  display: flex;
  align-items: center;
  gap: var(--e-3);
  margin-left: auto;
}

.lancar {
  --b-fundo: var(--sobre-escuro);
  --b-borda: var(--sobre-escuro);
  --b-texto: var(--noite-fixa);
}

.lancar:hover {
  --b-fundo: #fff;
  --b-borda: #fff;
}

.olho {
  display: grid;
  place-items: center;
  width: 2.75rem;
  height: 2.75rem;
  margin-right: calc(var(--e-2) * -1);
  border: 0;
  border-radius: var(--raio);
  background: transparent;
  color: rgb(246 244 240 / 0.8);
  cursor: pointer;
  transition: background-color 160ms var(--ease), color 160ms var(--ease);
}

.olho:hover {
  color: #fff;
  background: rgb(255 255 255 / 0.08);
}

.olho[aria-pressed='true'] {
  color: #fff;
  background: var(--noite-fixa);
}

.olho svg {
  width: 1.25rem;
  height: 1.25rem;
  stroke-width: 1.75;
}

.usuario {
  position: relative;
}

.usuario-botao {
  display: flex;
  align-items: center;
  gap: var(--e-2);
  min-height: 2.75rem;
  padding: 0 var(--e-2);
  border: 0;
  border-radius: var(--raio);
  background: transparent;
  color: var(--sobre-escuro);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.usuario-botao:hover {
  background: rgb(255 255 255 / 0.08);
}

.avatar {
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border-radius: 50%;
  background: var(--noite-fixa);
  font-weight: 700;
  font-size: var(--t-sm);
  text-transform: uppercase;
}

.usuario-textos {
  display: grid;
  line-height: 1.2;
}

.usuario-nome {
  font-size: var(--t-sm);
  font-weight: 600;
}

.usuario-familia {
  max-width: 12rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--t-xs);
  color: rgb(246 244 240 / 0.78);
}

.seta {
  width: 1rem;
  height: 1rem;
  opacity: 0.8;
}

.menu {
  position: absolute;
  right: 0;
  top: calc(100% + 6px);
  min-width: 13rem;
  padding: var(--e-1);
  border: 1px solid var(--linha);
  border-radius: var(--raio-g);
  background: var(--papel);
  box-shadow: 0 8px 24px var(--sombra-cor);
  animation: abrir-menu 160ms var(--ease);
}

@keyframes abrir-menu {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
}

.menu-item,
.mais-item {
  display: flex;
  align-items: center;
  gap: var(--e-3);
  width: 100%;
  min-height: 2.75rem;
  padding: 0 var(--e-3);
  border: 0;
  border-radius: var(--raio);
  background: transparent;
  color: var(--texto);
  font: inherit;
  font-size: var(--t-sm);
  font-weight: 500;
  text-decoration: none;
  cursor: pointer;
}

.menu-item:hover,
.mais-item:hover {
  background: var(--areia);
  color: var(--texto);
}

.menu-item svg,
.mais-item svg {
  width: 1.125rem;
  height: 1.125rem;
  color: var(--texto-2);
}

.mais-lista > li {
  padding: 0;
  min-height: 0;
  border: 0;
}

.mais-item {
  min-height: 3.25rem;
  font-size: var(--t-md);
}

main:focus {
  outline: none;
}

.nav-celular {
  display: none;
}

.avisos {
  position: fixed;
  left: 50%;
  bottom: var(--e-6);
  z-index: 60;
  display: grid;
  gap: var(--e-2);
  transform: translateX(-50%);
  pointer-events: none;
}

.aviso-rapido {
  padding: var(--e-3) var(--e-5);
  border-radius: 999px;
  background: var(--noite-fixa);
  color: var(--sobre-escuro);
  font-size: var(--t-sm);
  font-weight: 600;
  box-shadow: 0 6px 20px rgb(18 58 65 / 0.24);
  text-align: center;
}

.aviso-enter-active,
.aviso-leave-active {
  transition: opacity 220ms var(--ease), transform 220ms var(--ease);
}

.aviso-enter-from,
.aviso-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 1080px) {
  .usuario-textos {
    display: none;
  }

  .nav-item {
    padding: 0 var(--e-2);
  }

  .barra-dentro {
    gap: var(--e-4);
  }
}

@media (max-width: 760px) {
  .barra-dentro {
    padding: 0 var(--e-4);
  }

  .nav,
  .usuario {
    display: none;
  }

  .nav-celular {
    position: fixed;
    inset: auto 0 0;
    z-index: 20;
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    padding-bottom: env(safe-area-inset-bottom);
    background: var(--papel);
    border-top: 1px solid var(--linha);
    box-shadow: 0 -2px 12px rgb(30 42 46 / 0.06);
  }

  .nav-celular-item {
    display: grid;
    justify-items: center;
    gap: 2px;
    padding: var(--e-2) 0 var(--e-2);
    min-height: 3.75rem;
    border: 0;
    background: transparent;
    color: var(--texto-2);
    font: inherit;
    font-size: 0.75rem;
    font-weight: 600;
    text-decoration: none;
    cursor: pointer;
  }

  .nav-celular-item svg {
    width: 1.375rem;
    height: 1.375rem;
  }

  .nav-celular-item.router-link-active {
    color: var(--acao);
  }

  .avisos {
    bottom: calc(5rem + env(safe-area-inset-bottom));
  }
}
</style>
