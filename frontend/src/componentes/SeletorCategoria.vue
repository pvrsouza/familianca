<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Plus, RotateCcw } from 'lucide-vue-next'
import { categorias as api } from '../api/servicos'
import type { Categoria, Grupo, Tipo } from '../api/tipos'
import { mensagemDeErro } from '../api/http'
import { catalogo } from '../estado/catalogo'
import { sessao } from '../estado/sessao'
import { avisar } from '../estado/avisos'
import Mensagem from './Mensagem.vue'

// Categoria com busca (combobox): digitar filtra pelo nome da categoria ou do grupo, sem ligar para acento. Não achou?
// Quem organiza a família cria ali mesmo (ou reativa a desativada de mesmo nome); quem participa vê a quem pedir.
const props = defineProps<{ modelValue: number | null; tipo: Tipo; id?: string; invalido?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [number | null] }>()

type Opcao =
  | { chave: string; acao: 'escolher'; categoria: Categoria; grupo: Grupo }
  | { chave: string; acao: 'criar' }
  | { chave: string; acao: 'reativar'; categoria: Categoria; grupo: Grupo }

const idBase = computed(() => props.id ?? `categoria-${props.tipo.toLowerCase()}`)
const dono = computed(() => sessao.dono.value)
const grupos = computed(() => catalogo.lancaveis(props.tipo))
const texto = ref('')
const aberto = ref(false)
const digitando = ref(false)
const ativa = ref(0)
const entrada = ref<HTMLInputElement | null>(null)

function normal(s: string): string {
  return s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/\s+/g, ' ').trim()
}

const selecionada = computed(() => {
  for (const g of catalogo.estado.grupos) {
    const c = g.categorias.find((x) => x.id === props.modelValue)
    if (c) return { categoria: c, grupo: g }
  }
  return null
})

/** Quando não está digitando, o campo mostra a categoria escolhida. */
watch(selecionada, (s) => {
  if (!digitando.value) texto.value = s?.categoria.nome ?? ''
}, { immediate: true })

const busca = computed(() => (digitando.value ? normal(texto.value) : ''))

const filtrados = computed(() => {
  const b = busca.value
  if (!b) return grupos.value
  return grupos.value
    .map((g) => (normal(g.nome).includes(b) ? g : { ...g, categorias: g.categorias.filter((c) => normal(c.nome).includes(b)) }))
    .filter((g) => g.categorias.length > 0)
})

const nomeDigitado = computed(() => texto.value.trim().replace(/\s+/g, ' '))
const existeIgual = computed(() =>
  grupos.value.some((g) => g.categorias.some((c) => normal(c.nome) === busca.value)),
)
/** Desativada com o mesmo nome, num grupo ativo: melhor reativar do que criar outra igual. */
const desativadaIgual = computed(() => {
  if (!busca.value || existeIgual.value) return null
  for (const g of catalogo.estado.grupos) {
    if (g.tipo !== props.tipo || !g.ativo) continue
    const c = g.categorias.find((x) => !x.ativa && !x.calculada && normal(x.nome) === busca.value)
    if (c) return { categoria: c, grupo: g }
  }
  return null
})
const sugerirNova = computed(() => !!busca.value && !existeIgual.value)

const opcoes = computed<Opcao[]>(() => {
  const lista: Opcao[] = filtrados.value.flatMap((g) =>
    g.categorias.map((c) => ({ chave: `c${c.id}`, acao: 'escolher' as const, categoria: c, grupo: g })),
  )
  if (dono.value && desativadaIgual.value) lista.push({ chave: 'reativar', acao: 'reativar', ...desativadaIgual.value })
  else if (dono.value && sugerirNova.value) lista.push({ chave: 'criar', acao: 'criar' })
  return lista
})

function idOpcao(o: Opcao): string {
  return `${idBase.value}-op-${o.chave}`
}

function indice(o: Opcao): number {
  return opcoes.value.indexOf(o)
}

const opcaoAtiva = computed(() => (aberto.value ? opcoes.value[ativa.value] ?? null : null))
const extras = computed(() => opcoes.value.filter((o) => o.acao !== 'escolher'))

watch(opcoes, () => {
  ativa.value = 0
})

function abrir() {
  if (aberto.value) return
  aberto.value = true
  const i = opcoes.value.findIndex((o) => o.acao === 'escolher' && o.categoria.id === props.modelValue)
  ativa.value = Math.max(0, i)
}

function fechar() {
  aberto.value = false
  digitando.value = false
  texto.value = selecionada.value?.categoria.nome ?? ''
}

function digitar(e: Event) {
  texto.value = (e.target as HTMLInputElement).value
  digitando.value = true
  criando.value = false
  abrir()
}

function focou() {
  catalogo.carregar()
  entrada.value?.select()
  abrir()
}

