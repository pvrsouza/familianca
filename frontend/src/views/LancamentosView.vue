<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft, ChevronRight, Repeat, Search, X } from 'lucide-vue-next'
import { lancamentos as apiLancamentos, type OrigemLancamento } from '../api/servicos'
import type { Lancamento, Tipo } from '../api/tipos'
import { mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import { catalogo } from '../estado/catalogo'
import { avisar } from '../estado/avisos'
import { MESES, anoAtual, competenciaPorExtenso, dataCurta, dataPorExtenso, mesAtual, reais } from '../util/formato'
import PainelLateral from '../componentes/PainelLateral.vue'
import FormLancamento from '../componentes/FormLancamento.vue'
import EstadoVazio from '../componentes/EstadoVazio.vue'
import Mensagem from '../componentes/Mensagem.vue'

const route = useRoute()
const router = useRouter()

// Filtros vivem na URL: dá para voltar, recarregar, guardar o link e chegar aqui a partir da tabela do ano.
const ano = computed(() => Number(route.query.ano) || anoAtual())
const categoriaId = computed(() => Number(route.query.categoriaId) || null)
const dataValida = (v: unknown) => (typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v) ? v : '')
const de = computed(() => dataValida(route.query.de))
const ate = computed(() => dataValida(route.query.ate))
/**
 * Período: um mês (padrão), o ano inteiro ou datas livres. Vindo da tabela do ano com uma categoria (sem mês), o ano.
 */
const modo = computed<'mes' | 'ano' | 'datas'>(() => {
  if (route.query.periodo === 'datas') return 'datas'
  if (route.query.periodo === 'ano' || (!route.query.periodo && categoriaId.value && !route.query.mes)) return 'ano'
  return 'mes'
})
const mes = computed(() => (modo.value !== 'mes' ? null : Number(route.query.mes) || (ano.value === anoAtual() ? mesAtual() : 1)))
const tipo = computed<Tipo | null>(() => (route.query.tipo === 'RECEITA' || route.query.tipo === 'DESPESA' ? route.query.tipo : null))
const membroId = computed(() => Number(route.query.membroId) || null)
const termo = computed(() => (typeof route.query.q === 'string' ? route.query.q : ''))
const ORIGENS: Record<OrigemLancamento, string> = {
  AVULSO: 'Lançados uma vez',
  RECORRENTE: 'Confirmações do que se repete',
  PARCELADO: 'Parcelas de compras',
}
const origem = computed<OrigemLancamento | null>(() =>
  typeof route.query.origem === 'string' && route.query.origem in ORIGENS ? (route.query.origem as OrigemLancamento) : null)
/** Algum filtro além do período: o vazio diz "nada encontrado" e oferece limpar. */
const filtrando = computed(() => !!(termo.value || categoriaId.value || origem.value || tipo.value || membroId.value))

// A busca vai para a URL (e para o servidor) só quando a pessoa para de digitar.
const busca = ref(termo.value)
let esperaBusca: ReturnType<typeof setTimeout> | undefined
watch(busca, (v) => {
  clearTimeout(esperaBusca)
  esperaBusca = setTimeout(() => filtrar({ q: v.trim() || null }), 300)
})
watch(termo, (t) => { if (t !== busca.value.trim()) busca.value = t })

const lista = ref<Lancamento[]>([])
const carregando = ref(true)
const erro = ref('')
const novo = ref(route.query.novo === '1')
const editar = ref<Lancamento | null>(null)
const confirmandoExclusao = ref(false)
const excluindo = ref(false)
const erroExclusao = ref('')

const membros = computed(() => sessao.estado.familia?.membros ?? [])
/** Grupos para o filtro de categoria, só do tipo escolhido (Despesas/Receitas). */
const gruposDoFiltro = computed(() => catalogo.estado.grupos.filter((g) => !tipo.value || g.tipo === tipo.value))

function periodo(): { de: string; ate: string } {
  if (modo.value === 'datas') {
    return { de: de.value || `${ano.value}-01-01`, ate: ate.value || `${ano.value}-12-31` }
  }
  if (mes.value === null) return { de: `${ano.value}-01-01`, ate: `${ano.value}-12-31` }
  const m = String(mes.value).padStart(2, '0')
  const ultimo = new Date(ano.value, mes.value, 0).getDate()
  return { de: `${ano.value}-${m}-01`, ate: `${ano.value}-${m}-${ultimo}` }
}

