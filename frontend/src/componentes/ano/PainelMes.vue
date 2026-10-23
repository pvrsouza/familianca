<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowLeft, ChevronRight, Plus, Trash2, Undo2 } from 'lucide-vue-next'
import { lancamentos as apiLancamentos, recorrencias as apiRecorrencias } from '../../api/servicos'
import type { Lancamento, Recorrencia, Tipo } from '../../api/tipos'
import { ErroApi, mensagemDeErro } from '../../api/http'
import { sessao } from '../../estado/sessao'
import { avisar } from '../../estado/avisos'
import { ESTIMATIVAS, MESES, previstoPelaRegra, anoAtual, competencia, dataPorExtenso, hoje, mesAtual, reais, situacaoPagamento } from '../../util/formato'
import { catalogo } from '../../estado/catalogo'
import PainelLateral from '../PainelLateral.vue'
import FaixaEdicao from '../FaixaEdicao.vue'
import FormLancamento from '../FormLancamento.vue'
import ConferirOcorrencia from '../ConferirOcorrencia.vue'
import Mensagem from '../Mensagem.vue'

// O atalho da visão anual: clicar no valor de uma categoria num mês abre o que forma aquele valor. Com um
// lançamento só, já abre a edição; com o mês vazio, já abre o "Lançar" com a categoria e a data.
// embutido (na tabela do ano): abre numa faixa logo abaixo da linha clicada, mais enxuta, com os itens lado a lado;
// senão (celular), no painel lateral. Com item (a linha de um item da categoria aberta), mostra só ele naquele mês.
export interface ItemAlvo {
  descricao: string
  /** A recorrência que é o item; nulo = descrição avulsa ou estimativa. */
  recorrenciaId: number | null
  estimativa: boolean
}

export interface AlvoMes {
  categoria: { id: number; nome: string; ativa: boolean; calculada: boolean; estimado?: number[] }
  tipo: Tipo
  ano: number
  mes: number
  item?: ItemAlvo | null
}

const props = defineProps<{ alvo: AlvoMes | null; embutido?: boolean }>()
const emit = defineEmits<{ fechar: [] }>()

// UNICA: o fim é o próprio início, então o intervalo não importa (a faixa início–fim já limita a um mês).
const MESES_DA_FREQUENCIA = { UNICA: 1, MENSAL: 1, BIMESTRAL: 2, TRIMESTRAL: 3, SEMESTRAL: 6, ANUAL: 12 } as const

const itens = ref<Lancamento[]>([])
const previstas = ref<Recorrencia[]>([])
/** Recorrências da categoria puladas neste mês ("não houve"): dá para voltar a prever daqui mesmo. */
const puladas = ref<Recorrencia[]>([])
const carregando = ref(false)
const erro = ref('')
const modo = ref<'lista' | 'editar' | 'novo'>('lista')
const editando = ref<Lancamento | null>(null)
/** Abriu direto na edição ou no "Lançar": ao salvar, fecha em vez de voltar para a lista. */
const direto = ref(false)
const confirmandoExclusao = ref(false)
const excluindo = ref(false)
/** Item previsto (conta a pagar ou algo que se repete) com a exclusão aberta. */
const confirmandoRemoverPrevista = ref<number | null>(null)
const removendoPrevista = ref<number | null>(null)

const comp = computed(() => (props.alvo ? competencia(props.alvo.ano, props.alvo.mes) : ''))
const item = computed(() => props.alvo?.item ?? null)
const titulo = computed(() => item.value?.descricao ?? props.alvo?.categoria.nome ?? '')
const subtitulo = computed(() => {
  const a = props.alvo
  if (!a) return undefined
  const quando = `${MESES[a.mes - 1]} de ${a.ano}`
  return item.value ? `${a.categoria.nome} · ${quando}` : quando
})

const enxuto = computed(() => !!props.embutido)

/** "Feira" e " feira " são o mesmo item, como no servidor. */
function normalizar(t: string): string {
  return t.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim().replace(/\s+/g, ' ')
}

/** O lançamento é do item clicado? (a recorrência dele, ou a mesma descrição avulsa) */
function doItem(l: Lancamento): boolean {
  const i = item.value
  if (!i) return true
  if (i.estimativa) return false
  if (i.recorrenciaId != null) return l.recorrenciaId === i.recorrenciaId
  return !l.recorrenciaId && normalizar(l.descricao) === normalizar(i.descricao)
}

