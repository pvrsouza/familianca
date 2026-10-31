<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, Upload } from 'lucide-vue-next'
import { importacoes, lugaresSaldo } from '../api/servicos'
import type {
  AnaliseExtrato, ImportacaoFeita, ImportacaoLote, LinhaExtrato, LinhaParaGravar, LugarSaldo, StatusLinhaImportacao,
} from '../api/tipos'
import { mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import { catalogo } from '../estado/catalogo'
import { avisar } from '../estado/avisos'
import { dataCurta, reais } from '../util/formato'
import EstadoVazio from '../componentes/EstadoVazio.vue'
import Mensagem from '../componentes/Mensagem.vue'

const TAMANHO_MAXIMO = 2 * 1024 * 1024

/** PULAR = não entra nesta importação (a linha reaparece se o extrato for lido de novo). */
type AcaoLinha = 'IMPORTAR' | 'IGNORAR' | 'CONFIRMAR_RECORRENCIA' | 'PULAR'
interface Escolha { acao: AcaoLinha; categoriaId: number | null; membroId: number | null }

const eu = computed(() => sessao.estado.usuario)
const membros = computed(() => (sessao.estado.familia?.membros ?? []).filter((m) => m.ativo))
/** MEMBRO só enxerga a si mesmo; quem organiza escolhe qualquer pessoa. */
const membrosPermitidos = computed(() => (sessao.dono.value ? membros.value : membros.value.filter((m) => m.id === eu.value?.membroId)))

// Passo 1
const locais = ref<LugarSaldo[]>([])
const localId = ref<number | null>(null)
const arquivo = ref<File | null>(null)
const analisando = ref(false)
const erroArquivo = ref('')
const carregando = ref(true)
const erroCarga = ref('')
const lotes = ref<ImportacaoLote[]>([])
const erroLotes = ref('')

// Passo 2
const analise = ref<AnaliseExtrato | null>(null)
const escolhas = reactive<Record<string, Escolha>>({})
const filtro = ref<'TODAS' | StatusLinhaImportacao>('TODAS')
const gravando = ref(false)
const erroGravar = ref('')
const resultado = ref<ImportacaoFeita | null>(null)

const localEscolhido = computed(() => locais.value.find((l) => l.id === localId.value) ?? null)

async function carregarLotes() {
  erroLotes.value = ''
  try {
    lotes.value = await importacoes.listar()
  } catch (e) {
    erroLotes.value = mensagemDeErro(e)
  }
}

async function iniciar() {
  carregando.value = true
  erroCarga.value = ''
  try {
    const [todos] = await Promise.all([lugaresSaldo.listar(), catalogo.carregar(), carregarLotes()])
    // MEMBRO só importa em lugar da família toda ou dele.
    locais.value = todos.filter((l) => l.ativo && (sessao.dono.value || l.membroId === null || l.membroId === eu.value?.membroId))
    if (locais.value.length === 1) localId.value = locais.value[0].id
  } catch (e) {
    erroCarga.value = mensagemDeErro(e)
  } finally {
    carregando.value = false
  }
}
onMounted(iniciar)

function escolherArquivo(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0] ?? null
  erroArquivo.value = ''
  arquivo.value = null
  if (!f) return
  if (!/\.(ofx|ofc)$/i.test(f.name)) erroArquivo.value = 'Esse arquivo não é .ofx nem .ofc. Baixe o extrato em formato OFX no site ou app do banco.'
  else if (f.size > TAMANHO_MAXIMO) erroArquivo.value = 'O arquivo passa de 2 MB. Baixe um período menor.'
  else if (f.size === 0) erroArquivo.value = 'O arquivo está vazio.'
  else arquivo.value = f
}

function categoriaValida(l: LinhaExtrato, id: number | null): number | null {
  return id !== null && catalogo.lancaveis(l.tipo).some((g) => g.categorias.some((c) => c.id === id)) ? id : null
}