async function carregar() {
  carregando.value = true
  erro.value = ''
  try {
    lista.value = await apiLancamentos.listar({
      ...periodo(),
      categoriaId: categoriaId.value ?? undefined,
      membroId: membroId.value ?? undefined,
      tipo: tipo.value ?? undefined,
      q: termo.value || undefined,
      origem: origem.value ?? undefined,
    })
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    carregando.value = false
  }
}

watch(() => [route.query.ano, route.query.mes, route.query.periodo, route.query.de, route.query.ate, route.query.categoriaId,
  route.query.tipo, route.query.membroId, route.query.q, route.query.origem, sessao.estado.versaoDados], carregar, { immediate: true })
onMounted(() => catalogo.carregar())

function filtrar(mudanca: Record<string, string | number | null | undefined>) {
  const q: Record<string, string> = {}
  const atual = { ...route.query, ...mudanca }
  Object.entries(atual).forEach(([k, v]) => {
    if (v !== null && v !== undefined && v !== '' && k !== 'novo') q[k] = String(v)
  })
  router.replace({ query: q })
}

function mudarModo(m: 'mes' | 'ano' | 'datas') {
  if (m === 'datas') {
    const atual = periodo()
    filtrar({ periodo: 'datas', de: atual.de, ate: atual.ate, mes: null })
  } else {
    filtrar({ periodo: m, de: null, ate: null, mes: m === 'mes' ? mes.value ?? (ano.value === anoAtual() ? mesAtual() : 1) : null })
  }
}

function mudarData(campo: 'de' | 'ate', valor: string) {
  if (valor) filtrar({ [campo]: valor })
}

function mudarTipo(t: Tipo | null) {
  // A categoria escolhida é de outro tipo: sai junto.
  const daCategoria = catalogo.estado.grupos.find((g) => g.categorias.some((c) => c.id === categoriaId.value))?.tipo
  filtrar({ tipo: t, categoriaId: t && daCategoria && daCategoria !== t ? null : categoriaId.value })
}

function limparFiltros() {
  busca.value = ''
  filtrar({ q: null, categoriaId: null, origem: null, tipo: null, membroId: null })
}

function mudarMes(delta: number) {
  let m = (mes.value ?? 1) + delta
  let a = ano.value
  if (m < 1) { m = 12; a-- }
  if (m > 12) { m = 1; a++ }
  filtrar({ mes: m, ano: a })
}

const porDia = computed(() => {
  const grupos = new Map<string, Lancamento[]>()
  for (const l of lista.value) {
    const arr = grupos.get(l.data) ?? []
    arr.push(l)
    grupos.set(l.data, arr)
  }
  return [...grupos.entries()]
})

const totalDespesas = computed(() => lista.value.filter((l) => l.tipo === 'DESPESA').reduce((s, l) => s + l.valor, 0))
const totalReceitas = computed(() => lista.value.filter((l) => l.tipo === 'RECEITA').reduce((s, l) => s + l.valor, 0))

function podeAlterar(l: Lancamento) {
  return sessao.dono.value || l.membro?.id === sessao.estado.usuario?.membroId
}

function abrir(l: Lancamento) {
  if (!podeAlterar(l)) return
  editar.value = l
  confirmandoExclusao.value = false
  erroExclusao.value = ''
}

function salvo(msg: string) {
  novo.value = false
  editar.value = null
  avisar(msg)
}

async function excluir(todas: boolean) {
  if (!editar.value) return
  excluindo.value = true
  erroExclusao.value = ''
  try {
    await apiLancamentos.remover(editar.value.id, todas)
    const eraConfirmacao = !!editar.value.recorrenciaId
    editar.value = null
    sessao.avisarMudanca()
    avisar(todas ? 'Compra excluída com todas as parcelas.' : eraConfirmacao
      ? 'Excluído. A conta volta a aparecer como prevista.' : 'Lançamento excluído.')
  } catch (e) {
    erroExclusao.value = mensagemDeErro(e)
  } finally {
    excluindo.value = false
  }
}

const tituloPeriodo = computed(() => {
  if (modo.value === 'datas') {
    const p = periodo()
    return `${dataCurta(p.de)} a ${dataCurta(p.ate)}`
  }
  return mes.value === null ? String(ano.value) : `${MESES[mes.value - 1]} de ${ano.value}`
})
const tituloMes = computed(() => tituloPeriodo.value.charAt(0).toUpperCase() + tituloPeriodo.value.slice(1))
</script>

