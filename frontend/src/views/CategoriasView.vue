<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ArrowLeft, ChevronRight, Plus, Repeat, Search, X } from 'lucide-vue-next'
import { categorias as api, familia as apiFamilia } from '../api/servicos'
import type { Categoria, Estimativa, Grupo, Natureza, Tipo } from '../api/tipos'
import { ErroApi, mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import { catalogo } from '../estado/catalogo'
import { avisar } from '../estado/avisos'
import { ESTIMATIVAS, pct } from '../util/formato'
import Mensagem from '../componentes/Mensagem.vue'

// Plano de contas em duas colunas: os grupos do tipo à esquerda e as categorias do grupo escolhido à direita. A
// busca atravessa os grupos do tipo. No celular, a lista de grupos abre o grupo numa tela própria.
const dono = computed(() => sessao.dono.value)
const temPj = computed(() => sessao.tem('DESPESAS_PJ'))
const carregando = ref(true)
const erro = ref('')
const erroAcao = ref('')

onMounted(async () => {
  try {
    await catalogo.carregar(true)
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    carregando.value = false
  }
})

const tipo = ref<Tipo>('DESPESA')
const busca = ref('')
const mostrarDesativadas = ref(false)
const grupoId = ref<number | null>(null)
/** Só no celular: a lista de grupos dá lugar ao grupo aberto. */
const grupoAberto = ref(false)
const detalhe = ref<HTMLElement | null>(null)

function normal(s: string): string {
  return s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim()
}

const temEmpresa = computed(() => catalogo.estado.grupos.some((g) => g.empresa))
const contagemTipo = computed(() => ({
  DESPESA: catalogo.estado.grupos.filter((g) => g.tipo === 'DESPESA').reduce((s, g) => s + g.categorias.filter((c) => c.ativa).length, 0),
  RECEITA: catalogo.estado.grupos.filter((g) => g.tipo === 'RECEITA').reduce((s, g) => s + g.categorias.filter((c) => c.ativa).length, 0),
}))

const gruposDoTipoAtual = computed(() =>
  catalogo.estado.grupos.filter((g) => g.tipo === tipo.value && (g.ativo || mostrarDesativadas.value || g.id === grupoId.value)),
)
const grupo = computed(() => catalogo.estado.grupos.find((g) => g.id === grupoId.value) ?? null)

function visiveis(g: Grupo): Categoria[] {
  return mostrarDesativadas.value ? g.categorias : g.categorias.filter((c) => c.ativa)
}

const termo = computed(() => normal(busca.value))
const resultados = computed(() => {
  if (!termo.value) return []
  return gruposDoTipoAtual.value.flatMap((g) =>
    g.categorias.filter((c) => (c.ativa || mostrarDesativadas.value) && normal(c.nome).includes(termo.value)).map((c) => ({ c, g })),
  )
})
const quantasNoGrupo = (g: Grupo) =>
  termo.value ? resultados.value.filter((r) => r.g.id === g.id).length : visiveis(g).length

/** Linhas do painel da direita: o grupo escolhido ou, buscando, o que achou em todos os grupos do tipo. */
const linhas = computed(() =>
  termo.value ? resultados.value : grupo.value ? visiveis(grupo.value).map((c) => ({ c, g: grupo.value! })) : [],
)
const desativadasOcultas = computed(() => (grupo.value ? grupo.value.categorias.filter((c) => !c.ativa).length : 0))

// Mantém um grupo escolhido coerente com o tipo (e escolhe o primeiro ao entrar).
watch([tipo, () => catalogo.estado.grupos], () => {
  const g = grupo.value
  if (!g || g.tipo !== tipo.value) grupoId.value = gruposDoTipoAtual.value[0]?.id ?? null
}, { immediate: true })

function escolherGrupo(g: Grupo) {
  grupoId.value = g.id
  busca.value = ''
  grupoAberto.value = true
  editando.value = null
  excluirAlvo.value = null
  // No celular o grupo abre no lugar da lista: leva a tela até ele.
  if (window.matchMedia('(max-width: 760px)').matches) nextTick(() => detalhe.value?.scrollIntoView({ block: 'start' }))
}

function trocarTipo(t: Tipo) {
  if (tipo.value === t) return
  tipo.value = t
  grupoAberto.value = false
  editando.value = null
}

async function executar(acao: () => Promise<unknown>, ok: string) {
  erroAcao.value = ''
  try {
    await acao()
    await catalogo.carregar(true)
    sessao.avisarMudanca()
    avisar(ok)
    return true
  } catch (e) {
    erroAcao.value = e instanceof ErroApi && e.status === 409 && e.message.toLowerCase().includes('lança')
      ? `${e.message} Para guardar o histórico, desative em vez de excluir.`
      : mensagemDeErro(e)
    return false
  }
}

function dadosDe(c: Categoria) {
  return { grupoId: c.grupoId, nome: c.nome, ativa: c.ativa, tributavel: c.tributavel, aliquota: c.aliquota, recorrente: c.recorrente }
}

// ——— Edição em linha ———
const editando = ref<string | null>(null)
const form = reactive({
  nome: '', natureza: 'FIXA' as Natureza, grupoId: 0, aliquota: null as number | null, tributavel: false, recorrente: false,
  estimativa: 'FAMILIA' as Estimativa | 'FAMILIA',
})

function editarGrupo(g: Grupo) {
  editando.value = `g${g.id}`
  form.nome = g.nome
  form.natureza = g.natureza
}

function editarCategoria(c: Categoria) {
  editando.value = `c${c.id}`
  form.nome = c.nome
  form.grupoId = c.grupoId
  form.aliquota = c.aliquota
  form.tributavel = c.tributavel
  form.recorrente = c.recorrente
  form.estimativa = c.estimativa ?? 'FAMILIA'
}

async function salvarGrupo(g: Grupo) {
  if (!form.nome.trim()) return
  if (await executar(() => api.atualizarGrupo(g.id, { natureza: form.natureza, nome: form.nome.trim(), empresa: g.empresa, ativo: g.ativo }), 'Grupo atualizado.'))
    editando.value = null
}

async function salvarCategoria(c: Categoria) {
  if (!form.nome.trim()) return
  const movida = form.grupoId !== c.grupoId
  if (await executar(() => api.atualizar(c.id, {
    ...dadosDe(c),
    grupoId: form.grupoId, nome: form.nome.trim(),
    tributavel: temPj.value ? form.tributavel : c.tributavel,
    aliquota: temPj.value ? form.aliquota : c.aliquota,
    recorrente: form.aliquota == null ? form.recorrente : false,
    estimativa: form.estimativa,
  }), movida ? 'Categoria movida.' : 'Categoria atualizada.'))
    editando.value = null
}

function alternarGrupo(g: Grupo) {
  executar(() => api.atualizarGrupo(g.id, { natureza: g.natureza, nome: g.nome, empresa: g.empresa, ativo: !g.ativo }),
    g.ativo ? `${g.nome} desativado. O histórico continua no ano.` : `${g.nome} reativado.`)
}

function alternarCategoria(c: Categoria) {
  executar(() => api.atualizar(c.id, { ...dadosDe(c), ativa: !c.ativa }),
    c.ativa ? `${c.nome} desativada. Não recebe novos lançamentos.` : `${c.nome} reativada.`)
}

function alternarRecorrente(c: Categoria) {
  executar(() => api.atualizar(c.id, { ...dadosDe(c), recorrente: !c.recorrente }),
    c.recorrente ? `${c.nome}: ao lançar, vale só para o mês.` : `${c.nome}: ao lançar, já sugere repetir no ano.`)
}

const excluirAlvo = ref<string | null>(null)

async function excluirGrupo(g: Grupo) {
  excluirAlvo.value = null
  if (await executar(() => api.removerGrupo(g.id), `${g.nome} excluído.`)) grupoAberto.value = false
}

function excluirCategoria(c: Categoria) {
  excluirAlvo.value = null
  executar(() => api.remover(c.id), `${c.nome} excluída.`)
}

// ——— Novos ———
const nova = reactive({ nome: '', recorrente: false })
const novoGrupo = reactive({ nome: '', natureza: 'FIXA' as Natureza, aberto: false })
const campoNovoGrupo = ref<HTMLInputElement | null>(null)

async function criarCategoria() {
  const g = grupo.value
  const nome = nova.nome.trim()
  if (!g || !nome) return
  if (await executar(() => api.criar({ grupoId: g.id, nome, recorrente: nova.recorrente }), `${nome} criada em ${g.nome}.`)) {
    nova.nome = ''
    nova.recorrente = false
  }
}

async function abrirNovoGrupo() {
  novoGrupo.aberto = true
  await nextTick()
  campoNovoGrupo.value?.focus()
}

async function criarGrupo() {
  const nome = novoGrupo.nome.trim()
  if (!nome) return
  let criado: Grupo | null = null
  if (await executar(async () => { criado = await api.criarGrupo({ tipo: tipo.value, natureza: novoGrupo.natureza, nome }) }, `Grupo ${nome} criado.`)) {
    novoGrupo.nome = ''
    novoGrupo.aberto = false
    if (criado) escolherGrupo(criado)
  }
}

// ——— Estimativa dos próximos meses (regra da família; a categoria pode ter a sua) ———
const estimativaFamilia = computed<Estimativa>(() => sessao.estado.familia?.estimativa ?? 'MEDIA')
const opcoesEstimativa: Estimativa[] = ['MEDIA', 'MAXIMO', 'MINIMO', 'NENHUMA']

async function definirEstimativa(e: Estimativa) {
  if (e === estimativaFamilia.value) return
  erroAcao.value = ''
  try {
    const f = await apiFamilia.definirEstimativa(e)
    if (sessao.estado.familia) sessao.estado.familia.estimativa = f.estimativa
    sessao.avisarMudanca()
    avisar(e === 'NENHUMA' ? 'Os próximos meses deixam de ser estimados.' : `Os próximos meses agora são estimados ${ESTIMATIVAS[e].como}.`)
  } catch (err) {
    erroAcao.value = mensagemDeErro(err)
  }
}

/** A categoria recebe estimativa? Só despesa que não se repete e não é calculada. */
function estimavel(c: Categoria, g: Grupo) {
  return g.tipo === 'DESPESA' && !c.recorrente && !c.calculada
}

function aplicarPj() {
  executar(() => api.aplicarModeloPj(), 'Modelo PJ aplicado. Ajuste as alíquotas dos impostos da empresa.')
}

const gruposParaMover = computed(() => catalogo.estado.grupos.filter((g) => g.tipo === tipo.value))
const rotuloNatureza = (n: Natureza) => (n === 'FIXA' ? 'fixa' : 'variável')
</script>

<template>
  <div class="pagina">
    <header class="pagina-cabecalho">
      <div>
        <h1>Categorias</h1>
        <p v-if="dono">
          Os grupos e categorias que organizam o ano. Desativar guarda o histórico e só impede novos lançamentos;
          excluir só vale para o que nunca foi usado.
        </p>
        <p v-else>Os grupos e categorias que organizam o ano. Quem organiza a família pode mudar esta lista.</p>
      </div>
    </header>

    <Mensagem v-if="erroAcao" tipo="erro" class="erro-acao">{{ erroAcao }}</Mensagem>

    <div v-if="carregando" class="layout" aria-busy="true">
      <div class="esqueleto" style="height: 24rem" />
      <div class="esqueleto" style="height: 24rem" />
    </div>
    <Mensagem v-else-if="erro" tipo="erro">{{ erro }}</Mensagem>

    <template v-else>
      <section v-if="dono && temPj && !temEmpresa" class="painel painel-corpo pj">
        <div>
          <h2>Tem renda PJ?</h2>
          <p class="texto-2">
            O modelo PJ cria a receita "Notas fiscais emitidas" e o grupo da empresa com os impostos (DAS, ISS, PIS,
            IRPJ, CSLL) calculados todo mês sobre o que foi faturado, mais contador e pró-labore.
          </p>
        </div>
        <button type="button" class="botao" @click="aplicarPj">Aplicar modelo PJ</button>
      </section>

      <div class="ferramentas">
        <div class="segmentos" role="group" aria-label="Tipo">
          <button type="button" :aria-pressed="tipo === 'DESPESA'" @click="trocarTipo('DESPESA')">
            Despesas <span class="contagem num">{{ contagemTipo.DESPESA }}</span>
          </button>
          <button type="button" :aria-pressed="tipo === 'RECEITA'" @click="trocarTipo('RECEITA')">
            Receitas <span class="contagem num">{{ contagemTipo.RECEITA }}</span>
          </button>
        </div>
        <div class="busca">
          <Search class="busca-icone" aria-hidden="true" />
          <input v-model="busca" class="entrada" type="search" maxlength="120"
                 :placeholder="`Buscar nas ${tipo === 'DESPESA' ? 'despesas' : 'receitas'}`"
                 :aria-label="`Buscar categoria de ${tipo === 'DESPESA' ? 'despesa' : 'receita'}`"
                 @keydown.esc="busca = ''" />
          <button v-if="busca" type="button" class="limpar" aria-label="Limpar a busca" @click="busca = ''">
            <X aria-hidden="true" />
          </button>
        </div>
        <label class="caixa-marcar">
          <input v-model="mostrarDesativadas" type="checkbox" /> Mostrar desativadas
        </label>
      </div>

      <section v-if="tipo === 'DESPESA'" class="estimativa painel painel-corpo" aria-labelledby="titulo-estimativa">
        <div class="estimativa-texto">
          <h2 id="titulo-estimativa">Estimativa dos próximos meses</h2>
          <p class="texto-2">
            Despesas que não se repetem (supermercado, farmácia, combustível) ganham uma estimativa nos meses que
            faltam do ano, a partir dos meses que já passaram. Aparece como previsto, e o que for lançado no mês
            ocupa o lugar dela. Cada categoria pode seguir esta regra ou ter a sua.
          </p>
        </div>
        <div v-if="dono" class="segmentos" role="group" aria-label="Regra da família para estimar">
          <button v-for="e in opcoesEstimativa" :key="e" type="button" :aria-pressed="estimativaFamilia === e"
                  @click="definirEstimativa(e)">{{ ESTIMATIVAS[e].rotulo }}</button>
        </div>
        <p v-else class="selo">{{ ESTIMATIVAS[estimativaFamilia].rotulo }}</p>
      </section>

      <div class="layout" :class="{ aberto: grupoAberto || !!termo }">
        <nav class="grupos painel" :aria-label="`Grupos de ${tipo === 'DESPESA' ? 'despesas' : 'receitas'}`">
          <ul>
            <li v-for="g in gruposDoTipoAtual" :key="g.id">
              <button type="button" class="grupo-item" :class="{ inativo: !g.ativo, 'sem-resultado': !!termo && !quantasNoGrupo(g) }"
                      :aria-current="!termo && g.id === grupoId ? 'true' : undefined" @click="escolherGrupo(g)">
                <span class="grupo-nome">
                  {{ g.nome }}
                  <span class="grupo-meta">{{ rotuloNatureza(g.natureza) }}<template v-if="g.empresa"> · empresa</template><template v-if="!g.ativo"> · desativado</template></span>
                </span>
                <span class="num contagem">{{ quantasNoGrupo(g) }}</span>
                <ChevronRight class="seta" aria-hidden="true" />
              </button>
            </li>
          </ul>

          <div v-if="dono" class="novo-grupo">
            <button v-if="!novoGrupo.aberto" type="button" class="botao botao-sutil botao-pequeno" @click="abrirNovoGrupo">
              <Plus aria-hidden="true" /> Novo grupo
            </button>
            <form v-else class="form-novo-grupo" @submit.prevent="criarGrupo">
              <input ref="campoNovoGrupo" v-model="novoGrupo.nome" class="entrada" maxlength="80"
                     :placeholder="`Grupo de ${tipo === 'DESPESA' ? 'despesas' : 'receitas'}`" aria-label="Nome do grupo"
                     @keydown.esc="novoGrupo.aberto = false" />
              <select v-model="novoGrupo.natureza" class="selecao" aria-label="Natureza">
                <option value="FIXA">Fixa</option>
                <option value="VARIAVEL">Variável</option>
              </select>
              <div class="acoes">
                <button type="submit" class="botao botao-primario botao-pequeno" :disabled="!novoGrupo.nome.trim()">Criar grupo</button>
                <button type="button" class="botao botao-sutil botao-pequeno" @click="novoGrupo.aberto = false">Cancelar</button>
              </div>
            </form>
          </div>
        </nav>

        <section ref="detalhe" class="detalhe painel" :aria-labelledby="termo ? 'titulo-busca' : 'titulo-grupo'">
          <button type="button" class="botao botao-sutil botao-pequeno voltar" @click="grupoAberto = false; busca = ''">
            <ArrowLeft aria-hidden="true" /> Todos os grupos
          </button>

          <header v-if="termo" class="detalhe-cabecalho">
            <h2 id="titulo-busca">
              {{ resultados.length ? `${resultados.length} ${resultados.length === 1 ? 'categoria' : 'categorias'} com "${busca.trim()}"` : `Nada com "${busca.trim()}"` }}
            </h2>
          </header>

          <header v-else-if="grupo" class="detalhe-cabecalho" :class="{ inativo: !grupo.ativo }">
            <form v-if="editando === `g${grupo.id}`" class="editar-linha" @submit.prevent="salvarGrupo(grupo)">
              <input v-model="form.nome" class="entrada" maxlength="80" aria-label="Nome do grupo" required />
              <select v-model="form.natureza" class="selecao" aria-label="Natureza">
                <option value="FIXA">Fixa</option>
                <option value="VARIAVEL">Variável</option>
              </select>
              <button type="submit" class="botao botao-primario botao-pequeno">Salvar</button>
              <button type="button" class="botao botao-pequeno" @click="editando = null">Cancelar</button>
            </form>
            <template v-else>
              <div class="titulo-grupo">
                <h2 id="titulo-grupo">{{ grupo.nome }}</h2>
                <span class="selo">{{ rotuloNatureza(grupo.natureza) }}</span>
                <span v-if="grupo.empresa" class="selo selo-petroleo">empresa</span>
                <span v-if="!grupo.ativo" class="selo">desativado</span>
              </div>
              <div v-if="dono" class="acoes acoes-grupo">
                <template v-if="excluirAlvo === `g${grupo.id}`">
                  <span class="texto-2 pergunta">Excluir o grupo {{ grupo.nome }}?</span>
                  <button type="button" class="botao botao-perigo botao-pequeno" @click="excluirGrupo(grupo)">Excluir grupo</button>
                  <button type="button" class="botao botao-pequeno" @click="excluirAlvo = null">Manter</button>
                </template>
                <template v-else>
                  <button type="button" class="botao botao-sutil botao-pequeno" @click="editarGrupo(grupo)">Editar grupo</button>
                  <button type="button" class="botao botao-sutil botao-pequeno" @click="alternarGrupo(grupo)">{{ grupo.ativo ? 'Desativar' : 'Reativar' }}</button>
                  <button v-if="!grupo.categorias.length" type="button" class="botao botao-sutil botao-pequeno" @click="excluirAlvo = `g${grupo.id}`">Excluir</button>
                </template>
              </div>
            </template>
          </header>

          <p v-if="!termo && grupo" class="explica-repete texto-2">
            <Repeat aria-hidden="true" />
            <span><strong>Repete todo mês</strong>: ao lançar nesta categoria, o lançamento já vem marcado para repetir
              no ano (como aluguel, telefone ou salário) e os próximos meses aparecem como previstos.</span>
          </p>

          <ul v-if="linhas.length" class="lista categorias">
            <li v-for="{ c, g } in linhas" :key="c.id" :class="{ inativo: !c.ativa }">
              <form v-if="editando === `c${c.id}`" class="editar-linha" @submit.prevent="salvarCategoria(c)">
                <input v-model="form.nome" class="entrada" maxlength="120" aria-label="Nome da categoria" required />
                <select v-model="form.grupoId" class="selecao" aria-label="Grupo">
                  <option v-for="og in gruposParaMover" :key="og.id" :value="og.id">{{ og.nome }}</option>
                </select>
                <label v-if="!c.calculada" class="caixa-marcar">
                  <input v-model="form.recorrente" type="checkbox" /> Repete todo mês
                </label>
                <label v-if="g.tipo === 'DESPESA' && !c.calculada && !form.recorrente" class="estimar">
                  <span>Estimar</span>
                  <select v-model="form.estimativa" class="selecao">
                    <option value="FAMILIA">Como a família ({{ ESTIMATIVAS[estimativaFamilia].rotulo.toLowerCase() }})</option>
                    <option v-for="e in opcoesEstimativa" :key="e" :value="e">{{ ESTIMATIVAS[e].rotulo }}</option>
                  </select>
                </label>
                <label v-if="temPj && g.tipo === 'DESPESA' && g.empresa" class="aliquota">
                  <span>Alíquota %</span>
                  <input v-model.number="form.aliquota" class="entrada num" type="number" min="0" max="100" step="0.01" />
                </label>
                <label v-if="temPj && g.tipo === 'RECEITA'" class="caixa-marcar">
                  <input v-model="form.tributavel" type="checkbox" /> Tributável (PJ)
                </label>
                <div class="acoes">
                  <button type="submit" class="botao botao-primario botao-pequeno">Salvar</button>
                  <button type="button" class="botao botao-pequeno" @click="editando = null">Cancelar</button>
                </div>
              </form>

              <template v-else>
                <span class="categoria">
                  <span class="nome">{{ c.nome }}</span>
                  <button v-if="termo" type="button" class="no-grupo" @click="escolherGrupo(g)">em {{ g.nome }}</button>
                </span>
                <span class="marcas">
                  <span v-if="c.calculada" class="selo selo-petroleo num">{{ pct(c.aliquota, 2) }} das notas</span>
                  <span v-if="c.tributavel" class="selo selo-petroleo">tributável</span>
                  <span v-if="!c.ativa" class="selo">desativada</span>
                  <span v-if="c.estimativa && estimavel(c, g)" class="selo" :class="{ 'selo-previsto': c.estimativa !== 'NENHUMA' }"
                        :title="`Estima os próximos meses ${ESTIMATIVAS[c.estimativa].como}`">
                    {{ c.estimativa === 'NENHUMA' ? 'sem estimativa' : `estima: ${ESTIMATIVAS[c.estimativa].rotulo.toLowerCase()}` }}
                  </span>
                </span>
                <span class="repete">
                  <label v-if="dono && !c.calculada" class="caixa-marcar" :title="c.recorrente ? 'Ao lançar, já sugere repetir no ano' : 'Ao lançar, vale só para o mês'">
                    <input type="checkbox" :checked="c.recorrente" :disabled="!c.ativa" @change="alternarRecorrente(c)" />
                    Repete todo mês
                  </label>
                  <span v-else-if="c.recorrente" class="selo selo-repete"><Repeat aria-hidden="true" /> repete todo mês</span>
                </span>
                <span v-if="dono" class="acoes acoes-linha">
                  <template v-if="excluirAlvo === `c${c.id}`">
                    <button type="button" class="botao botao-perigo botao-pequeno" @click="excluirCategoria(c)">Excluir {{ c.nome }}</button>
                    <button type="button" class="botao botao-pequeno" @click="excluirAlvo = null">Manter</button>
                  </template>
                  <template v-else>
                    <button type="button" class="botao botao-sutil botao-pequeno" @click="editarCategoria(c)">Editar</button>
                    <button type="button" class="botao botao-sutil botao-pequeno" @click="alternarCategoria(c)">{{ c.ativa ? 'Desativar' : 'Reativar' }}</button>
                    <button type="button" class="botao botao-sutil botao-pequeno" @click="excluirAlvo = `c${c.id}`">Excluir</button>
                  </template>
                </span>
              </template>
            </li>
          </ul>

          <p v-else-if="termo" class="texto-2 vazio">
            Nenhuma categoria de {{ tipo === 'DESPESA' ? 'despesa' : 'receita' }} com esse nome<template v-if="!mostrarDesativadas">
            (as desativadas não entram; marque "Mostrar desativadas")</template>.
            <template v-if="dono"> Escolha um grupo à esquerda para criá-la.</template>
          </p>
          <p v-else-if="grupo" class="texto-2 vazio">Nenhuma categoria neste grupo ainda.</p>

          <p v-if="!termo && desativadasOcultas && !mostrarDesativadas" class="texto-2 ocultas">
            {{ desativadasOcultas }} {{ desativadasOcultas === 1 ? 'desativada escondida' : 'desativadas escondidas' }}.
            <button type="button" class="botao botao-sutil botao-pequeno" @click="mostrarDesativadas = true">Mostrar</button>
          </p>

          <form v-if="dono && !termo && grupo?.ativo" class="nova-categoria" @submit.prevent="criarCategoria">
            <input v-model="nova.nome" class="entrada" maxlength="120" :aria-label="`Nova categoria em ${grupo.nome}`"
                   :placeholder="`Nova categoria em ${grupo.nome}`" />
            <label class="caixa-marcar">
              <input v-model="nova.recorrente" type="checkbox" /> Repete todo mês
            </label>
            <button type="submit" class="botao botao-pequeno" :disabled="!nova.nome.trim()">
              <Plus aria-hidden="true" /> Adicionar
            </button>
          </form>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.erro-acao {
  margin-bottom: var(--e-4);
  position: sticky;
  top: calc(var(--barra-altura) + var(--e-2));
  z-index: 5;
}

.pj {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--e-4);
  margin-bottom: var(--e-6);
}