function recorrenciaDoItem(r: Recorrencia): boolean {
  const i = item.value
  return !i || (i.recorrenciaId != null && r.id === i.recorrenciaId)
}
const podeLancar = computed(() => !!props.alvo && props.alvo.categoria.ativa && !props.alvo.categoria.calculada)
const dataNova = computed(() => {
  const a = props.alvo
  if (!a) return hoje()
  return a.ano === anoAtual() && a.mes === mesAtual() ? hoje() : `${comp.value}-01`
})
const variasPessoas = computed(() => (sessao.estado.familia?.membros ?? []).filter((m) => m.ativo).length > 1)
const total = computed(() => itens.value.reduce((s, l) => s + l.valor, 0))
/** Estimativa do mês (despesa que não se repete, meses à frente): o que falta para chegar na média/máximo/mínimo. */
const estimadoNoMes = computed(() => (props.alvo ? props.alvo.categoria.estimado?.[props.alvo.mes - 1] ?? 0 : 0))
/** A estimativa aparece no mês da categoria inteira, ou quando o item clicado é ela. */
const mostrarEstimativa = computed(() => estimadoNoMes.value > 0.004 && (!item.value || item.value.estimativa))
const regraEstimativa = computed(() => {
  const c = catalogo.estado.grupos.flatMap((g) => g.categorias).find((x) => x.id === props.alvo?.categoria.id)
  return ESTIMATIVAS[c?.estimativa ?? sessao.estado.familia?.estimativa ?? 'MEDIA'].como
})

function meses(c: string): number {
  const [a, m] = c.split('-').map(Number)
  return a * 12 + m
}

/** A recorrência cai neste mês, não foi pulada nem confirmada? Então o valor está previsto aqui. */
function previstaNoMes(r: Recorrencia, lancados: Lancamento[]): boolean {
  const c = comp.value
  if (meses(c) < meses(r.inicio) || (r.fim && meses(c) > meses(r.fim))) return false
  if ((meses(c) - meses(r.inicio)) % MESES_DA_FREQUENCIA[r.frequencia] !== 0) return false
  if (r.puladas.includes(c)) return false
  return !lancados.some((l) => l.recorrenciaId === r.id && l.competenciaRecorrencia === c)
}

/** Recorrências da categoria (para saber se a confirmação de um lançamento foi automática). */
const daCategoria = ref<Recorrencia[]>([])

function confirmadaSozinha(l: Lancamento): boolean {
  const r = daCategoria.value.find((x) => x.id === l.recorrenciaId)
  return !!r?.confirmarSozinhaDesde && l.data >= r.confirmarSozinhaDesde
}

function rotuloConfirmacao(l: Lancamento): string {
  if (confirmadaSozinha(l)) return 'confirmada sozinha'
  return props.alvo?.tipo === 'RECEITA' ? 'recebimento confirmado' : 'conta confirmada'
}

function podeAlterar(l: Lancamento) {
  return sessao.dono.value || l.membro?.id === sessao.estado.usuario?.membroId
}

async function carregar(primeira: boolean) {
  const a = props.alvo
  if (!a) return
  carregando.value = true
  erro.value = ''
  catalogo.carregar().catch(() => {})
  try {
    const ultimoDia = new Date(a.ano, a.mes, 0).getDate()
    const [ls, rs] = await Promise.all([
      apiLancamentos.listar({ categoriaId: a.categoria.id, de: `${comp.value}-01`, ate: `${comp.value}-${ultimoDia}` }),
      apiRecorrencias.listar(),
    ])
    itens.value = ls.filter(doItem)
    daCategoria.value = rs.filter((r) => r.categoria.id === a.categoria.id)
    previstas.value = daCategoria.value.filter((r) => recorrenciaDoItem(r) && previstaNoMes(r, ls))
    puladas.value = daCategoria.value.filter((r) => recorrenciaDoItem(r) && r.puladas.includes(comp.value))
    const ls1 = itens.value
    if (primeira && !puladas.value.length && !mostrarEstimativa.value) {
      if (ls1.length === 1 && !previstas.value.length && podeAlterar(ls1[0])) abrirEdicao(ls1[0], true)
      else if (!ls1.length && !previstas.value.length && podeLancar.value && !item.value) abrirNovo(true)
    }
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    carregando.value = false
  }
}

watch(() => props.alvo, (a) => {
  modo.value = 'lista'
  editando.value = null
  direto.value = false
  confirmandoRemoverPrevista.value = null
  itens.value = []
  previstas.value = []
  puladas.value = []
  if (a) carregar(true)
}, { immediate: true })