<template>
  <div class="pagina">
    <header class="pagina-cabecalho">
      <div>
        <h1>Lançamentos</h1>
        <p>O que entrou e saiu, de quem é cada um. Cada pessoa altera os próprios; quem organiza altera todos.</p>
      </div>
    </header>

    <div class="filtros">
      <div class="filtros-linha">
        <div class="segmentos" role="group" aria-label="Período">
          <button type="button" :aria-pressed="modo === 'mes'" @click="mudarModo('mes')">Mês</button>
          <button type="button" :aria-pressed="modo === 'ano'" @click="mudarModo('ano')">Ano</button>
          <button type="button" :aria-pressed="modo === 'datas'" @click="mudarModo('datas')">Entre datas</button>
        </div>

        <div v-if="modo === 'mes'" class="periodo" role="group" aria-label="Mês">
          <button type="button" class="botao botao-icone botao-pequeno" aria-label="Mês anterior" @click="mudarMes(-1)">
            <ChevronLeft aria-hidden="true" />
          </button>
          <span class="periodo-nome">{{ tituloMes }}</span>
          <button type="button" class="botao botao-icone botao-pequeno" aria-label="Próximo mês" @click="mudarMes(1)">
            <ChevronRight aria-hidden="true" />
          </button>
        </div>
        <div v-else-if="modo === 'ano'" class="periodo" role="group" aria-label="Ano">
          <button type="button" class="botao botao-icone botao-pequeno" aria-label="Ano anterior" @click="filtrar({ ano: ano - 1 })">
            <ChevronLeft aria-hidden="true" />
          </button>
          <span class="periodo-nome">{{ ano }}</span>
          <button type="button" class="botao botao-icone botao-pequeno" aria-label="Próximo ano" @click="filtrar({ ano: ano + 1 })">
            <ChevronRight aria-hidden="true" />
          </button>
        </div>
        <div v-else class="datas" role="group" aria-label="Entre datas">
          <label class="data-filtro">
            <span>De</span>
            <input class="entrada" type="date" :value="periodo().de" :max="periodo().ate"
                   @change="mudarData('de', ($event.target as HTMLInputElement).value)" />
          </label>
          <label class="data-filtro">
            <span>Até</span>
            <input class="entrada" type="date" :value="periodo().ate" :min="periodo().de"
                   @change="mudarData('ate', ($event.target as HTMLInputElement).value)" />
          </label>
        </div>

        <div class="segmentos" role="group" aria-label="Tipo">
          <button type="button" :aria-pressed="tipo === null" @click="mudarTipo(null)">Tudo</button>
          <button type="button" :aria-pressed="tipo === 'DESPESA'" @click="mudarTipo('DESPESA')">Despesas</button>
          <button type="button" :aria-pressed="tipo === 'RECEITA'" @click="mudarTipo('RECEITA')">Receitas</button>
        </div>
      </div>

      <div class="filtros-linha">
        <label class="busca">
          <span class="sr">Buscar</span>
          <Search aria-hidden="true" />
          <input v-model="busca" class="entrada" type="search" maxlength="200"
                 placeholder="Buscar: descrição, observação, categoria ou pessoa" />
        </label>

        <label class="filtro-select">
          <span class="sr">Categoria</span>
          <select class="selecao" :value="categoriaId ?? ''"
                  @change="filtrar({ categoriaId: ($event.target as HTMLSelectElement).value || null })">
            <option value="">Todas as categorias</option>
            <optgroup v-for="g in gruposDoFiltro" :key="g.id" :label="g.nome">
              <option v-for="c in g.categorias" :key="c.id" :value="c.id">{{ c.nome }}</option>
            </optgroup>
          </select>
        </label>

        <label class="filtro-select">
          <span class="sr">Origem</span>
          <select class="selecao" :value="origem ?? ''"
                  @change="filtrar({ origem: ($event.target as HTMLSelectElement).value || null })">
            <option value="">Qualquer origem</option>
            <option v-for="(rotulo, chave) in ORIGENS" :key="chave" :value="chave">{{ rotulo }}</option>
          </select>
        </label>

        <label v-if="membros.length > 1" class="filtro-select">
          <span class="sr">Pessoa</span>
          <select class="selecao" :value="membroId ?? ''" @change="filtrar({ membroId: ($event.target as HTMLSelectElement).value || null })">
            <option value="">Toda a família</option>
            <option v-for="m in membros" :key="m.id" :value="m.id">{{ m.nome }}</option>
          </select>
        </label>

        <button v-if="filtrando" type="button" class="botao botao-sutil botao-pequeno" @click="limparFiltros">
          <X aria-hidden="true" /> Limpar filtros
        </button>
      </div>
    </div>

    <p v-if="!carregando && lista.length" class="totais">
      <span>Despesas <strong class="num">{{ reais(totalDespesas) }}</strong></span>
      <span>Receitas <strong class="num receita">{{ reais(totalReceitas) }}</strong></span>
      <span class="texto-2">{{ lista.length }} {{ lista.length === 1 ? 'lançamento' : 'lançamentos' }}</span>
    </p>

    <div v-if="carregando" class="painel" aria-busy="true">
      <div v-for="i in 5" :key="i" class="esqueleto linha-esqueleto" />
    </div>

    <Mensagem v-else-if="erro" tipo="erro">
      {{ erro }}
      <template #acoes><button type="button" class="botao botao-pequeno" @click="carregar">Tentar de novo</button></template>
    </Mensagem>

    <div v-else-if="!lista.length" class="painel">
      <EstadoVazio v-if="filtrando" :titulo="`Nada encontrado em ${tituloPeriodo}`">
        Nenhum lançamento do período bate com os filtros. Tente outro período ou tire algum filtro.
        <template #acoes>
          <button type="button" class="botao" @click="limparFiltros">Limpar filtros</button>
        </template>
      </EstadoVazio>
      <EstadoVazio v-else :titulo="`Nada lançado em ${tituloPeriodo}`">
        Lance o que já aconteceu. O que se repete todo mês fica melhor em recorrências: aparece como previsto e é só
        confirmar.
        <template #acoes>
          <button type="button" class="botao botao-primario" @click="novo = true">Lançar</button>
          <RouterLink to="/recorrencias" class="botao">Ver recorrências</RouterLink>
        </template>
      </EstadoVazio>
    </div>

    <div v-else class="dias painel">
      <section v-for="[dia, itens] in porDia" :key="dia" class="dia">
        <h2 class="dia-titulo">{{ dataPorExtenso(dia) }}</h2>
        <ul class="lista">
          <li v-for="l in itens" :key="l.id">
            <component :is="podeAlterar(l) ? 'button' : 'div'" :type="podeAlterar(l) ? 'button' : undefined"
                       class="item" :class="{ clicavel: podeAlterar(l) }" @click="abrir(l)">
              <span class="item-texto">
                <span class="descricao">{{ l.descricao }}</span>
                <span class="meta">
                  {{ l.categoria.nome }} · {{ l.grupo.nome }}<template v-if="l.membro"> · {{ l.membro.nome }}</template>
                  · {{ l.notaFiscal ? 'entrou na conta' : l.tipo === 'RECEITA' ? 'recebido' : 'pago' }} em {{ dataPorExtenso(l.data) }}
                  <template v-if="l.notaFiscal">
                    <br />Nota<template v-if="l.notaFiscal.competencia"> de {{ competenciaPorExtenso(l.notaFiscal.competencia) }}</template><template
                      v-if="l.notaFiscal.emissao">{{ l.notaFiscal.competencia ? ',' : '' }} emitida em {{ dataPorExtenso(l.notaFiscal.emissao, true) }}</template>
                  </template>
                </span>
                <span v-if="l.parcela || l.recorrenciaId" class="selos">
                  <span v-if="l.parcela" class="selo num">parcela {{ l.parcela.numero }}/{{ l.parcela.total }}</span>
                  <span v-if="l.recorrenciaId" class="selo selo-petroleo"><Repeat aria-hidden="true" /> confirmada</span>
                  <span v-if="l.diasAtraso != null" class="selo" :class="l.diasAtraso ? 'selo-atraso' : 'selo-verde'"
                        :title="`Vencimento em ${dataPorExtenso(l.vencimento!)}; pago em ${dataPorExtenso(l.data)}`">
                    {{ l.diasAtraso ? `${l.diasAtraso} ${l.diasAtraso === 1 ? 'dia' : 'dias'} de atraso` : 'em dia' }}
                  </span>
                </span>
              </span>
              <span class="valor num" :class="{ receita: l.tipo === 'RECEITA' }">
                {{ l.tipo === 'RECEITA' ? '+' : '' }}{{ reais(l.valor) }}
              </span>
            </component>
          </li>
        </ul>
      </section>
    </div>

    <PainelLateral :aberto="novo" titulo="Lançar" descricao="Uma receita ou despesa que já aconteceu."
                   @fechar="novo = false">
      <FormLancamento v-if="novo" @salvo="salvo" @cancelar="novo = false" />
    </PainelLateral>

    <PainelLateral :aberto="!!editar" titulo="Editar lançamento"
                   :descricao="editar?.parcela ? `Parcela ${editar.parcela.numero} de ${editar.parcela.total}: a mudança vale só para ela.` : undefined"
                   @fechar="editar = null">
      <template v-if="editar">
        <FormLancamento :key="editar.id" :lancamento="editar" @salvo="salvo" @cancelar="editar = null" />
        <div class="zona-exclusao">
          <button v-if="!confirmandoExclusao" type="button" class="botao botao-perigo" @click="confirmandoExclusao = true">
            Excluir lançamento
          </button>
          <div v-else class="confirmar">
            <p v-if="editar.recorrenciaId">É a confirmação de algo que se repete. Ao excluir, o mês volta a ser previsto; se ela confirma sozinha, o mês fica como "não houve".</p>
            <p v-else>Excluir "{{ editar.descricao }}"? Não dá para desfazer.</p>
            <div class="acoes">
              <button type="button" class="botao botao-perigo" :disabled="excluindo" @click="excluir(false)">
                {{ editar.parcela ? 'Só esta parcela' : 'Excluir' }}
              </button>
              <button v-if="editar.parcela" type="button" class="botao botao-perigo" :disabled="excluindo" @click="excluir(true)">
                A compra inteira ({{ editar.parcela.total }} parcelas)
              </button>
              <button type="button" class="botao" @click="confirmandoExclusao = false">Manter</button>
            </div>
          </div>
          <Mensagem v-if="erroExclusao" tipo="erro">{{ erroExclusao }}</Mensagem>
        </div>
      </template>
    </PainelLateral>
  </div>
