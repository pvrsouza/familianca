<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Check, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { recorrencias, visao as apiVisao } from '../api/servicos'
import type { CategoriaVisao, Pendente, Tipo, VisaoAnual } from '../api/tipos'
import { mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import { guia } from '../estado/guia'
import { MESES, alcanceInflacao, anoAtual, mesAtual, reais, ultimoAnoPrevisto, variacao } from '../util/formato'
import { useMidia } from '../util/midia'
import TabelaAno from '../componentes/ano/TabelaAno.vue'
import AnoCelular from '../componentes/ano/AnoCelular.vue'
import FluxoCaixa from '../componentes/ano/FluxoCaixa.vue'
import PorPessoa from '../componentes/ano/PorPessoa.vue'
import Mensagem from '../componentes/Mensagem.vue'
import PainelMes, { type AlvoMes } from '../componentes/ano/PainelMes.vue'
import PainelSaldo from '../componentes/ano/PainelSaldo.vue'

const route = useRoute()
const router = useRouter()
const celular = useMidia('(max-width: 760px)')

const ano = computed(() => Math.max(Math.min(Number(route.params.ano) || anoAtual(), ultimoAnoPrevisto()), sessao.primeiroAno.value))
const aba = computed(() => (['fluxo', 'pessoas'].includes(String(route.query.aba)) ? String(route.query.aba) : 'orcamento'))
const dados = ref<VisaoAnual | null>(null)
const carregando = ref(true)
/** Filtro rápido da tabela pelo nome: continua ao trocar de ano, sai ao deixar a tela. */
const busca = ref('')
const erro = ref('')
const pendentes = ref<Pendente[]>([])

const mesDestaque = computed(() => (ano.value === anoAtual() ? mesAtual() : null))

async function carregar() {
  carregando.value = !dados.value || dados.value.ano !== ano.value
  erro.value = ''
  try {
    const [v, p] = await Promise.all([
      apiVisao.ano(ano.value),
      recorrencias.pendentes().catch(() => []),
    ])
    dados.value = v
    pendentes.value = p
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    carregando.value = false
  }
}

watch([ano, () => sessao.estado.versaoDados], carregar, { immediate: true })

function irPara(a: number) {
  router.push({ path: a === anoAtual() ? '/ano' : `/ano/${a}`, query: route.query })
}

function trocarAba(a: string) {
  router.replace({ query: { ...route.query, aba: a === 'orcamento' ? undefined : a } })
}

const renda = computed(() => dados.value?.receitas.total.ano ?? 0)
const despesas = computed(() => dados.value?.despesas.total.ano ?? 0)
const sobra = computed(() => renda.value - despesas.value)
const somaPrevisto = (gs: VisaoAnual['despesas']['grupos']) => gs.reduce((s, g) => s + g.previsto.reduce((a, b) => a + b, 0), 0)
const previsto = computed(() => (dados.value ? somaPrevisto(dados.value.despesas.grupos) : 0))
const rendaPrevista = computed(() => (dados.value ? somaPrevisto(dados.value.receitas.grupos) : 0))
const receitasPendentes = computed(() => pendentes.value.filter((p) => p.recorrencia.tipo === 'RECEITA').length)
const contasPendentes = computed(() => pendentes.value.length - receitasPendentes.value)
const textoPendentes = computed(() => {
  const partes: string[] = []
  if (receitasPendentes.value) {
    partes.push(receitasPendentes.value === 1 ? '1 receita para confirmar se chegou' : `${receitasPendentes.value} receitas para confirmar se chegaram`)
  }
  if (contasPendentes.value) {
    partes.push(contasPendentes.value === 1 ? '1 conta para conferir' : `${contasPendentes.value} contas para conferir`)
  }
  return partes.join(' e ')
})

// Clicar no valor de uma categoria num mês abre o painel do mês (no celular; na tabela, a faixa abre na própria linha).
const alvo = ref<AlvoMes | null>(null)
function abrirMes(c: CategoriaVisao, tipo: Tipo, mes: number) {
  alvo.value = { categoria: c, tipo, ano: ano.value, mes }
}
const vazio = computed(() => !!dados.value && renda.value === 0 && despesas.value === 0)
const bemvindo = computed(() => route.query.bemvindo === '1')
const inflacao = computed(() => dados.value?.resumo.inflacaoFamiliar.ano ?? null)
const tempo = computed(() => (ano.value < anoAtual() ? 'passado' : ano.value > anoAtual() ? 'futuro' : 'atual'))

// ——— Como começar ———
// Guia de quem está chegando (ou recomeçou do zero), no ano corrente. O saldo inicial é obrigatório (pode ser zero ou
// negativo) porque é o ponto de partida do fluxo; o painel dele abre aqui mesmo. Os outros passos podem ser pulados
// (guardado no navegador), mas só depois do saldo: antes dele não há o que pular nem desfazer. O guia some quando nada
// mais está pendente.
// Saldo inicial em qualquer ano conta (é o que libera os lançamentos no servidor).
const temSaldo = computed(() => !!sessao.estado.familia?.temSaldoInicial || !!dados.value?.resumo.sobraInformada.some(Boolean))
const realizadas = computed(() =>
  dados.value ? dados.value.despesas.grupos.reduce((s, g) => s + g.realizado.reduce((a, b) => a + b, 0), 0) : 0,
)
const temFamilia = computed(() => {
  const f = sessao.estado.familia
  return !!f && (f.membros.filter((m) => m.ativo).length > 1 || f.convitesPendentes.length > 0)
})
const saldoAberto = ref<number | null>(null)
const familiaId = computed(() => sessao.estado.familia?.id ?? 0)

interface Passo {
  id: string
  titulo: string
  texto: string
  feito: boolean
  /** O saldo inicial não se pula. */
  obrigatorio: boolean
  /** Só depois do saldo inicial (lançar e cadastrar o que se repete). */
  depoisDoSaldo?: boolean
  pulado: boolean
  acao?: { rotulo: string; para?: string; abrir?: () => void }
}
const passos = computed<Passo[]>(() => {
  const mes = mesAtual()
  const dono = sessao.dono.value
  const pulado = (id: string) => temSaldo.value && guia.pulado(familiaId.value, id)
  const lista: Passo[] = [
    {
      id: 'saldo',
      titulo: 'Informar quanto vocês têm hoje',
      texto: temSaldo.value
        ? 'Feito. Para conferir ou corrigir, abram a aba Fluxo de caixa: o saldo fica na primeira linha.'
        : dono
          ? `O saldo do mês em que vocês começam, ${MESES[mes - 1]} ou um que já passou: contas, poupança, dinheiro na mão, cofrinho. Mesmo que seja zero ou negativo. É o ponto de partida: depois cada mês começa sozinho com a sobra do anterior.`
          : 'O saldo inicial é informado por quem organiza a família. Ele é o ponto de partida do fluxo de caixa.',
      feito: temSaldo.value,
      obrigatorio: true,
      pulado: false,
      acao: temSaldo.value
        ? { rotulo: 'Conferir', abrir: () => trocarAba('fluxo') }
        : dono ? { rotulo: 'Informar saldo', abrir: () => (saldoAberto.value = mes - 1) } : undefined,
    },
    {
      id: 'recorrencias',
      titulo: 'Cadastrar o que se repete',
      texto: 'Salário, aluguel, escola, plano de saúde. Aparecem como previstos em todos os meses seguintes.',
      feito: !vazio.value,
      obrigatorio: false,
      pulado: pulado('recorrencias'),
      depoisDoSaldo: !temSaldo.value,
      acao: { rotulo: 'Cadastrar', para: '/recorrencias?nova=1' },
    },
    {
      id: 'lancamento',
      titulo: 'Lançar um gasto do dia a dia',
      texto: 'Mercado, farmácia, combustível. Inclusive o que já pagaram este mês.',
      feito: realizadas.value > 0.004,
      obrigatorio: false,
      pulado: pulado('lancamento'),
      depoisDoSaldo: !temSaldo.value,
      acao: { rotulo: 'Lançar', para: '/lancamentos?novo=1' },
    },
  ]
  if (dono) {
    lista.push({
      id: 'familia',
      titulo: 'Trazer a família',
      texto: 'Cada um lança o seu e todos veem o mesmo quadro.',
      feito: temFamilia.value,
      obrigatorio: false,
      pulado: pulado('familia'),
      acao: { rotulo: 'Convidar', para: '/familia' },
    })
  }
  return lista
})
const pendente = (p: Passo) => !p.feito && !p.pulado
const mostrarGuia = computed(() => tempo.value === 'atual' && !!dados.value && passos.value.some(pendente))
function pular(p: Passo, sim: boolean) {
  guia.pular(familiaId.value, p.id, sim)
}
// Vindo de um formulário de lançamento sem saldo inicial (?saldo=1): abre o painel do saldo do mês atual. Com
// ?saldo=AAAA-MM (o mês do saldo inicial escolhido no painel, de outro ano), abre nesse mês quando a visão chega.
watch([() => route.query.saldo, dados], () => {
  const pedido = String(route.query.saldo ?? '')
  if (!pedido || !dados.value) return
  const doMes = /^(\d{4})-(\d{2})$/.exec(pedido)
  if (doMes && Number(doMes[1]) !== dados.value.ano) return // a visão do ano pedido ainda está carregando
  if (sessao.dono.value) {
    if (doMes) saldoAberto.value = Number(doMes[2]) - 1
    else if (pedido === '1' && tempo.value === 'atual') saldoAberto.value = mesAtual() - 1
  }
  router.replace({ query: { ...route.query, saldo: undefined } })
}, { immediate: true })

const proximo = computed(() => passos.value.findIndex(pendente))
</script>

<template>
  <div class="pagina pagina-larga">
    <header class="cabecalho">
      <div class="titulo">
        <h1>O ano de vocês</h1>
        <div class="ano" role="group" aria-label="Ano">
          <button type="button" class="botao botao-sutil botao-icone botao-pequeno" :aria-label="`Ver ${ano - 1}`"
                  :disabled="ano <= sessao.primeiroAno.value"
                  :title="ano <= sessao.primeiroAno.value ? `Não há dados antes de ${sessao.primeiroAno.value}` : undefined"
                  @click="irPara(ano - 1)">
            <ChevronLeft aria-hidden="true" />
          </button>
          <span class="ano-numero num" aria-live="polite">{{ ano }}</span>
          <button type="button" class="botao botao-sutil botao-icone botao-pequeno" :aria-label="`Ver ${ano + 1}`"
                  :disabled="ano >= ultimoAnoPrevisto()"
                  :title="ano >= ultimoAnoPrevisto() ? `A previsão vai até ${ultimoAnoPrevisto()}` : undefined"
                  @click="irPara(ano + 1)">
            <ChevronRight aria-hidden="true" />
          </button>
          <button v-if="ano !== anoAtual()" type="button" class="botao botao-sutil botao-pequeno" @click="irPara(anoAtual())">
            Voltar para {{ anoAtual() }}
          </button>
        </div>
      </div>
      <div class="segmentos abas" role="tablist" aria-label="Como ver o ano">
        <button type="button" role="tab" :aria-selected="aba === 'orcamento'" @click="trocarAba('orcamento')">Orçamento</button>
        <button type="button" role="tab" :aria-selected="aba === 'fluxo'" @click="trocarAba('fluxo')">Fluxo de caixa</button>
        <button type="button" role="tab" :aria-selected="aba === 'pessoas'" @click="trocarAba('pessoas')">Por pessoa</button>
      </div>
    </header>

    <div v-if="carregando" class="carregando" aria-busy="true" aria-label="Carregando o ano">
      <div class="esqueleto" style="height: 1.5rem; width: min(40rem, 90%)" />
      <div class="esqueleto" style="height: 26rem; margin-top: 1.5rem" />
    </div>

    <Mensagem v-else-if="erro" tipo="erro">
      Não conseguimos carregar o ano. {{ erro }}
      <template #acoes><button type="button" class="botao botao-pequeno" @click="carregar">Tentar de novo</button></template>
    </Mensagem>

    <template v-else-if="dados">
      <p v-if="!vazio" class="resumo">
        Em {{ ano }}, a renda da família {{ tempo === 'passado' ? 'somou' : 'soma' }}
        <strong class="num receita">{{ reais(renda) }}</strong> e as despesas
        <strong class="num">{{ reais(despesas) }}</strong>.
        <template v-if="sobra >= 0"> Sobram <strong class="num">{{ reais(sobra) }}</strong> no ano.</template>
        <template v-else> As despesas passam da renda em <strong class="num negativo">{{ reais(-sobra) }}</strong>; a tabela mostra onde estão os maiores pesos.</template>
        <span v-if="rendaPrevista > 0.004" class="linha-previsto">
          <span class="selo selo-previsto num">{{ reais(rendaPrevista) }}</span> da renda ainda {{ tempo === 'passado' ? 'ficou' : 'está' }} prevista: só conta como recebida depois de confirmada.
        </span>
        <span v-if="previsto > 0.004" class="linha-previsto">
          <span class="selo selo-previsto num">{{ reais(previsto) }}</span> das despesas ainda estão previstos.
        </span>
        <span v-if="inflacao !== null" class="linha-previsto">Inflação da família {{ alcanceInflacao(dados!.ano) }}: <strong class="num">{{ variacao(inflacao) }}</strong>.</span>
      </p>

      <Mensagem v-if="pendentes.length > 0 && !vazio" class="pendentes">
        Para conferir: {{ textoPendentes }}. É o que se repete e já venceu; confirmem o valor ou marquem que não houve.
        <template #acoes>
          <RouterLink to="/recorrencias" class="botao botao-pequeno">Conferir</RouterLink>
        </template>
      </Mensagem>

      <section v-if="mostrarGuia" class="comeco painel" aria-labelledby="comeco-titulo">
        <div>
          <h2 id="comeco-titulo">{{ bemvindo ? 'Tudo pronto. O ano de vocês começa aqui.' : 'Como começar' }}</h2>
          <p>
            Comecem pelo saldo: quanto a família tem hoje, somando tudo. Ele é obrigatório e vem antes de qualquer
            lançamento; os outros passos podem ser feitos depois dele ou pulados. Este guia some quando não houver
            mais nada pendente.
          </p>
        </div>
        <ol class="passos">
          <li v-for="(p, i) in passos" :key="p.id"
              :class="{ feito: p.feito, pulado: p.pulado && !p.feito, proximo: i === proximo }">
            <span class="numero" aria-hidden="true">
              <Check v-if="p.feito" />
              <template v-else>{{ i + 1 }}</template>
            </span>
            <div>
              <strong>
                {{ p.titulo }}
                <span v-if="p.obrigatorio && !p.feito" class="selo selo-petroleo marca">obrigatório</span>
                <span v-else-if="p.pulado && !p.feito" class="selo marca">pulado</span>
                <span v-if="p.feito" class="sr"> (feito)</span>
              </strong>
              <span>{{ p.texto }}</span>
            </div>
            <div v-if="p.acao || (temSaldo && !p.obrigatorio && !p.feito)" class="acoes-passo">
              <span v-if="p.depoisDoSaldo && !p.feito" class="depois">Depois do saldo</span>
              <template v-else-if="p.acao">
                <RouterLink v-if="p.acao.para && !p.feito" :to="p.acao.para" class="botao botao-pequeno"
                            :class="{ 'botao-primario': i === proximo }">{{ p.acao.rotulo }}</RouterLink>
                <button v-else-if="p.acao.abrir" type="button" class="botao botao-pequeno"
                        :class="{ 'botao-primario': i === proximo, 'botao-sutil': p.feito }"
                        @click="p.acao.abrir()">{{ p.acao.rotulo }}</button>
              </template>
              <button v-if="temSaldo && !p.obrigatorio && !p.feito" type="button" class="botao botao-sutil botao-pequeno"
                      :aria-label="p.pulado ? `Voltar a mostrar: ${p.titulo}` : `Pular: ${p.titulo}`"
                      @click="pular(p, !p.pulado)">{{ p.pulado ? 'Desfazer' : 'Pular' }}</button>
            </div>
          </li>
        </ol>
      </section>

      <div class="conteudo-aba">
        <template v-if="aba === 'orcamento'">
          <AnoCelular v-if="celular" :visao="dados" :mes-atual="mesDestaque" @abrir="abrirMes" />
          <TabelaAno v-else v-model:busca="busca" :visao="dados" :mes-atual="mesDestaque" />
        </template>
        <FluxoCaixa v-else-if="aba === 'fluxo'" :visao="dados" :mes-atual="mesDestaque" @mudou="carregar" />
        <PorPessoa v-else :visao="dados" />
      </div>
    </template>

    <PainelMes :alvo="alvo" @fechar="alvo = null" />
    <PainelSaldo v-if="dados" :visao="dados" :mes="saldoAberto" :informados="[]" fora-do-fluxo
                 @fechar="saldoAberto = null" @mudou="carregar"
                 @trocar-mes="saldoAberto = $event" />
  </div>
</template>

<style scoped>
.pagina-larga {
  max-width: 100rem;
}

.cabecalho {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--e-4);
  margin-bottom: var(--e-4);
}