function teclar(e: KeyboardEvent) {
  const n = opcoes.value.length
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    if (!aberto.value) return abrir()
    if (n) ativa.value = (ativa.value + 1) % n
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    if (!aberto.value) return abrir()
    if (n) ativa.value = (ativa.value - 1 + n) % n
  } else if (e.key === 'Enter') {
    if (!aberto.value) return
    e.preventDefault()
    if (opcaoAtiva.value) escolher(opcaoAtiva.value)
  } else if (e.key === 'Escape') {
    if (!aberto.value) return
    e.preventDefault()
    e.stopPropagation()
    fechar()
  }
}

function escolher(o: Opcao) {
  if (o.acao === 'escolher') {
    emit('update:modelValue', o.categoria.id)
    digitando.value = false
    aberto.value = false
    texto.value = o.categoria.nome
  } else if (o.acao === 'criar') {
    iniciarCriacao()
  } else {
    reativar(o.categoria, o.grupo)
  }
}

// ——— Criar (ou reativar) sem sair do lançamento ———
const criando = ref(false)
const novo = ref({ nome: '', grupoId: 0 })
const salvando = ref(false)
const erro = ref('')
const gruposParaCriar = computed(() => catalogo.estado.grupos.filter((g) => g.tipo === props.tipo && g.ativo))
const campoGrupo = ref<HTMLSelectElement | null>(null)

async function iniciarCriacao() {
  // Grupo sugerido: o do primeiro resultado parecido; senão o primeiro do tipo.
  const parecido = filtrados.value[0]?.id
  novo.value = { nome: nomeDigitado.value.slice(0, 120), grupoId: parecido ?? gruposParaCriar.value[0]?.id ?? 0 }
  erro.value = ''
  aberto.value = false
  criando.value = true
  await nextTick()
  campoGrupo.value?.focus()
}

function cancelarCriacao() {
  criando.value = false
  fechar()
  entrada.value?.focus()
}

async function usar(id: number, mensagem: string) {
  await catalogo.carregar(true)
  sessao.avisarMudanca()
  digitando.value = false
  emit('update:modelValue', id)
  avisar(mensagem)
}

async function criar() {
  const nome = novo.value.nome.trim()
  const grupo = gruposParaCriar.value.find((g) => g.id === novo.value.grupoId)
  if (!nome || !grupo) return
  salvando.value = true
  erro.value = ''
  try {
    const c = await api.criar({ grupoId: grupo.id, nome })
    criando.value = false
    await usar(c.id, `${c.nome} criada em ${grupo.nome}.`)
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    salvando.value = false
  }
}

async function reativar(c: Categoria, g: Grupo) {
  aberto.value = false
  erro.value = ''
  try {
    await api.atualizar(c.id, { grupoId: c.grupoId, nome: c.nome, ativa: true, tributavel: c.tributavel, aliquota: c.aliquota })
    await usar(c.id, `${c.nome} reativada em ${g.nome}.`)
  } catch (e) {
    erro.value = mensagemDeErro(e)
  }
}
</script>