</template>

<style scoped>
.filtros {
  display: grid;
  gap: var(--e-3);
  margin-bottom: var(--e-4);
}

.filtros-linha {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--e-3) var(--e-4);
}

.datas {
  display: flex;
  flex-wrap: wrap;
  gap: var(--e-2) var(--e-3);
}

.data-filtro {
  display: flex;
  align-items: center;
  gap: var(--e-2);
  font-size: var(--t-sm);
  font-weight: 600;
}

.data-filtro .entrada {
  width: auto;
}

.busca {
  position: relative;
  flex: 1 1 18rem;
}

.busca svg {
  position: absolute;
  top: 50%;
  left: var(--e-3);
  width: 1.1rem;
  height: 1.1rem;
  color: var(--texto-2);
  transform: translateY(-50%);
  pointer-events: none;
}

.busca .entrada {
  padding-left: 2.5rem;
}

.filtro-select {
  flex: 0 1 14rem;
}

.periodo {
  display: flex;
  align-items: center;
  gap: var(--e-2);
}

.periodo-nome {
  min-width: 10.5rem;
  text-align: center;
  font-weight: 700;
  font-size: var(--t-lg);
}

.totais {
  display: flex;
  flex-wrap: wrap;
  gap: var(--e-2) var(--e-6);
  margin-bottom: var(--e-5);
  font-size: var(--t-sm);
}