function abrirEdicao(l: Lancamento, deCara = false) {
  editando.value = l
  confirmandoExclusao.value = false
  direto.value = deCara
  modo.value = 'editar'
}

function abrirNovo(deCara = false) {
  direto.value = deCara
  modo.value = 'novo'
}

function voltar() {
  modo.value = 'lista'
  editando.value = null
}

function terminou(mensagem: string) {
  avisar(mensagem)
  if (direto.value) {
    emit('fechar')
    return
  }
  voltar()
  carregar(false)
}

async function excluir(todas: boolean) {
  const l = editando.value
  if (!l) return
  excluindo.value = true
  erro.value = ''
  try {
    await apiLancamentos.remover(l.id, todas)
    sessao.avisarMudanca()
    terminou(!l.recorrenciaId ? 'Lançamento excluído.'
      : confirmadaSozinha(l) ? 'Confirmação desfeita: o mês ficou como "não houve".'
        : 'Confirmação desfeita: o mês volta a ser previsto.')
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    excluindo.value = false
  }
}

function conferido(mensagem: string) {
  avisar(mensagem)
  carregar(false)
}

/** Do mês atual em diante, a recorrência de valor variável é prevista pela regra (média, maior ou menor confirmação). */
function usaEstimativa(r: Recorrencia) {
  const a = props.alvo
  return !!a && !!r.previstoPor && (a.ano > anoAtual() || (a.ano === anoAtual() && a.mes >= mesAtual()))
}

function previstoDe(r: Recorrencia) {
  return usaEstimativa(r) ? r.valorPrevisto : r.valor
}

function podeAlterarRecorrencia(r: Recorrencia) {
  return sessao.dono.value || r.membro?.id === sessao.estado.usuario?.membroId
}

const voltando = ref<number | null>(null)

// ——— Excluir o que está previsto (conta a pagar ou algo que se repete) ———

async function removerPrevista(r: Recorrencia) {
  removendoPrevista.value = r.id
  erro.value = ''
  try {
    await apiRecorrencias.remover(r.id)
    sessao.avisarMudanca()
    confirmandoRemoverPrevista.value = null
    conferido(r.frequencia === 'UNICA' ? `${r.descricao} excluída.` : `${r.descricao} excluída: os meses previstos saíram do ano.`)
  } catch (e) {
    erro.value = e instanceof ErroApi && e.status === 409
      ? `${r.descricao} já tem meses confirmados, que viraram lançamentos, então não dá para excluir. Para tirar só `
        + `${MESES[props.alvo!.mes - 1]}, use "Pular o mês". Para parar daqui em diante, abra em Recorrências e marque `
        + '"Tem data para acabar".'
      : mensagemDeErro(e)
    confirmandoRemoverPrevista.value = null
  } finally {
    removendoPrevista.value = null
  }
}

async function voltarAPrever(r: Recorrencia) {
  voltando.value = r.id
  erro.value = ''
  try {
    await apiRecorrencias.desfazerPulo(r.id, comp.value)
    sessao.avisarMudanca()
    conferido(`${r.descricao}: ${MESES[props.alvo!.mes - 1]} volta a ser previsto.`)
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    voltando.value = null
  }
}
</script>