.pj p {
  max-width: 70ch;
  margin-top: var(--e-1);
  font-size: var(--t-sm);
}

.estimativa {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--e-4);
  margin-bottom: var(--e-5);
}

.estimativa-texto {
  flex: 1 1 28rem;
}

.estimativa-texto h2 {
  font-size: var(--t-md);
}

.estimativa-texto p {
  max-width: 72ch;
  margin-top: var(--e-1);
  font-size: var(--t-sm);
}

.estimar {
  display: flex;
  align-items: center;
  gap: var(--e-2);
  font-size: var(--t-xs);
  font-weight: 600;
}

.editar-linha .estimar .selecao {
  flex: none;
  width: auto;
}

/* ——— Ferramentas: tipo, busca e filtro ——— */
.ferramentas {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--e-3) var(--e-5);
  margin-bottom: var(--e-4);
}

.segmentos .contagem {
  margin-left: var(--e-1);
  font-weight: 500;
  color: var(--texto-2);
}

.busca {
  position: relative;
  flex: 1 1 16rem;
  max-width: 26rem;
}

.busca .entrada {
  padding-left: 2.5rem;
  padding-right: 2.75rem;
}

.busca .entrada::-webkit-search-cancel-button {
  display: none;
}

.busca-icone {
  position: absolute;
  left: var(--e-3);
  top: 50%;
  width: 1.125rem;
  height: 1.125rem;
  transform: translateY(-50%);
  color: var(--texto-2);
  pointer-events: none;
}