.totais strong {
  margin-left: var(--e-1);
  font-size: var(--t-md);
}

.linha-esqueleto {
  height: 3rem;
  margin: var(--e-3) var(--e-4);
}

.dias {
  overflow: hidden;
}

.dia + .dia {
  border-top: 1px solid var(--pedra-forte);
}

.dia-titulo {
  padding: var(--e-2) var(--e-4);
  background: var(--areia);
  border-bottom: 1px solid var(--linha);
  font-size: var(--t-xs);
  font-weight: 700;
  color: var(--texto-2);
}

.lista > li {
  padding: 0;
}

.item {
  display: flex;
  align-items: center;
  gap: var(--e-3);
  width: 100%;
  min-height: 3.75rem;
  padding: var(--e-3) var(--e-4);
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
}

.item.clicavel {
  cursor: pointer;
}

.item.clicavel:hover {
  background: var(--areia);
}

.item-texto {
  display: grid;
  flex: 1;
  min-width: 0;
}

.descricao {
  font-weight: 600;
  overflow-wrap: anywhere;
}

.meta {
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.selos {
  display: flex;
  flex-wrap: wrap;
  gap: var(--e-1);
  margin-top: var(--e-1);
}

.valor {
  font-weight: 700;
  white-space: nowrap;
}

.zona-exclusao {
  display: grid;
  gap: var(--e-3);
  margin-top: var(--e-8);
  padding-top: var(--e-5);
  border-top: 1px solid var(--linha);
}

.confirmar {
  display: grid;
  gap: var(--e-3);
  font-size: var(--t-sm);
}

@media (max-width: 640px) {
  .filtro-select {
    flex: 1 1 100%;
  }
}
</style>