<template>
  <component :is="enxuto ? FaixaEdicao : PainelLateral" :aberto="!!alvo" :titulo="titulo" :descricao="subtitulo"
             @fechar="emit('fechar')">
    <template v-if="alvo">
      <button v-if="modo !== 'lista' && !direto" type="button" class="botao botao-sutil botao-pequeno voltar" @click="voltar">
        <ArrowLeft aria-hidden="true" /> Tudo de {{ MESES[alvo.mes - 1] }}
      </button>

      <template v-if="modo === 'editar' && editando">
        <p v-if="editando.parcela" class="texto-2 aviso-parcela">
          Parcela {{ editando.parcela.numero }} de {{ editando.parcela.total }}: a mudança vale só para ela.
        </p>
        <FormLancamento :key="editando.id" :lancamento="editando" @salvo="terminou" @cancelar="direto ? emit('fechar') : voltar()" />
        <div class="zona-exclusao">
          <button v-if="!confirmandoExclusao" type="button" class="botao botao-perigo" @click="confirmandoExclusao = true">
            {{ editando.recorrenciaId ? 'Desfazer a confirmação' : 'Excluir lançamento' }}
          </button>
          <div v-else class="confirmar">
            <p v-if="editando.recorrenciaId && confirmadaSozinha(editando)">
              Ela confirma sozinha. Ao desfazer, {{ MESES[alvo.mes - 1] }} fica como "não houve" (dá para voltar a prever em
              Recorrências).
            </p>
            <p v-else-if="editando.recorrenciaId">É a confirmação de algo que se repete. Ao desfazer, o mês volta a ser previsto.</p>
            <p v-else>Excluir "{{ editando.descricao }}"? Não dá para desfazer.</p>
            <div class="acoes">
              <button type="button" class="botao botao-perigo" :disabled="excluindo" @click="excluir(false)">
                {{ editando.parcela ? 'Só esta parcela' : editando.recorrenciaId ? 'Desfazer' : 'Excluir' }}
              </button>
              <button v-if="editando.parcela" type="button" class="botao botao-perigo" :disabled="excluindo" @click="excluir(true)">
                Todas as {{ editando.parcela.total }}
              </button>
              <button type="button" class="botao" @click="confirmandoExclusao = false">Manter</button>
            </div>
          </div>
        </div>
      </template>

      <FormLancamento v-else-if="modo === 'novo'" :tipo-inicial="alvo.tipo" :categoria-inicial="alvo.categoria.id"
                      :data-inicial="dataNova" @salvo="terminou" @cancelar="direto ? emit('fechar') : voltar()" />

      <template v-else>
        <div v-if="carregando && !itens.length && !previstas.length && !puladas.length" aria-busy="true">
          <div v-for="i in 3" :key="i" class="esqueleto linha-esqueleto" />
        </div>

        <template v-else>
          <section v-if="previstas.length" class="bloco">
            <h3 v-if="!item">{{ alvo.tipo === 'RECEITA' ? 'Previsto para receber' : 'Previsto' }}</h3>
            <ul class="lista" :class="{ 'so-item': item, 'em-linhas': enxuto && !item }">
              <li v-for="r in previstas" :key="r.id" class="prevista">
                <div v-if="!item || usaEstimativa(r)" class="nome-prevista">
                  <span v-if="!item" class="descricao">{{ r.descricao }}</span>
                  <p v-if="usaEstimativa(r)" class="texto-2 nota-pulo">
                    Cadastrada em {{ reais(r.valor) }}; prevista {{ previstoPelaRegra(r.previstoPor!, r.confirmacoesNaPrevisao) }}.
                  </p>
                </div>
                <ConferirOcorrencia v-if="confirmandoRemoverPrevista !== r.id" :recorrencia="r" :competencia="comp"
                                    :valor-previsto="previstoDe(r)" :compacto="enxuto" :em-linha="enxuto && !item"
                                    @feito="conferido">
                  <template v-if="enxuto && podeAlterarRecorrencia(r)" #mais>
                    <button type="button" class="botao botao-sutil botao-pequeno excluir-prevista"
                            @click="confirmandoRemoverPrevista = r.id; erro = ''">
                      <Trash2 aria-hidden="true" /> {{ r.frequencia === 'UNICA' ? 'Excluir esta conta' : 'Excluir (todos os meses)' }}
                    </button>
                  </template>
                </ConferirOcorrencia>
                <template v-if="podeAlterarRecorrencia(r)">
                  <div v-if="confirmandoRemoverPrevista === r.id" class="confirmar-prevista" role="group"
                       :aria-labelledby="`excluir-${r.id}`">
                    <p :id="`excluir-${r.id}`" class="pergunta-excluir">Excluir "{{ r.descricao }}"?</p>
                    <ul class="consequencias">
                      <template v-if="r.frequencia === 'UNICA'">
                        <li>A conta sai de {{ MESES[alvo.mes - 1] }}: {{ reais(previstoDe(r)) }} a menos nas despesas do ano.</li>
                        <li>Use quando ela foi registrada por engano. Se foi paga, confirme em vez de excluir.</li>
                      </template>
                      <template v-else>
                        <li>Some de todos os meses em que estava prevista, não só de {{ MESES[alvo.mes - 1] }}.</li>
                        <li>Para tirar só este mês, volte e use "{{ alvo.tipo === 'RECEITA' ? 'Não recebi' : 'Pular o mês' }}".</li>
                      </template>
                      <li>Não dá para desfazer.</li>
                    </ul>
                    <div class="acoes">
                      <button type="button" class="botao botao-perigo botao-pequeno" :disabled="removendoPrevista === r.id"
                              @click="removerPrevista(r)">
                        <Trash2 aria-hidden="true" /> Sim, excluir
                      </button>
                      <button type="button" class="botao botao-pequeno" @click="confirmandoRemoverPrevista = null">
                        <Undo2 aria-hidden="true" /> Voltar
                      </button>
                    </div>
                  </div>
                  <div v-else-if="!enxuto">
                    <button type="button" class="botao botao-sutil botao-pequeno excluir-prevista"
                            @click="confirmandoRemoverPrevista = r.id; erro = ''">
                      <Trash2 aria-hidden="true" /> {{ r.frequencia === 'UNICA' ? 'Excluir esta conta' : 'Excluir (todos os meses)' }}
                    </button>
                  </div>
                </template>
              </li>
            </ul>
          </section>

          <section v-if="mostrarEstimativa" class="bloco">
            <h3>Estimativa</h3>
            <div class="estimativa">
              <span class="selo selo-previsto num">{{ reais(estimadoNoMes) }}</span>
              <p class="texto-2">
                Estimado {{ regraEstimativa }}{{ itens.length ? ', descontando o que já foi lançado' : '' }}. Quando
                vocês lançarem o gasto real, ele ocupa o lugar da estimativa. A regra muda em
                <RouterLink to="/categorias" @click="emit('fechar')">Categorias</RouterLink>.
              </p>
            </div>
          </section>

          <section v-if="puladas.length" class="bloco">
            <h3>{{ alvo.tipo === 'RECEITA' ? 'Não recebido neste mês' : 'Pulado neste mês' }}</h3>
            <ul class="lista">
              <li v-for="r in puladas" :key="r.id" class="prevista">
                <span class="descricao">{{ r.descricao }}</span>
                <p class="texto-2 nota-pulo">
                  {{ reais(r.valor) }} ficou fora do ano porque {{ MESES[alvo.mes - 1] }} foi marcado como "não houve".
                </p>
                <div v-if="podeAlterarRecorrencia(r)">
                  <button type="button" class="botao botao-pequeno" :disabled="voltando === r.id" @click="voltarAPrever(r)">
                    <Undo2 aria-hidden="true" /> Voltar a prever
                  </button>
                </div>
              </li>
            </ul>
          </section>

          <section v-if="itens.length || (!item && !enxuto)" class="bloco">
            <h3>{{ alvo.tipo === 'RECEITA' ? 'Recebido' : 'Lançado' }}</h3>
            <p v-if="!itens.length" class="texto-2 vazio">Nada lançado em {{ MESES[alvo.mes - 1] }}.</p>
            <ul v-else class="lista">
              <li v-for="l in itens" :key="l.id">
                <component :is="podeAlterar(l) ? 'button' : 'div'" :type="podeAlterar(l) ? 'button' : undefined"
                           class="item" :class="{ clicavel: podeAlterar(l) }" @click="podeAlterar(l) && abrirEdicao(l)">
                  <span class="item-texto">
                    <span class="descricao">{{ l.descricao }}</span>
                    <span class="meta">
                      {{ dataPorExtenso(l.data) }}<template v-if="l.parcela"> · parcela {{ l.parcela.numero }}/{{ l.parcela.total }}</template><template v-if="l.recorrenciaId"> · {{ rotuloConfirmacao(l) }}</template><template v-if="situacaoPagamento(l.diasAtraso)"> · {{ situacaoPagamento(l.diasAtraso)!.toLowerCase() }}</template><template v-if="l.membro && variasPessoas"> · {{ l.membro.nome }}</template>
                    </span>
                  </span>
                  <span class="num valor" :class="{ receita: alvo.tipo === 'RECEITA' }">{{ reais(l.valor) }}</span>
                  <ChevronRight v-if="podeAlterar(l)" class="seta" aria-hidden="true" />
                </component>
              </li>
            </ul>
            <p v-if="itens.length > 1" class="total num">Total lançado: <strong>{{ reais(total) }}</strong></p>
          </section>

          <p v-if="item && !itens.length && !previstas.length && !puladas.length && !mostrarEstimativa" class="texto-2 vazio">
            Nada de {{ item.descricao }} em {{ MESES[alvo.mes - 1] }}.
          </p>

          <Mensagem v-if="erro" tipo="erro">{{ erro }}</Mensagem>

          <div class="acoes rodape" :class="{ enxuto: enxuto }">
            <button v-if="podeLancar && (!item || (!itens.length && !previstas.length))" type="button" class="botao botao-primario" @click="abrirNovo()">
              <Plus aria-hidden="true" /> Lançar em {{ MESES[alvo.mes - 1] }}
            </button>
            <RouterLink :to="`/lancamentos?categoriaId=${alvo.categoria.id}&ano=${alvo.ano}`" class="botao botao-sutil"
                        @click="emit('fechar')">
              Ver o ano todo da categoria
            </RouterLink>
          </div>
        </template>
      </template>
    </template>
  </component>