.limpar {
  position: absolute;
  right: 0;
  top: 0;
  display: grid;
  place-items: center;
  width: 2.75rem;
  height: 2.75rem;
  border: 0;
  border-radius: var(--raio);
  background: none;
  color: var(--texto-2);
  cursor: pointer;
}

.limpar:hover {
  color: var(--texto);
}

.limpar svg {
  width: 1rem;
  height: 1rem;
}

/* ——— Duas colunas ——— */
.layout {
  display: grid;
  grid-template-columns: 17rem minmax(0, 1fr);
  gap: var(--e-5);
  align-items: start;
}

.grupos {
  position: sticky;
  top: calc(var(--barra-altura) + var(--e-4));
  max-height: calc(100vh - var(--barra-altura) - var(--e-8));
  overflow-y: auto;
  padding: var(--e-2);
}

.grupos ul {
  display: grid;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.grupo-item {
  display: flex;
  align-items: center;
  gap: var(--e-2);
  width: 100%;
  min-height: 3rem;
  padding: var(--e-2) var(--e-3);
  border: 0;
  border-radius: var(--raio);
  background: none;
  color: var(--texto);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color 160ms var(--ease);
}

.grupo-item:hover {
  background: var(--areia);
}

.grupo-item[aria-current='true'] {
  background: var(--petroleo-tinta);
  color: var(--petroleo-noite);
}

.grupo-nome {
  display: grid;
  flex: 1;
  min-width: 0;
  font-size: var(--t-sm);
  font-weight: 600;
}

.grupo-meta {
  font-size: var(--t-xs);
  font-weight: 400;
  color: var(--texto-2);
}

.grupo-item .contagem {
  font-size: var(--t-xs);
  font-weight: 600;
  color: var(--texto-2);
}

.grupo-item .seta {
  display: none;
  width: 1rem;
  height: 1rem;
  color: var(--texto-2);
}

.grupo-item.inativo .grupo-nome {
  color: var(--cinza-claro);
}

.grupo-item.sem-resultado {
  opacity: 0.55;
}

.novo-grupo {
  margin-top: var(--e-2);
  padding: var(--e-2) var(--e-1) var(--e-1);
  border-top: 1px solid var(--linha);
}

.form-novo-grupo {
  display: grid;
  gap: var(--e-2);
}

.form-novo-grupo .entrada,
.form-novo-grupo .selecao {
  min-height: 2.5rem;
  font-size: var(--t-sm);
}

/* ——— Grupo escolhido ——— */
.detalhe {
  min-width: 0;
  overflow: hidden;
  scroll-margin-top: calc(var(--barra-altura) + var(--e-4));
}

.voltar {
  display: none;
  margin: var(--e-3) var(--e-3) 0;
}

.detalhe-cabecalho {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--e-3);
  padding: var(--e-4) var(--e-5);
  border-bottom: 1px solid var(--linha);
}

