<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ChevronLeft, ChevronRight, Repeat, Plus, Users } from 'lucide-vue-next'
import { recorrencias, visao as apiVisao } from '../api/servicos'
import type { CategoriaVisao, Pendente, Tipo, VisaoAnual } from '../api/tipos'
import { mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import { anoAtual, mesAtual, reais, variacao } from '../util/formato'
import { useMidia } from '../util/midia'
import TabelaAno from '../componentes/ano/TabelaAno.vue'
import AnoCelular from '../componentes/ano/AnoCelular.vue'
import FluxoCaixa from '../componentes/ano/FluxoCaixa.vue'
import PorPessoa from '../componentes/ano/PorPessoa.vue'
import Mensagem from '../componentes/Mensagem.vue'
import PainelMes, { type AlvoMes } from '../componentes/ano/PainelMes.vue'

const route = useRoute()
const router = useRouter()
const celular = useMidia('(max-width: 760px)')

const ano = computed(() => Number(route.params.ano) || anoAtual())
const aba = computed(() => (['fluxo', 'pessoas'].includes(String(route.query.aba)) ? String(route.query.aba) : 'orcamento'))
const dados = ref<VisaoAnual | null>(null)
const carregando = ref(true)
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

// Clicar no valor de uma categoria num mês abre o painel do mês.
const alvo = ref<AlvoMes | null>(null)
function abrirMes(c: CategoriaVisao, tipo: Tipo, mes: number) {
  alvo.value = { categoria: c, tipo, ano: ano.value, mes }
}
const vazio = computed(() => !!dados.value && renda.value === 0 && despesas.value === 0)
const bemvindo = computed(() => route.query.bemvindo === '1')
const inflacao = computed(() => dados.value?.resumo.inflacaoFamiliar.ano ?? null)
const tempo = computed(() => (ano.value < anoAtual() ? 'passado' : ano.value > anoAtual() ? 'futuro' : 'atual'))
</script>

<template>
  <div class="pagina pagina-larga">
    <header class="cabecalho">
      <div class="titulo">
        <h1>O ano de vocês</h1>
        <div class="ano" role="group" aria-label="Ano">
          <button type="button" class="botao botao-sutil botao-icone botao-pequeno" :aria-label="`Ver ${ano - 1}`"
                  @click="irPara(ano - 1)">
            <ChevronLeft aria-hidden="true" />
          </button>
          <span class="ano-numero num" aria-live="polite">{{ ano }}</span>
          <button type="button" class="botao botao-sutil botao-icone botao-pequeno" :aria-label="`Ver ${ano + 1}`"
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
        <span v-if="inflacao !== null" class="linha-previsto">Inflação da família no ano: <strong class="num">{{ variacao(inflacao) }}</strong>.</span>
      </p>

      <Mensagem v-if="pendentes.length > 0 && !vazio" class="pendentes">
        Para conferir: {{ textoPendentes }}. É o que se repete e já venceu; confirmem o valor ou marquem que não houve.
        <template #acoes>
          <RouterLink to="/recorrencias" class="botao botao-pequeno">Conferir</RouterLink>
        </template>
      </Mensagem>

      <section v-if="vazio" class="comeco painel">
        <div>
          <h2>{{ bemvindo ? 'Tudo pronto. O ano de vocês começa aqui.' : `${ano} ainda está em branco.` }}</h2>
          <p>
            O jeito mais rápido de ver o ano inteiro é começar pelo que se repete: salário, aluguel, escola, plano de
            saúde. Cada um aparece como previsto em todos os meses até vocês confirmarem.
          </p>
        </div>
        <ul class="passos">
          <li>
            <Repeat aria-hidden="true" />
            <div>
              <strong>Cadastrar o que se repete</strong>
              <span>Salário, contas fixas, assinaturas.</span>
            </div>
            <RouterLink to="/recorrencias?nova=1" class="botao botao-primario botao-pequeno">Cadastrar</RouterLink>
          </li>
          <li>
            <Plus aria-hidden="true" />
            <div>
              <strong>Lançar um gasto do dia a dia</strong>
              <span>Mercado, farmácia, combustível.</span>
            </div>
            <RouterLink to="/lancamentos?novo=1" class="botao botao-pequeno">Lançar</RouterLink>
          </li>
          <li v-if="sessao.dono.value">
            <Users aria-hidden="true" />
            <div>
              <strong>Trazer a família</strong>
              <span>Cada um lança o seu e vê o mesmo quadro.</span>
            </div>
            <RouterLink to="/familia" class="botao botao-pequeno">Convidar</RouterLink>
          </li>
        </ul>
      </section>

      <div class="conteudo-aba">
        <template v-if="aba === 'orcamento'">
          <AnoCelular v-if="celular" :visao="dados" :mes-atual="mesDestaque" @abrir="abrirMes" />
          <TabelaAno v-else :visao="dados" :mes-atual="mesDestaque" @abrir="abrirMes" />
        </template>
        <FluxoCaixa v-else-if="aba === 'fluxo'" :visao="dados" :mes-atual="mesDestaque" @mudou="carregar" />
        <PorPessoa v-else :visao="dados" />
      </div>
    </template>

    <PainelMes :alvo="alvo" @fechar="alvo = null" />
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

.passos svg {
  width: 1.25rem;
  height: 1.25rem;
  flex: none;
  color: var(--petroleo);
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
}
</style>