function iniciarEscolha(l: LinhaExtrato): Escolha {
  let acao: AcaoLinha = l.acaoSugerida
  // Já importada e possível duplicata ficam desmarcadas até a pessoa decidir.
  if (l.status !== 'NOVA') acao = 'PULAR'
  if (acao === 'CONFIRMAR_RECORRENCIA' && !l.ocorrenciaPendente) acao = 'IMPORTAR'
  const membroPadrao = membrosPermitidos.value.some((m) => m.id === eu.value?.membroId) ? eu.value!.membroId : membrosPermitidos.value[0]?.id ?? null
  return { acao, categoriaId: categoriaValida(l, l.categoriaIdSugerida), membroId: membroPadrao }
}

async function analisar() {
  if (!arquivo.value || localId.value === null) return
  analisando.value = true
  erroArquivo.value = ''
  try {
    const r = await importacoes.analisar(arquivo.value, localId.value)
    Object.keys(escolhas).forEach((k) => delete escolhas[k])
    r.linhas.forEach((l) => { escolhas[l.chave] = iniciarEscolha(l) })
    analise.value = r
    filtro.value = 'TODAS'
    resultado.value = null
    erroGravar.value = ''
  } catch (e) {
    erroArquivo.value = mensagemDeErro(e)
  } finally {
    analisando.value = false
  }
}

function voltar() {
  analise.value = null
  resultado.value = null
  arquivo.value = null
  erroGravar.value = ''
}

const jaImportadas = computed(() => analise.value?.linhas.filter((l) => l.status === 'JA_IMPORTADA') ?? [])
const linhasAtivas = computed(() => (analise.value?.linhas ?? []).filter((l) => l.status !== 'JA_IMPORTADA'
  && (filtro.value === 'TODAS' || filtro.value === l.status)))
const mostraJa = computed(() => filtro.value === 'TODAS' || filtro.value === 'JA_IMPORTADA')
const aGravar = computed(() => (analise.value?.linhas ?? []).filter((l) => l.status !== 'JA_IMPORTADA' && escolhas[l.chave]?.acao !== 'PULAR'))

function rotuloResumo(a: AnaliseExtrato) {
  const r = a.resumo
  const partes = [`${r.total} linhas`, `${r.novas} novas`, `${r.jaImportadas} já importadas`,
    `${r.possiveisDuplicatas} possíveis duplicatas`]
  if (a.periodoDe && a.periodoAte) partes.push(`período ${dataCurta(a.periodoDe)} a ${dataCurta(a.periodoAte)}`)
  return partes.join(' · ')
}

function categoriasDe(l: LinhaExtrato) {
  return catalogo.lancaveis(l.tipo)
}

async function gravar() {
  if (!analise.value || localId.value === null || !aGravar.value.length) return
  gravando.value = true
  erroGravar.value = ''
  try {
    const linhas: LinhaParaGravar[] = aGravar.value.map((l) => {
      const e = escolhas[l.chave]
      const confirma = e.acao === 'CONFIRMAR_RECORRENCIA' && l.ocorrenciaPendente
      return {
        chave: l.chave, acao: e.acao as LinhaParaGravar['acao'], data: l.data, descricao: l.descricao, valor: l.valor, tipo: l.tipo,
        categoriaId: e.acao === 'IMPORTAR' ? e.categoriaId : null,
        membroId: e.acao === 'IMPORTAR' ? e.membroId : null,
        recorrenciaId: confirma ? l.ocorrenciaPendente!.recorrenciaId : null,
        competencia: confirma ? l.ocorrenciaPendente!.competencia : null,
      }
    })
    resultado.value = await importacoes.gravar({
      localSaldoId: localId.value, periodoDe: analise.value.periodoDe, periodoAte: analise.value.periodoAte, linhas,
    })
    analise.value = null
    arquivo.value = null
    sessao.avisarMudanca()
    carregarLotes()
  } catch (e) {
    erroGravar.value = mensagemDeErro(e)
  } finally {
    gravando.value = false
  }
}