<template>
  <div class="seletor">
    <input
      :id="idBase"
      ref="entrada"
      class="selecao"
      type="text"
      role="combobox"
      autocomplete="off"
      maxlength="120"
      placeholder="Escolha ou busque a categoria"
      :value="texto"
      :aria-expanded="aberto"
      :aria-controls="`${idBase}-lista`"
      aria-autocomplete="list"
      :aria-activedescendant="opcaoAtiva ? idOpcao(opcaoAtiva) : undefined"
      :aria-invalid="invalido || undefined"
      :disabled="criando"
      required
      @input="digitar"
      @focus="focou"
      @click="abrir"
      @blur="fechar"
      @keydown="teclar"
    />

    <!-- mousedown.prevent: clicar numa opção não tira o foco do campo antes do clique. -->
    <div v-show="aberto" :id="`${idBase}-lista`" class="lista-opcoes" role="listbox" aria-label="Categorias" @mousedown.prevent>
      <div v-for="g in filtrados" :key="g.id" role="group" :aria-labelledby="`${idBase}-g${g.id}`">
        <div :id="`${idBase}-g${g.id}`" class="grupo-opcoes" role="presentation">{{ g.nome }}</div>
        <div
          v-for="c in g.categorias"
          :id="`${idBase}-op-c${c.id}`"
          :key="c.id"
          role="option"
          class="opcao"
          :class="{ ativa: opcaoAtiva?.chave === `c${c.id}` }"
          :aria-selected="c.id === modelValue"
          @mouseenter="ativa = opcoes.findIndex((o) => o.chave === `c${c.id}`)"
          @click="escolher(opcoes.find((o) => o.chave === `c${c.id}`)!)"
        >
          {{ c.nome }}
        </div>
      </div>

      <div
        v-for="o in extras"
        :id="idOpcao(o)"
        :key="o.chave"
        role="option"
        class="opcao opcao-acao"
        :class="{ ativa: opcaoAtiva?.chave === o.chave }"
        aria-selected="false"
        @mouseenter="ativa = indice(o)"
        @click="escolher(o)"
      >
        <template v-if="o.acao === 'reativar'">
          <RotateCcw aria-hidden="true" /> Reativar "{{ o.categoria.nome }}" em {{ o.grupo.nome }} (está desativada)
        </template>
        <template v-else><Plus aria-hidden="true" /> Criar a categoria "{{ nomeDigitado }}"</template>
      </div>

      <p v-if="!dono && sugerirNova" class="sem-resultado" role="presentation">
        Não achou "{{ nomeDigitado }}"? Peça para quem organiza a família criar em Categorias. Por enquanto, use a
        mais próxima{{ filtrados.length ? ' acima' : ', como "Outras"' }}.
      </p>
      <p v-else-if="!opcoes.length" class="sem-resultado" role="presentation">Nenhuma categoria para lançar ainda.</p>
    </div>

    <!-- Sem <form>: o seletor fica dentro do formulário do lançamento, e form dentro de form não vale. -->
    <div v-if="criando" class="criar" role="group" :aria-labelledby="`${idBase}-novo-titulo`">
      <p :id="`${idBase}-novo-titulo`" class="criar-titulo">Nova categoria de {{ tipo === 'RECEITA' ? 'receita' : 'despesa' }}</p>
      <div class="criar-campos">
        <div class="campo">
          <label :for="`${idBase}-novo-nome`">Nome</label>
          <input :id="`${idBase}-novo-nome`" v-model="novo.nome" class="entrada" maxlength="120" required
                 @keydown.enter.prevent="criar" />
        </div>
        <div class="campo">
          <label :for="`${idBase}-novo-grupo`">Em qual grupo</label>
          <select :id="`${idBase}-novo-grupo`" ref="campoGrupo" v-model="novo.grupoId" class="selecao">
            <option v-for="g in gruposParaCriar" :key="g.id" :value="g.id">{{ g.nome }}</option>
          </select>
        </div>
      </div>
      <p class="ajuda">Ela passa a valer para toda a família e aparece na visão anual dentro do grupo escolhido.</p>
      <div class="acoes">
        <button type="button" class="botao botao-primario botao-pequeno" :disabled="salvando || !novo.nome.trim()" @click="criar">
          {{ salvando ? 'Criando…' : 'Criar e usar' }}
        </button>
        <button type="button" class="botao botao-sutil botao-pequeno" :disabled="salvando" @click="cancelarCriacao">Cancelar</button>
      </div>
    </div>

    <Mensagem v-if="erro" tipo="erro">{{ erro }}</Mensagem>
  </div>
</template>

<style scoped>
.seletor {
  position: relative;
  display: grid;
  gap: var(--e-2);
}

.lista-opcoes {
  position: absolute;
  top: calc(2.75rem + var(--e-1));
  left: 0;
  right: 0;
  z-index: 20;
  max-height: 18rem;
  overflow-y: auto;
  padding: var(--e-1) 0;
  border: 1px solid var(--linha);
  border-radius: var(--raio);
  background: var(--superficie);
  box-shadow: var(--sombra);
}

.grupo-opcoes {
  padding: var(--e-2) var(--e-3) var(--e-1);
  font-size: var(--t-xs);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--texto-2);
}

.opcao {
  display: flex;
  align-items: center;
  gap: var(--e-2);
  min-height: 2.5rem;
  padding: var(--e-1) var(--e-3) var(--e-1) var(--e-5);
  font-size: var(--t-sm);
  cursor: pointer;
}

.opcao[aria-selected='true'] {
  font-weight: 600;
}

.opcao.ativa {
  background: var(--petroleo-tinta);
}

.opcao-acao {
  margin-top: var(--e-1);
  padding-left: var(--e-3);
  border-top: 1px solid var(--linha);
  color: var(--acao);
  font-weight: 600;
}

.opcao-acao svg {
  width: 1rem;
  height: 1rem;
  flex: none;
}

.sem-resultado {
  padding: var(--e-2) var(--e-3);
  font-size: var(--t-sm);
  line-height: 1.45;
  color: var(--texto-2);
}

.criar {
  display: grid;
  gap: var(--e-3);
  padding: var(--e-3) var(--e-4);
  border-radius: var(--raio);
  background: var(--areia);
}

.criar-titulo {
  font-size: var(--t-sm);
  font-weight: 600;
}

.criar-campos {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--e-3);
}

.criar .ajuda {
  font-size: var(--t-xs);
  color: var(--texto-2);
}

@media (max-width: 480px) {
  .criar-campos {
    grid-template-columns: 1fr;
  }
}
</style>