.titulo-grupo {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--e-2);
}

.detalhe-cabecalho h2 {
  font-size: var(--t-lg);
}

.detalhe-cabecalho.inativo h2 {
  color: var(--cinza-claro);
}

.acoes-grupo {
  margin-left: auto;
  gap: 0;
  align-items: center;
}

.pergunta {
  margin-right: var(--e-2);
  font-size: var(--t-sm);
}

.explica-repete {
  display: flex;
  gap: var(--e-2);
  padding: var(--e-3) var(--e-5);
  font-size: var(--t-xs);
  line-height: 1.5;
  border-bottom: 1px solid var(--linha);
}

.explica-repete svg {
  flex: none;
  width: 1rem;
  height: 1rem;
  margin-top: 1px;
}

.explica-repete span {
  max-width: 75ch;
}

.explica-repete strong {
  color: var(--texto);
}

.categorias > li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 10.5rem auto;
  align-items: center;
  gap: var(--e-3);
  min-height: 3.25rem;
  padding: var(--e-1) var(--e-3) var(--e-1) var(--e-5);
}

.categorias > li:has(.editar-linha) {
  display: block;
}

.categoria {
  display: grid;
  min-width: 0;
}

.nome {
  font-size: var(--t-sm);
  font-weight: 500;
  overflow-wrap: anywhere;
}