// Desfazer
const desfazendo = ref<number | null>(null)
const confirmandoId = ref<number | null>(null)
const erroDesfazer = ref('')

async function desfazer(lote: ImportacaoLote) {
  desfazendo.value = lote.id
  erroDesfazer.value = ''
  try {
    const r = await importacoes.desfazer(lote.id)
    confirmandoId.value = null
    sessao.avisarMudanca()
    avisar(r.mantidosPorEdicao
      ? `Importação desfeita: ${r.removidos} removidos e ${r.mantidosPorEdicao} mantidos por terem sido editados.`
      : `Importação desfeita: ${r.removidos} lançamentos removidos.`)
    await carregarLotes()
  } catch (e) {
    erroDesfazer.value = mensagemDeErro(e)
  } finally {
    desfazendo.value = null
  }
}

const plural = (n: number, um: string, varios: string) => `${n} ${n === 1 ? um : varios}`
</script>

<template>
  <div class="pagina">
    <header class="pagina-cabecalho">
      <div>
        <h1>Importar extrato</h1>
        <p v-if="!analise && !resultado">Traga o extrato do banco e escolha o que vira lançamento.</p>
        <p v-else-if="analise">Passo 2 de 2: revise as linhas antes de importar. Nada é gravado até você confirmar.</p>
      </div>
      <RouterLink to="/lancamentos" class="botao botao-sutil"><ArrowLeft aria-hidden="true" /> Lançamentos</RouterLink>
    </header>

    <div v-if="carregando" class="painel" aria-busy="true"><div v-for="i in 3" :key="i" class="esqueleto linha-esqueleto" /></div>
    <Mensagem v-else-if="erroCarga" tipo="erro">
      {{ erroCarga }}
      <template #acoes><button type="button" class="botao botao-pequeno" @click="iniciar">Tentar de novo</button></template>
    </Mensagem>

    <!-- Resultado -->
    <section v-else-if="resultado" class="painel painel-corpo resultado" aria-labelledby="t-resultado">
      <h2 id="t-resultado">Importação concluída</h2>
      <ul class="numeros" role="list">
        <li><strong class="num">{{ resultado.importadas }}</strong> importadas</li>
        <li><strong class="num">{{ resultado.ignoradas }}</strong> ignoradas</li>
        <li><strong class="num">{{ resultado.confirmadas }}</strong> contas confirmadas</li>
        <li><strong class="num">{{ resultado.jaExistiam }}</strong> já existiam</li>
      </ul>
      <div class="acoes">
        <RouterLink to="/lancamentos" class="botao botao-primario">Ver lançamentos</RouterLink>
        <button type="button" class="botao" @click="resultado = null">Importar outro extrato</button>
      </div>
    </section>

    <!-- Passo 2: revisão -->
    <template v-else-if="analise">
      <Mensagem v-if="analise.avisoConta" tipo="info">{{ analise.avisoConta }}</Mensagem>
      <p class="resumo" aria-live="polite">{{ rotuloResumo(analise) }}</p>
      <p v-if="analise.banco || analise.contaOfx" class="texto-2 conta-ofx">
        Extrato de {{ analise.banco ?? 'banco não informado' }}<template v-if="analise.contaOfx">, conta {{ analise.contaOfx }}</template>
        · vai para {{ localEscolhido?.nome }}
      </p>

      <div class="segmentos" role="group" aria-label="Filtrar por situação">
        <button type="button" :aria-pressed="filtro === 'TODAS'" @click="filtro = 'TODAS'">Todas</button>
        <button type="button" :aria-pressed="filtro === 'NOVA'" @click="filtro = 'NOVA'">Novas</button>
        <button type="button" :aria-pressed="filtro === 'POSSIVEL_DUPLICATA'" @click="filtro = 'POSSIVEL_DUPLICATA'">Possíveis duplicatas</button>
        <button type="button" :aria-pressed="filtro === 'JA_IMPORTADA'" @click="filtro = 'JA_IMPORTADA'">Já importadas</button>
      </div>

      <EstadoVazio v-if="!linhasAtivas.length && !(mostraJa && jaImportadas.length)" titulo="Nenhuma linha nesse filtro">
        Troque o filtro para ver as outras linhas do extrato.
      </EstadoVazio>

      <ul v-if="linhasAtivas.length" class="linhas painel" role="list">
        <li v-for="l in linhasAtivas" :key="l.chave" class="linha" :class="{ pulada: escolhas[l.chave].acao === 'PULAR' }">
          <div class="l-data num">{{ dataCurta(l.data) }}</div>
          <div class="l-desc">
            <span class="descricao">{{ l.descricao }}</span>
            <span v-if="l.status === 'POSSIVEL_DUPLICATA' && l.lancamentoParecido" class="selo selo-atraso">possível duplicata</span>
            <p v-if="l.lancamentoParecido" class="parecido">
              Parecido com um lançamento seu: "{{ l.lancamentoParecido.descricao }}" em {{ dataCurta(l.lancamentoParecido.data) }},
              {{ reais(l.lancamentoParecido.valor) }}, {{ l.lancamentoParecido.categoriaNome }}.
              Se é o mesmo, deixe em "Não importar".
            </p>
          </div>
          <div class="l-valor num" :class="l.tipo === 'RECEITA' ? 'receita' : 'despesa'">
            {{ l.tipo === 'RECEITA' ? '+' : '−' }}{{ reais(l.valor) }}
          </div>

          <label class="campo">
            <span>Ação</span>
            <select v-model="escolhas[l.chave].acao" class="selecao">
              <option value="IMPORTAR">Importar</option>
              <option value="IGNORAR">Ignorar (não é receita nem despesa)</option>
              <option v-if="l.ocorrenciaPendente" value="CONFIRMAR_RECORRENCIA">
                Confirmar a conta {{ l.ocorrenciaPendente.descricao }}
              </option>
              <option value="PULAR">Não importar agora</option>
            </select>
            <small v-if="l.ocorrenciaPendente && escolhas[l.chave].acao === 'CONFIRMAR_RECORRENCIA'" class="texto-2">
              Vence em {{ dataCurta(l.ocorrenciaPendente.vencimento) }}, previsto {{ reais(l.ocorrenciaPendente.valor) }}.
              Vale o valor e a data do extrato.
            </small>
          </label>

          <template v-if="escolhas[l.chave].acao === 'IMPORTAR'">
            <label class="campo">
              <span>Categoria</span>
              <select v-model="escolhas[l.chave].categoriaId" class="selecao">
                <option :value="null">A classificar</option>
                <optgroup v-for="g in categoriasDe(l)" :key="g.id" :label="g.nome">
                  <option v-for="c in g.categorias" :key="c.id" :value="c.id">{{ c.nome }}</option>
                </optgroup>
              </select>
            </label>
            <label class="campo">
              <span>Membro</span>
              <select v-model="escolhas[l.chave].membroId" class="selecao" :disabled="membrosPermitidos.length < 2">
                <option v-for="m in membrosPermitidos" :key="m.id" :value="m.id">{{ m.nome }}</option>
              </select>
            </label>
          </template>
        </li>
      </ul>

      <details v-if="mostraJa && jaImportadas.length" class="ja painel" :open="filtro === 'JA_IMPORTADA'">
        <summary>{{ plural(jaImportadas.length, 'linha já importada', 'linhas já importadas') }} (não entram de novo)</summary>
        <ul role="list">
          <li v-for="l in jaImportadas" :key="l.chave">
            <span class="num">{{ dataCurta(l.data) }}</span>
            <span class="descricao">{{ l.descricao }}</span>
            <span class="num" :class="l.tipo === 'RECEITA' ? 'receita' : 'despesa'">{{ l.tipo === 'RECEITA' ? '+' : '−' }}{{ reais(l.valor) }}</span>
          </li>
        </ul>
      </details>

      <Mensagem v-if="erroGravar" tipo="erro">{{ erroGravar }}</Mensagem>
      <div class="rodape acoes">
        <button type="button" class="botao" :disabled="gravando" @click="voltar">Voltar</button>
        <button type="button" class="botao botao-primario" :disabled="gravando || !aGravar.length" @click="gravar">
          {{ gravando ? 'Importando…' : `Importar ${plural(aGravar.length, 'lançamento', 'lançamentos')}` }}
        </button>
      </div>
    </template>

    <!-- Passo 1 -->
    <template v-else>
      <section class="painel painel-corpo passo" aria-labelledby="t-passo1">
        <h2 id="t-passo1">Passo 1 de 2: de onde vem o extrato</h2>
        <EstadoVazio v-if="!locais.length" titulo="Cadastre onde está o dinheiro primeiro">
          O extrato precisa de uma conta cadastrada (conta corrente, poupança...) para saber de onde vieram as linhas.
          <template #acoes><RouterLink to="/ano" class="botao">Abrir o ano</RouterLink></template>
        </EstadoVazio>
        <form v-else class="form" novalidate @submit.prevent="analisar">
          <label class="campo">
            <span>Conta</span>
            <select v-model="localId" class="selecao" required>
              <option :value="null" disabled>Escolha a conta</option>
              <option v-for="l in locais" :key="l.id" :value="l.id">{{ l.nome }}</option>
            </select>
          </label>
          <label class="campo">
            <span>Arquivo do extrato</span>
            <input class="entrada" type="file" accept=".ofx,.ofc" required aria-describedby="ajuda-arquivo" @change="escolherArquivo" />
            <small id="ajuda-arquivo" class="texto-2">
              Aceitamos .ofx e .ofc. Transações já importadas são identificadas e ignoradas.
            </small>
          </label>
          <p class="texto-2 privacidade">O arquivo é lido e descartado: não fica guardado no Familiança.</p>
          <Mensagem v-if="erroArquivo" tipo="erro">{{ erroArquivo }}</Mensagem>
          <div class="acoes">
            <button type="submit" class="botao botao-primario" :disabled="analisando || !arquivo || localId === null">
              <Upload aria-hidden="true" /> {{ analisando ? 'Lendo o extrato…' : 'Ler extrato' }}
            </button>
          </div>
        </form>
      </section>

      <section class="secao" aria-labelledby="t-anteriores">
        <h2 id="t-anteriores">Importações anteriores</h2>
        <Mensagem v-if="erroLotes" tipo="erro">
          {{ erroLotes }}
          <template #acoes><button type="button" class="botao botao-pequeno" @click="carregarLotes">Tentar de novo</button></template>
        </Mensagem>
        <p v-else-if="!lotes.length" class="texto-2">Nenhuma importação ainda.</p>
        <ul v-else class="lotes painel" role="list">
          <li v-for="b in lotes" :key="b.id">
            <div>
              <strong>{{ b.localSaldoNome }}</strong>
              <span v-if="b.periodoDe && b.periodoAte" class="texto-2"> · {{ dataCurta(b.periodoDe) }} a {{ dataCurta(b.periodoAte) }}</span>
              <p class="texto-2 meta">
                {{ b.importadas }} importadas, {{ b.ignoradas }} ignoradas, {{ b.confirmadas }} confirmadas ·
                por {{ b.feitaPor }} em {{ dataCurta(b.feitaEm) }}
              </p>
            </div>
            <button v-if="b.podeDesfazer && confirmandoId !== b.id" type="button" class="botao botao-pequeno"
                    @click="confirmandoId = b.id; erroDesfazer = ''">Desfazer importação</button>
            <div v-else-if="confirmandoId === b.id" class="confirmar">
              <p>Remove os lançamentos criados por esta importação. Os que você editou depois ficam, e avisamos quantos.</p>
              <div class="acoes">
                <button type="button" class="botao botao-perigo botao-pequeno" :disabled="desfazendo === b.id" @click="desfazer(b)">
                  {{ desfazendo === b.id ? 'Desfazendo…' : 'Desfazer importação' }}
                </button>
                <button type="button" class="botao botao-pequeno" :disabled="desfazendo === b.id" @click="confirmandoId = null">Manter</button>
              </div>
              <Mensagem v-if="erroDesfazer" tipo="erro">{{ erroDesfazer }}</Mensagem>
            </div>
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>

