<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { lancamentos } from '../api/servicos'
import { sessao } from '../estado/sessao'

// Descrição com busca nas já usadas na categoria escolhida, para a família chamar a mesma coisa pelo mesmo nome.
// Campo vazio: as 3 mais comuns como sugestão direta. Digitando: o servidor procura nos lançamentos e recorrências
// da categoria por parte do termo, sem ligar para acento, maiúscula ou erro de digitação (pg_trgm), e a lista abre
// embaixo do campo (combobox). Escrito quase igual a uma existente, o campo oferece usar a que já existe.
const props = defineProps<{
  modelValue: string
  id: string
  categoriaId: number | null
  placeholder?: string
  invalido?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [string] }>()

/** Abaixo disso a busca traria quase tudo: as 3 mais comuns já cobrem. */
const MINIMO_PARA_BUSCAR = 2
const ESPERA_DIGITACAO_MS = 250

// Cache por categoria e termo enquanto os dados não mudam: apagar uma letra e voltar não busca de novo.
const cache = new Map<string, string[]>()
let versaoCache = -1

async function consultar(categoriaId: number, termo: string): Promise<string[]> {
  if (versaoCache !== sessao.estado.versaoDados) {
    cache.clear()
    versaoCache = sessao.estado.versaoDados
  }
  const chave = `${categoriaId}|${termo}`
  if (!cache.has(chave)) {
    try {
      cache.set(chave, await lancamentos.descricoes(categoriaId, termo || undefined))
    } catch {
      return [] // sugestão é ajuda: sem ela, o campo continua funcionando (e a próxima tecla tenta de novo)
    }
  }
  return cache.get(chave) ?? []
}

function normal(s: string): string {
  return s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/\s+/g, ' ').trim()
}

// ——— Busca enquanto digita ———
const termo = computed(() => normal(props.modelValue))
const achadas = ref<string[]>([])
const buscou = ref('')
const aberto = ref(false)
const ativa = ref(-1)
let espera: ReturnType<typeof setTimeout> | undefined
let ultimaBusca = 0

function digitar(e: Event) {
  emit('update:modelValue', (e.target as HTMLInputElement).value)
  clearTimeout(espera)
  espera = setTimeout(buscar, ESPERA_DIGITACAO_MS)
}

async function buscar() {
  const id = props.categoriaId
  const t = termo.value
  if (!id || t.length < MINIMO_PARA_BUSCAR) {
    achadas.value = []
    aberto.value = false
    return
  }
  const esta = ++ultimaBusca
  const lista = await consultar(id, t)
  if (esta !== ultimaBusca) return // chegou depois de uma busca mais nova
  achadas.value = lista
  buscou.value = t
  ativa.value = -1
  aberto.value = document.activeElement?.id === props.id
}

// ——— As mais comuns (campo vazio) ———
const comuns = ref<string[]>([])

watch(() => [props.categoriaId, sessao.estado.versaoDados] as const, async ([id]) => {
  achadas.value = []
  aberto.value = false
  if (!id) {
    comuns.value = []
    return
  }
  const lista = await consultar(id, '')
  if (id === props.categoriaId) comuns.value = lista
}, { immediate: true })

/** O que já está escrito exatamente igual não precisa aparecer como opção. */
const opcoes = computed(() => achadas.value.filter((s) => s !== props.modelValue.trim()))
const mostrarLista = computed(() => aberto.value && termo.value.length >= MINIMO_PARA_BUSCAR && buscou.value === termo.value)

function escolher(s: string) {
  clearTimeout(espera)
  emit('update:modelValue', s)
  aberto.value = false
}

function fechar() {
  clearTimeout(espera)
  aberto.value = false
}

function teclar(e: KeyboardEvent) {
  if (!mostrarLista.value || !opcoes.value.length) return
  const n = opcoes.value.length
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    ativa.value = (ativa.value + 1) % n
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    ativa.value = (ativa.value - 1 + n) % n
  } else if (e.key === 'Enter' && ativa.value >= 0) {
    e.preventDefault()
    escolher(opcoes.value[ativa.value])
  } else if (e.key === 'Escape') {
    e.preventDefault()
    e.stopPropagation()
    fechar()
  }
}

/** Mesma descrição escrita de outro jeito (maiúsculas, acento, espaço). */
const parecida = computed(() =>
  termo.value ? [...achadas.value, ...comuns.value].find((s) => normal(s) === termo.value && s !== props.modelValue.trim()) ?? null : null,
)