.titulo {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--e-2) var(--e-5);
}

.ano {
  display: flex;
  align-items: center;
  gap: var(--e-1);
}

.ano-numero {
  min-width: 3.5rem;
  text-align: center;
  font-size: var(--t-xl);
  font-weight: 700;
  color: var(--petroleo-escuro);
}

.resumo {
  max-width: 78ch;
  margin-bottom: var(--e-5);
  font-size: var(--t-lg);
  line-height: 1.6;
  text-wrap: pretty;
}

.resumo strong {
  font-weight: 700;
}

.negativo {
  color: var(--erro);
}

.linha-previsto {
  display: block;
  margin-top: var(--e-1);
  font-size: var(--t-sm);
  color: var(--texto-2);
}

.linha-previsto strong {
  color: var(--texto);
}

.pendentes {
  margin-bottom: var(--e-5);
}

.comeco {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.3fr);
  gap: var(--e-8);
  padding: var(--e-6);
  margin-bottom: var(--e-6);
}

.comeco h2 {
  font-family: var(--fonte-titulo);
  font-weight: 500;
  font-size: var(--t-xl);
  margin-bottom: var(--e-2);
}

.comeco p {
  color: var(--texto-2);
}

.passos {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--e-2);
}

.passos li {
  display: flex;
  align-items: center;
  gap: var(--e-3);
  padding: var(--e-3) var(--e-4);
  border-radius: var(--raio);
  background: var(--areia);
}