<style scoped>
.linha-esqueleto { height: 3rem; margin: var(--e-3) var(--e-4); }
.passo { max-width: 40rem; }
.passo h2 { margin-bottom: var(--e-4); }
.form { display: grid; gap: var(--e-4); }
.campo { display: grid; gap: var(--e-1); font-size: var(--t-sm); font-weight: 600; min-width: 0; }
.campo small { font-weight: 400; }
.privacidade { font-size: var(--t-sm); }
.resumo { font-weight: 700; margin-bottom: var(--e-2); }
.conta-ofx { font-size: var(--t-sm); margin-bottom: var(--e-3); }
.segmentos { margin: var(--e-3) 0 var(--e-4); }
.despesa { color: var(--erro); }
.linhas > li, .ja li { border-bottom: 1px solid var(--linha); }
.linhas > li:last-child { border-bottom: 0; }
.linha { display: grid; grid-template-columns: 1fr auto; gap: var(--e-2) var(--e-3); padding: var(--e-3) var(--e-4); }
.linha.pulada { opacity: 0.75; }
.l-data { grid-column: 1; font-size: var(--t-xs); color: var(--texto-2); }
.l-desc { grid-column: 1; display: grid; gap: var(--e-1); min-width: 0; }
.l-valor { grid-column: 2; grid-row: 1 / span 2; align-self: center; font-weight: 700; white-space: nowrap; }
.linha .campo { grid-column: 1 / -1; }
.descricao { font-weight: 600; overflow-wrap: anywhere; }
.parecido { font-size: var(--t-xs); color: var(--texto-2); }
.ja { margin-top: var(--e-4); padding: var(--e-3) var(--e-4); }
.ja summary { cursor: pointer; font-weight: 600; }
.ja li { display: grid; grid-template-columns: auto 1fr auto; gap: var(--e-3); padding: var(--e-2) 0; font-size: var(--t-sm); color: var(--texto-2); }
.rodape { margin-top: var(--e-5); position: sticky; bottom: 0; padding: var(--e-3) 0; background: var(--fundo, var(--superficie)); }
.resultado h2 { margin-bottom: var(--e-3); }
.numeros { display: flex; flex-wrap: wrap; gap: var(--e-2) var(--e-6); margin-bottom: var(--e-4); list-style: none; padding: 0; }
.numeros strong { font-size: var(--t-lg); margin-right: var(--e-1); }
.secao { margin-top: var(--e-8); }
.lotes > li { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: var(--e-3); padding: var(--e-3) var(--e-4); border-bottom: 1px solid var(--linha); }
.lotes > li:last-child { border-bottom: 0; }
.meta { font-size: var(--t-xs); }
.confirmar { display: grid; gap: var(--e-2); font-size: var(--t-sm); max-width: 32rem; }

@media (min-width: 900px) {
  .linha { grid-template-columns: 5.5rem minmax(0, 1fr) 8rem 16rem 12rem 9rem; align-items: start; }
  .l-data, .l-desc, .l-valor, .linha .campo { grid-column: auto; grid-row: auto; }
  .l-valor { text-align: right; align-self: start; }
  .linha .campo span { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
}
</style>
