<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { CircleCheck, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { lancamentos as apiLancamentos, recorrencias as api } from '../api/servicos'
import type { ContaDoMes, ContasDoMes, Recorrencia } from '../api/tipos'
import { mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import { avisar } from '../estado/avisos'
import { competenciaPorExtenso, dataPorExtenso, diasEntre, hoje, reais, ultimoAnoPrevisto } from '../util/formato'
import ConferirOcorrencia from '../componentes/ConferirOcorrencia.vue'
import EstadoVazio from '../componentes/EstadoVazio.vue'
import FormRecorrencia from '../componentes/FormRecorrencia.vue'
import Mensagem from '../componentes/Mensagem.vue'
import PainelLateral from '../componentes/PainelLateral.vue'

// Checklist do mês, como a planilha: cada conta que alguém precisa pagar, até tudo ficar pago. Pagar é confirmar a
// ocorrência (o mesmo "conferir" da visão anual). Débito automático e cartão saem sozinhos: só para conferir.
const route = useRoute()
const router = useRouter()

const mesHoje = hoje().slice(0, 7)
const ultimoMes = `${ultimoAnoPrevisto()}-12`
const mes = computed(() => {
  const q = route.query.mes
  return typeof q === 'string' && /^\d{4}-(0[1-9]|1[0-2])$/.test(q) && q <= ultimoMes ? q : mesHoje
})
const nomeMes = computed(() => competenciaPorExtenso(mes.value))

const dados = ref<ContasDoMes | null>(null)
const carregando = ref(true)
const erro = ref('')

async function carregar() {
  carregando.value = true
  erro.value = ''
  try {
    dados.value = await api.contasAPagar(mes.value)
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    carregando.value = false
  }
}

watch(() => [mes.value, sessao.estado.versaoDados], carregar, { immediate: true })

function irPara(competencia: string) {
  router.replace({ query: competencia === mesHoje ? {} : { mes: competencia } })
}

function mudarMes(delta: number) {
  const [a, m] = mes.value.split('-').map(Number)
  const d = new Date(a, m - 1 + delta, 1)
  irPara(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
}

// ——— Grupos ———
const aPagar = computed(() => dados.value?.aPagar ?? [])
const pagas = computed(() => aPagar.value.filter((c) => c.pagamento))
const abertas = computed(() => aPagar.value.filter((c) => !c.pagamento))
const em7Dias = (() => {
  const d = new Date()
  d.setDate(d.getDate() + 7)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})()
const grupos = computed(() => [
  { titulo: 'Vencidas', contas: abertas.value.filter((c) => c.vencimento && c.vencimento < hoje()) },
  { titulo: 'Vencem em até 7 dias', contas: abertas.value.filter((c) => c.vencimento && c.vencimento >= hoje() && c.vencimento <= em7Dias) },
  { titulo: 'Depois', contas: abertas.value.filter((c) => !c.vencimento || c.vencimento > em7Dias) },
].filter((g) => g.contas.length))
const falta = computed(() => abertas.value.reduce((s, c) => s + c.valorPrevisto, 0))
const totalPago = computed(() => pagas.value.reduce((s, c) => s + c.pagamento!.valor, 0))
// Calculado, nada gravado: aparecendo uma conta nova, o mês volta a ficar aberto sozinho.
const tudoPago = computed(() => aPagar.value.length > 0 && abertas.value.length === 0)

const paraConferir = computed(() => dados.value?.paraConferir ?? [])
const aConferir = computed(() => paraConferir.value.filter((c) => !c.pagamento).length)

const anteriores = computed(() => dados.value?.anterioresEmAberto ?? [])
const contasAnteriores = computed(() => anteriores.value.reduce((s, m) => s + m.contas, 0))

// ——— Cada conta ———
const variasPessoas = computed(() => (sessao.estado.familia?.membros.length ?? 0) > 1)

function chave(c: ContaDoMes) {
  return `${c.recorrencia.id}-${c.competencia}`
}

function podeAlterar(r: Recorrencia) {
  return sessao.dono.value || r.membro?.id === sessao.estado.usuario?.membroId
}

function quandoVence(c: ContaDoMes): string {
  if (!c.vencimento) return 'sem vencimento'
  const dias = diasEntre(hoje(), c.vencimento)
  if (dias === 0) return 'vence hoje'
  if (dias === 1) return 'vence amanhã'
  if (dias < 0) return `venceu em ${dataPorExtenso(c.vencimento)} (há ${-dias} ${dias === -1 ? 'dia' : 'dias'})`
  return `vence em ${dataPorExtenso(c.vencimento)}`
}

// "Paguei" sempre abre a conferência: valor e data à vista antes de gravar.
const pagando = ref<string | null>(null)

function feito(mensagem: string) {
  pagando.value = null
  avisar(mensagem)
}

const desfazendo = ref<string | null>(null)
const ocupado = ref(false)
const erroDesfazer = ref('')

async function desfazer(c: ContaDoMes) {
  ocupado.value = true
  erroDesfazer.value = ''
  try {
    await apiLancamentos.remover(c.pagamento!.id)
    desfazendo.value = null
    sessao.avisarMudanca()
    avisar(`${c.recorrencia.descricao} voltou para as contas a pagar de ${nomeMes.value}.`)
  } catch (e) {
    erroDesfazer.value = mensagemDeErro(e)
  } finally {
    ocupado.value = false
  }
}

// Conta sem "como é paga": edita ali mesmo para tirar do checklist o que é débito automático.
const editando = ref<Recorrencia | null>(null)

function salvo(mensagem: string) {
  editando.value = null
  avisar(mensagem)
}
</script>

<template>
  <div class="pagina">
    <header class="pagina-cabecalho">
      <div>
        <h1>Contas a pagar</h1>
        <p>As contas do mês que alguém precisa pagar. Pagou, marque aqui: o mês fecha quando todas estiverem pagas.</p>
      </div>
      <div class="periodo" role="group" aria-label="Mês">
        <button type="button" class="botao botao-icone botao-pequeno" aria-label="Mês anterior" @click="mudarMes(-1)">
          <ChevronLeft aria-hidden="true" />
        </button>
        <span class="periodo-nome">{{ nomeMes }}</span>
        <button type="button" class="botao botao-icone botao-pequeno" aria-label="Próximo mês"
                :disabled="mes >= ultimoMes" @click="mudarMes(1)">
          <ChevronRight aria-hidden="true" />
        </button>
      </div>
    </header>

    <div v-if="carregando && !dados" class="painel" aria-busy="true">
      <div v-for="i in 4" :key="i" class="esqueleto linha-esqueleto" />
    </div>

    <Mensagem v-else-if="erro" tipo="erro">
      {{ erro }}
      <template #acoes><button type="button" class="botao botao-pequeno" @click="carregar">Tentar de novo</button></template>
    </Mensagem>

    <template v-else-if="dados">
      <p v-if="contasAnteriores" class="aviso aviso-erro anteriores">
        {{ contasAnteriores }} {{ contasAnteriores === 1 ? 'conta' : 'contas' }}
        de {{ anteriores.length === 1 ? competenciaPorExtenso(anteriores[0].competencia) : 'meses anteriores' }}
        em aberto.
        <button type="button" class="link" @click="irPara(anteriores[0].competencia)">
          Ver {{ competenciaPorExtenso(anteriores[0].competencia) }}
        </button>
      </p>

      <section class="secao">
        <div v-if="tudoPago" class="aviso aviso-sucesso resumo" role="status">
          <CircleCheck aria-hidden="true" />
          <span><strong>Tudo pago em {{ nomeMes }}.</strong> {{ pagas.length }} {{ pagas.length === 1 ? 'conta' : 'contas' }},
            <span class="num">{{ reais(totalPago) }}</span>.</span>
        </div>
        <p v-else-if="aPagar.length" class="resumo-falta" role="status">
          Faltam <strong class="num">{{ reais(falta) }}</strong>
          <span class="texto-2">· {{ pagas.length }} de {{ aPagar.length }} pagas</span>
        </p>

        <div v-if="!aPagar.length" class="painel">
          <EstadoVazio :titulo="`Nenhuma conta a pagar em ${nomeMes}`">
            Entram aqui as contas que se repetem (ou de uma vez só) pagas por boleto ou Pix, e as que ainda não dizem
            como são pagas. Débito automático e cartão aparecem abaixo, só para conferir.
            <template #acoes>
              <RouterLink to="/recorrencias" class="botao">Ver recorrências</RouterLink>
            </template>
          </EstadoVazio>
        </div>

        <div v-for="g in grupos" :key="g.titulo" class="grupo">
          <h2 class="grupo-titulo">{{ g.titulo }} <span class="texto-2">· {{ g.contas.length }}</span></h2>
          <ul class="lista painel">
            <li v-for="c in g.contas" :key="chave(c)" class="conta">
              <div class="linha">
                <span class="item-texto">
                  <span class="descricao">{{ c.recorrencia.descricao }}</span>
                  <span class="meta">
                    {{ c.recorrencia.categoria.nome }} · {{ quandoVence(c) }}<template
                      v-if="c.recorrencia.membro && variasPessoas"> · {{ c.recorrencia.membro.nome }}</template>
                  </span>
                  <span v-if="!c.recorrencia.formaPagamento && podeAlterar(c.recorrencia)" class="selos">
                    <button type="button" class="link meta" @click="editando = c.recorrencia">
                      É débito automático? Diga como é paga
                    </button>
                  </span>
                </span>
                <span class="valor num previsto">{{ reais(c.valorPrevisto) }}</span>
                <button v-if="podeAlterar(c.recorrencia) && pagando !== chave(c)" type="button"
                        class="botao botao-primario botao-pequeno" @click="pagando = chave(c)">Paguei</button>
              </div>
              <div v-if="pagando === chave(c)" class="conferir">
                <ConferirOcorrencia :recorrencia="c.recorrencia" :competencia="c.competencia"
                                    :valor-previsto="c.valorPrevisto" compacto @feito="feito" />
                <button type="button" class="botao botao-sutil botao-pequeno" @click="pagando = null">Cancelar</button>
              </div>
            </li>
          </ul>
        </div>

        <details v-if="pagas.length" class="grupo pagas">
          <summary class="grupo-titulo">
            Pagas <span class="texto-2">· {{ pagas.length }} · <span class="num">{{ reais(totalPago) }}</span></span>
          </summary>
          <ul class="lista painel">
            <li v-for="c in pagas" :key="chave(c)" class="conta">
              <div class="linha">
                <span class="item-texto">
                  <span class="descricao">{{ c.recorrencia.descricao }}</span>
                  <span class="meta">
                    pago em {{ dataPorExtenso(c.pagamento!.data) }}<template
                      v-if="c.pagamento!.membro && variasPessoas"> · {{ c.pagamento!.membro.nome }}</template>
                  </span>
                  <span class="selos">
                    <span class="selo selo-petroleo"><CircleCheck aria-hidden="true" /> paga</span>
                    <span v-if="c.pagamento!.diasAtraso != null" class="selo"
                          :class="c.pagamento!.diasAtraso ? 'selo-atraso' : 'selo-verde'">
                      {{ c.pagamento!.diasAtraso ? `${c.pagamento!.diasAtraso} ${c.pagamento!.diasAtraso === 1 ? 'dia' : 'dias'} de atraso` : 'em dia' }}
                    </span>
                  </span>
                </span>
                <span class="valor num">{{ reais(c.pagamento!.valor) }}</span>
                <button v-if="podeAlterar(c.recorrencia) && desfazendo !== chave(c)" type="button"
                        class="botao botao-sutil botao-pequeno" @click="desfazendo = chave(c); erroDesfazer = ''">
                  Desfazer
                </button>
              </div>
              <div v-if="desfazendo === chave(c)" class="conferir">
                <p class="meta">O lançamento do pagamento é excluído e a conta volta para as contas a pagar.</p>
                <div class="acoes">
                  <button type="button" class="botao botao-perigo botao-pequeno" :disabled="ocupado" @click="desfazer(c)">
                    Desfazer pagamento
                  </button>
                  <button type="button" class="botao botao-pequeno" @click="desfazendo = null">Manter</button>
                </div>
                <Mensagem v-if="erroDesfazer" tipo="erro">{{ erroDesfazer }}</Mensagem>
              </div>
            </li>
          </ul>
        </details>
      </section>

      <section v-if="paraConferir.length" class="secao">
        <div class="secao-cabecalho">
          <h2>Só para conferir <span class="texto-2">· {{ aConferir ? `${aConferir} a conferir` : 'tudo conferido' }}</span></h2>
          <p>Débito automático e cartão saem sozinhos: só veja se o valor veio certo. Não contam para fechar o mês. No
            cartão, cada conta aparece sozinha (a fatura ainda não existe aqui).</p>
        </div>
        <ul class="lista painel">
          <li v-for="c in paraConferir" :key="chave(c)" class="conta">
            <div class="linha">
              <span class="item-texto">
                <span class="descricao">{{ c.recorrencia.descricao }}</span>
                <span class="meta">
                  {{ c.recorrencia.formaPagamento === 'CARTAO_CREDITO' ? 'no cartão' : 'débito automático' }}
                  · {{ c.pagamento ? `${c.recorrencia.formaPagamento === 'CARTAO_CREDITO' ? 'cobrado' : 'debitado'} em ${dataPorExtenso(c.pagamento.data)}` : quandoVence(c) }}
                </span>
                <span v-if="c.pagamento" class="selos">
                  <span class="selo selo-petroleo"><CircleCheck aria-hidden="true" /> conferida</span>
                </span>
              </span>
              <span class="valor num" :class="{ previsto: !c.pagamento }">
                {{ reais(c.pagamento ? c.pagamento.valor : c.valorPrevisto) }}
              </span>
              <button v-if="!c.pagamento && podeAlterar(c.recorrencia) && pagando !== chave(c)" type="button"
                      class="botao botao-pequeno" @click="pagando = chave(c)">Conferir</button>
            </div>
            <div v-if="pagando === chave(c)" class="conferir">
              <ConferirOcorrencia :recorrencia="c.recorrencia" :competencia="c.competencia"
                                  :valor-previsto="c.valorPrevisto" compacto @feito="feito" />
              <button type="button" class="botao botao-sutil botao-pequeno" @click="pagando = null">Cancelar</button>
            </div>
          </li>
        </ul>
      </section>
    </template>

    <PainelLateral :aberto="!!editando" titulo="Editar recorrência" @fechar="editando = null">
      <FormRecorrencia v-if="editando" :key="editando.id" :recorrencia="editando" @salvo="salvo"
                       @cancelar="editando = null" />
    </PainelLateral>
  </div>
</template>

<style scoped>
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

.linha-esqueleto {
  height: 3rem;
  margin: var(--e-3) var(--e-4);
}

.anteriores {
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: var(--e-5);
}

.resumo {
  margin-bottom: var(--e-5);
}

.resumo-falta {
  font-size: var(--t-lg);
  margin-bottom: var(--e-5);
}

.grupo + .grupo {
  margin-top: var(--e-6);
}

.grupo-titulo {
  font-size: var(--t-md);
  margin-bottom: var(--e-2);
}

.pagas summary {
  cursor: pointer;
}

.lista > li.conta {
  display: grid;
  gap: var(--e-3);
}

.linha {
  display: flex;
  align-items: center;
  gap: var(--e-3);
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

/* Ainda não pago: o valor é o previsto, mais apagado que o pago. */
.valor.previsto {
  color: var(--texto-2);
  font-style: italic;
}

.conferir {
  display: grid;
  gap: var(--e-2);
  justify-items: start;
  padding-left: var(--e-2);
}

.link {
  border: 0;
  padding: 0;
  background: none;
  color: var(--petroleo);
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
  text-align: left;
}
</style>