/**
 * Separa o trecho que bateu com o termo para destacar, comparando letra a letra sem acento nem maiúscula (cada
 * letra vira uma só, então as posições batem com o texto original). Achada por semelhança: nada a destacar.
 */
function partes(s: string): { texto: string; bate: boolean }[] {
  const semAcento = (x: string) => x.split('').map((c) => c.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().charAt(0) || c).join('')
  const t = semAcento(props.modelValue.trim())
  const i = t ? semAcento(s).indexOf(t) : -1
  if (i < 0) return [{ texto: s, bate: false }]
  return [
    { texto: s.slice(0, i), bate: false },
    { texto: s.slice(i, i + t.length), bate: true },
    { texto: s.slice(i + t.length), bate: false },
  ].filter((p) => p.texto)
}

const idLista = computed(() => `${props.id}-lista`)
const idOpcao = (i: number) => `${props.id}-op-${i}`
const sugerirComuns = computed(() => !termo.value && comuns.value.length > 0)
</script>

<template>
  <div class="campo-descricao">
    <input :id="id" class="entrada" maxlength="200" required autocomplete="off" role="combobox"
           aria-autocomplete="list" :aria-expanded="mostrarLista" :aria-controls="idLista"
           :aria-activedescendant="mostrarLista && ativa >= 0 ? idOpcao(ativa) : undefined"
           :value="modelValue" :placeholder="placeholder" :aria-invalid="invalido || undefined"
           :aria-describedby="sugerirComuns || parecida ? `${id}-ajuda` : undefined"
           @input="digitar" @focus="buscar" @blur="fechar" @keydown="teclar" />

    <!-- mousedown.prevent: clicar numa opção não tira o foco do campo antes do clique. -->
    <div v-show="mostrarLista" :id="idLista" class="lista-opcoes" role="listbox" aria-label="Descrições já usadas"
         @mousedown.prevent>
      <template v-if="opcoes.length">
        <p class="cabeca-lista" role="presentation">Já usadas nesta categoria</p>
        <div v-for="(s, i) in opcoes" :id="idOpcao(i)" :key="s" role="option" class="opcao"
             :class="{ ativa: ativa === i }" :aria-selected="ativa === i" @mouseenter="ativa = i" @click="escolher(s)">
          <span><template v-for="(p, n) in partes(s)" :key="n"><mark v-if="p.bate">{{ p.texto }}</mark><template
            v-else>{{ p.texto }}</template></template></span>
        </div>
      </template>
      <p v-else-if="!parecida" class="sem-resultado" role="presentation">
        Nada parecido nesta categoria: vai entrar como uma descrição nova.
      </p>
    </div>

    <p v-if="parecida" :id="`${id}-ajuda`" class="parecida">
      Já existe <strong>“{{ parecida }}”</strong> nesta categoria.
      <button type="button" class="botao botao-sutil botao-pequeno" @click="escolher(parecida)">
        Usar como já está
      </button>
    </p>
    <div v-else-if="sugerirComuns" :id="`${id}-ajuda`" class="sugestoes">
      <span class="rotulo-sugestoes">Mais comuns:</span>
      <button v-for="s in comuns" :key="s" type="button" class="sugestao" @click="escolher(s)">{{ s }}</button>
      <span class="rotulo-sugestoes">ou digite para buscar</span>
    </div>
  </div>
</template>

<style scoped>
.campo-descricao {
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

.cabeca-lista {
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
  min-height: 2.5rem;
  padding: var(--e-1) var(--e-3);
  font-size: var(--t-sm);
  overflow-wrap: anywhere;
  cursor: pointer;
}

.opcao.ativa {
  background: var(--petroleo-tinta);
}

.opcao mark {
  background: none;
  color: inherit;
  font-weight: 700;
}

.sem-resultado {
  padding: var(--e-2) var(--e-3);
  font-size: var(--t-sm);
  line-height: 1.45;
  color: var(--texto-2);
}

.sugestoes {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--e-1) var(--e-2);
}

.rotulo-sugestoes {
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.sugestao {
  max-width: 100%;
  min-height: 2rem;
  padding: 0.125rem 0.625rem;
  border: 1px solid var(--pedra-forte);
  border-radius: 999px;
  background: var(--papel);
  color: var(--petroleo-escuro);
  font: inherit;
  font-size: var(--t-xs);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.sugestao:hover {
  border-color: var(--petroleo);
  background: var(--petroleo-tinta);
}

.parecida {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--e-1) var(--e-2);
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.parecida strong {
  color: var(--texto);
}
</style>