.no-grupo {
  justify-self: start;
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  font-size: var(--t-xs);
  color: var(--texto-2);
  text-decoration: underline;
  text-decoration-color: var(--pedra-forte);
  text-underline-offset: 2px;
  cursor: pointer;
}

.no-grupo:hover {
  color: var(--acao);
}

.marcas {
  display: flex;
  flex-wrap: wrap;
  gap: var(--e-1);
}

.repete .caixa-marcar {
  font-size: var(--t-sm);
  white-space: nowrap;
}

.selo-repete {
  display: inline-flex;
  align-items: center;
  gap: var(--e-1);
}

.selo-repete svg {
  width: 0.875rem;
  height: 0.875rem;
}

.acoes-linha {
  justify-content: flex-end;
  gap: 0;
}

.inativo .nome {
  color: var(--cinza-claro);
}

.vazio,
.ocultas {
  padding: var(--e-5);
  font-size: var(--t-sm);
}

.ocultas {
  display: flex;
  align-items: center;
  gap: var(--e-2);
  padding-top: var(--e-3);
  padding-bottom: var(--e-3);
  border-top: 1px solid var(--linha);
}

.editar-linha {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--e-2) var(--e-3);
  width: 100%;
  padding: var(--e-2) 0;
}

.editar-linha .entrada {
  flex: 2 1 12rem;
  min-height: 2.25rem;
}