.numero {
  display: grid;
  place-items: center;
  flex: none;
  width: 1.75rem;
  height: 1.75rem;
  border-radius: 50%;
  background: var(--papel);
  border: 1px solid var(--pedra-forte);
  font-family: var(--fonte-titulo);
  font-weight: 600;
  color: var(--petroleo-escuro);
}

.proximo .numero {
  background: var(--petroleo);
  border-color: var(--petroleo);
  color: var(--sobre-escuro);
}

.feito .numero {
  background: var(--petroleo-tinta);
  border-color: transparent;
}

.numero svg {
  width: 1rem;
  height: 1rem;
}

.feito strong {
  color: var(--texto-2);
  text-decoration: line-through;
  text-decoration-color: var(--pedra-forte);
}

.passos .acoes-passo {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--e-1);
}

.depois {
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.marca {
  margin-left: var(--e-1);
  vertical-align: 0.1em;
  font-size: var(--t-xs);
}

.pulado strong,
.pulado > div > span {
  color: var(--texto-2);
}

.passos li.proximo {
  background: var(--papel);
  box-shadow: inset 0 0 0 1px var(--petroleo);
}

.passos div {
  display: grid;
  flex: 1;
  min-width: 0;
}

.passos span {
  font-size: var(--t-sm);
  color: var(--texto-2);
}

@media (max-width: 900px) {
  .comeco {
    grid-template-columns: 1fr;
    gap: var(--e-4);
    padding: var(--e-5);
  }
}

@media (max-width: 760px) {
  .resumo {
    font-size: var(--t-md);
  }

  .abas {
    width: 100%;
    display: grid;
    grid-template-columns: repeat(3, 1fr);
  }

  .abas button {
    justify-content: center;
    padding: 0 var(--e-2);
  }

  .passos li {
    flex-wrap: wrap;
  }

  .passos li > div:not(.acoes-passo) {
    flex-basis: calc(100% - 1.75rem - var(--e-3));
  }

  /* No celular as ações descem para uma linha própria, alinhadas com o texto. */
  .passos .acoes-passo {
    width: 100%;
    justify-content: flex-start;
    padding-left: calc(1.75rem + var(--e-3));
  }
}
</style>