</template>

<style scoped>
.voltar {
  margin: calc(var(--e-2) * -1) 0 var(--e-4) calc(var(--e-3) * -1);
}

.aviso-parcela {
  margin-bottom: var(--e-4);
  font-size: var(--t-sm);
}

.bloco + .bloco {
  margin-top: var(--e-6);
}

.bloco h3 {
  margin-bottom: var(--e-2);
  font-size: var(--t-xs);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--texto-2);
}

.lista {
  border: 1px solid var(--linha);
  border-radius: var(--raio-g);
  background: var(--papel);
}

.lista > li:has(.item) {
  padding: 0;
}

.estimativa {
  display: grid;
  justify-items: start;
  gap: var(--e-2);
  padding: var(--e-3) var(--e-4);
  border: 1px solid var(--linha);
  border-radius: var(--raio-g);
  background: var(--papel);
}

.estimativa p {
  font-size: var(--t-sm);
  line-height: 1.5;
}

.nota-pulo {
  font-size: var(--t-sm);
}

.prevista {
  display: grid;
  gap: var(--e-2);
  padding: var(--e-3) var(--e-4);
}

/* Na faixa da tabela, um item por linha (como a planilha): nome à esquerda, valor, data e ações na mesma linha.
   Com 11 itens, são 11 linhas baixas, não 11 caixas. */