.editar-linha .selecao {
  flex: 1 1 9rem;
  min-height: 2.25rem;
  width: auto;
}

.aliquota {
  display: flex;
  align-items: center;
  gap: var(--e-2);
  font-size: var(--t-xs);
  font-weight: 600;
}

.aliquota .entrada {
  width: 6rem;
  flex: none;
}

.nova-categoria {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--e-2) var(--e-4);
  padding: var(--e-4) var(--e-5);
  border-top: 1px solid var(--linha);
  background: var(--areia);
}

.nova-categoria .entrada {
  flex: 1 1 14rem;
  min-height: 2.25rem;
  font-size: var(--t-sm);
}

.nova-categoria .caixa-marcar {
  font-size: var(--t-sm);
}

/* Com mouse, as ações da linha aparecem ao passar ou com foco; no toque ficam sempre visíveis. */
@media (hover: hover) {
  .categorias > li .acoes-linha {
    opacity: 0;
    transition: opacity 150ms var(--ease);
  }

  .categorias > li:hover .acoes-linha,
  .categorias > li:focus-within .acoes-linha {
    opacity: 1;
  }

  .categorias > li:hover {
    background: var(--areia);
  }
}

@media (max-width: 1100px) {
  .categorias > li {
    grid-template-columns: minmax(0, 1fr) auto auto;
  }

  .acoes-linha {
    grid-column: 1 / -1;
    justify-content: flex-start;
    margin-left: calc(var(--e-3) * -1);
  }

  .categorias > li .acoes-linha {
    opacity: 1;
  }
}

/* Celular: a lista de grupos abre o grupo numa tela própria, com "Todos os grupos" para voltar. */
@media (max-width: 760px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .grupos {
    position: static;
    max-height: none;
  }

  .layout.aberto .grupos,
  .layout:not(.aberto) .detalhe {
    display: none;
  }

  .grupo-item .seta,
  .voltar {
    display: inline-flex;
  }

  .busca {
    max-width: none;
    flex-basis: 100%;
  }

  .detalhe-cabecalho,
  .explica-repete,
  .nova-categoria {
    padding-left: var(--e-4);
    padding-right: var(--e-4);
  }

  .categorias > li {
    grid-template-columns: minmax(0, 1fr) auto;
    padding-left: var(--e-4);
  }

  .repete {
    grid-column: 1 / -1;
  }

  .acoes-grupo {
    margin-left: calc(var(--e-3) * -1);
    width: 100%;
  }
}
</style>