.lista.em-linhas > li.prevista {
  grid-template-columns: minmax(9rem, 14rem) minmax(0, 1fr);
  align-items: center;
  column-gap: var(--e-4);
  padding: var(--e-2) var(--e-4);
  min-height: 0;
}

.em-linhas .nome-prevista .descricao {
  font-weight: 600;
}

.em-linhas .confirmar-prevista {
  grid-column: 2;
}

/* Só um item (faixa aberta pela linha do item): sem a caixa em volta. */
.lista.so-item {
  border: 0;
  background: none;
}

.so-item .prevista {
  padding: 0;
}

.item {
  display: flex;
  align-items: center;
  gap: var(--e-3);
  width: 100%;
  min-height: 3.5rem;
  padding: var(--e-2) var(--e-4);
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

.lista > li:first-child .item {
  border-radius: var(--raio-g) var(--raio-g) 0 0;
}

.lista > li:last-child .item {
  border-radius: 0 0 var(--raio-g) var(--raio-g);
}

.lista > li:only-child .item {
  border-radius: var(--raio-g);
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

.valor {
  font-weight: 700;
  white-space: nowrap;
}

.seta {
  width: 1rem;
  height: 1rem;
  flex: none;
  color: var(--texto-2);
}

.vazio {
  font-size: var(--t-sm);
}

.total {
  margin-top: var(--e-2);
  text-align: right;
  font-size: var(--t-sm);
  color: var(--texto-2);
}

.total strong {
  color: var(--texto);
}

.linha-esqueleto {
  height: 3rem;
  margin-bottom: var(--e-2);
}

.rodape {
  flex-wrap: wrap;
  margin-top: var(--e-6);
}

/* No cartão: rodapé e blocos mais juntos, botões pequenos. */
.rodape.enxuto {
  margin-top: var(--e-3);
}

.rodape.enxuto .botao {
  min-height: 2.25rem;
  padding: 0 var(--e-3);
  font-size: var(--t-sm);
}

.confirmar-prevista {
  display: grid;
  gap: var(--e-2);
  padding: var(--e-3) var(--e-4);
  border-radius: var(--raio);
  background: var(--areia);
  border: 1px solid var(--linha);
}

.pergunta-excluir {
  font-size: var(--t-sm);
  font-weight: 600;
}

.consequencias {
  display: grid;
  gap: var(--e-1);
  margin: 0;
  padding-left: var(--e-5);
  font-size: var(--t-sm);
  line-height: 1.45;
  color: var(--texto-2);
}

.excluir-prevista {
  color: var(--coral-borda);
}

.zona-exclusao {
  margin-top: var(--e-8);
  padding-top: var(--e-5);
  border-top: 1px solid var(--linha);
}

.confirmar {
  display: grid;
  gap: var(--e-3);
}
</style>
