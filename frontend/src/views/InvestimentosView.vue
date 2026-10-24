<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, Plus } from 'lucide-vue-next'
import { investimentos as api } from '../api/servicos'
import type { Carteira, Investimento, Movimento, TipoInvestimento } from '../api/tipos'
import { ErroApi, mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import { avisar } from '../estado/avisos'
import { MESES, MESES_CURTOS, TIPOS_INVESTIMENTO, anoAtual, competencia, competenciaCurta, mesAtual, pct, reais, reaisCurtos } from '../util/formato'
import PainelLateral from '../componentes/PainelLateral.vue'
import CampoValor from '../componentes/CampoValor.vue'
import EstadoVazio from '../componentes/EstadoVazio.vue'
import Mensagem from '../componentes/Mensagem.vue'
import RecursoBloqueado from '../componentes/RecursoBloqueado.vue'

const liberado = computed(() => sessao.tem('INVESTIMENTOS'))
const dono = computed(() => sessao.dono.value)
const ano = ref(anoAtual())
const carteira = ref<Carteira | null>(null)
const lista = ref<Investimento[]>([])
const carregando = ref(true)
const erro = ref('')
const bloqueado = ref(false)

async function carregar() {
  if (!liberado.value) {
    carregando.value = false
    return
  }
  erro.value = ''
  try {
    const [c, l] = await Promise.all([api.carteira(ano.value), api.listar()])
    carteira.value = c
    lista.value = l
  } catch (e) {
    if (e instanceof ErroApi && e.status === 402) bloqueado.value = true
    else erro.value = mensagemDeErro(e)
  } finally {
    carregando.value = false
  }
}

watch([ano, liberado], carregar, { immediate: true })

function semNegativo(v: number) {
  return v < -0.004 ? '−' + reaisCurtos(-v) : reaisCurtos(v)
}

const saldoFinal = computed(() => {
  const s = carteira.value?.saldos ?? []
  const ate = ano.value === anoAtual() ? mesAtual() : 12
  return s[ate - 1] ?? 0
})

// ——— Novo investimento ———
const novo = reactive({ aberto: false, nome: '', tipo: 'RENDA_FIXA' as TipoInvestimento, erro: '' })

async function criar() {
  if (!novo.nome.trim()) {
    novo.erro = 'Dê um nome, como o banco ou o produto.'
    return
  }
  try {
    await api.criar(novo.nome.trim(), novo.tipo)
    avisar(`${novo.nome.trim()} adicionado à carteira.`)
    Object.assign(novo, { aberto: false, nome: '', erro: '' })
    carregar()
  } catch (e) {
    novo.erro = mensagemDeErro(e)
  }
}

// ——— Movimento do mês ———
const mov = reactive({
  aberto: false,
  investimentoId: 0,
  mes: competencia(anoAtual(), mesAtual()),
  aporte: null as number | null,
  rentabilidade: null as number | null,
  saldoInformado: null as number | null,
  existentes: [] as Movimento[],
  erro: '',
  salvando: false,
})

async function abrirMovimento(inv?: Investimento) {
  const alvo = inv ?? lista.value.find((i) => i.ativo)
  if (!alvo) return
  Object.assign(mov, {
    aberto: true, investimentoId: alvo.id, mes: competencia(ano.value, ano.value === anoAtual() ? mesAtual() : 12),
    aporte: null, rentabilidade: null, saldoInformado: null, erro: '', existentes: [],
  })
  await carregarMovimentos()
}

async function carregarMovimentos() {
  try {
    mov.existentes = await api.movimentos(mov.investimentoId)
    preencher()
  } catch {
    mov.existentes = []
  }
}

function preencher() {
  const m = mov.existentes.find((x) => x.competencia === mov.mes)
  mov.aporte = m?.aporte ?? null
  mov.rentabilidade = m?.rentabilidade ?? null
  mov.saldoInformado = m?.saldoInformado ?? null
}

const movimentoExiste = computed(() => mov.existentes.some((x) => x.competencia === mov.mes))

// Primeiro registro do investimento: o saldo do extrato vira abertura (o dinheiro já existia), não rendimento.
const primeiroRegistro = computed(() => !mov.existentes.some((x) => x.competencia < mov.mes))

/**
 * Saldo do extrato que dá um rendimento implícito acima de 3% do saldo anterior (para mais ou para menos) costuma
 * ser aporte ou resgate não registrado. Só avisa: quem quiser salva assim mesmo.
 */
const rendimentoEstranho = computed(() => {
  if (mov.saldoInformado === null || primeiroRegistro.value || !carteira.value) return null
  const [a, m] = mov.mes.split('-').map(Number)
  if (a !== carteira.value.ano) return null
  const p = carteira.value.investimentos.find((x) => x.investimento.id === mov.investimentoId)
  const anterior = p ? (m === 1 ? p.saldoInicial : p.saldos[m - 2]) : 0
  if (anterior <= 0) return null
  const rendimento = mov.saldoInformado - anterior - (mov.aporte ?? 0)
  return Math.abs(rendimento / anterior) > 0.03 ? { rendimento, pct: (rendimento / anterior) * 100 } : null
})

async function salvarMovimento() {
  mov.erro = ''
  if (mov.aporte === null && mov.rentabilidade === null && mov.saldoInformado === null) {
    mov.erro = 'Preencha pelo menos um campo: aporte, rentabilidade ou saldo do extrato.'
    return
  }
  mov.salvando = true
  try {
    await api.registrar(mov.investimentoId, mov.mes, {
      aporte: mov.aporte, rentabilidade: mov.rentabilidade, saldoInformado: mov.saldoInformado,
    })
    avisar('Mês registrado na carteira.')
    mov.aberto = false
    sessao.avisarMudanca()
    carregar()
  } catch (e) {
    mov.erro = mensagemDeErro(e)
  } finally {
    mov.salvando = false
  }
}

async function removerMovimento() {
  mov.salvando = true
  try {
    await api.removerMovimento(mov.investimentoId, mov.mes)
    avisar('Registro do mês removido.')
    mov.aberto = false
    sessao.avisarMudanca()
    carregar()
  } catch (e) {
    mov.erro = mensagemDeErro(e)
  } finally {
    mov.salvando = false
  }
}

async function alternarAtivo(inv: Investimento) {
  try {
    await api.atualizar(inv.id, inv.nome, inv.tipo, !inv.ativo)
    avisar(inv.ativo ? `${inv.nome} encerrado. O histórico continua.` : `${inv.nome} reativado.`)
    carregar()
  } catch (e) {
    erro.value = mensagemDeErro(e)
  }
}

const nomeMes = computed(() => {
  const [, m] = mov.mes.split('-').map(Number)
  return m ? MESES[m - 1] : ''
})
</script>

<template>
  <div class="pagina">
    <header class="pagina-cabecalho">
      <div>
        <h1>Investimentos</h1>
        <p>A carteira da família mês a mês: o que foi aplicado, o que rendeu e o saldo. Os aportes entram no fluxo de caixa.</p>
      </div>
      <div v-if="liberado && !bloqueado && dono" class="acoes">
        <button type="button" class="botao" @click="novo.aberto = true"><Plus aria-hidden="true" /> Novo investimento</button>
        <button v-if="lista.some((i) => i.ativo)" type="button" class="botao botao-primario" @click="abrirMovimento()">Registrar mês</button>
      </div>
    </header>

    <RecursoBloqueado v-if="!liberado || bloqueado" recurso="Investimentos" plano="Família" />

    <template v-else>
      <div v-if="carregando" class="esqueleto" style="height: 20rem" aria-busy="true" />
      <Mensagem v-else-if="erro" tipo="erro">{{ erro }}</Mensagem>

      <div v-else-if="!lista.length" class="painel">
        <EstadoVazio titulo="A carteira ainda está vazia">
          Cadastre onde a família guarda dinheiro (poupança, Tesouro, previdência) e registre todo mês o aporte e o
          rendimento.
          <template v-if="dono" #acoes>
            <button type="button" class="botao botao-primario" @click="novo.aberto = true">Novo investimento</button>
          </template>
        </EstadoVazio>
      </div>

      <template v-else-if="carteira">
        <div class="ano" role="group" aria-label="Ano">
          <button type="button" class="botao botao-icone botao-pequeno" :aria-label="`Ver ${ano - 1}`" :disabled="ano <= sessao.primeiroAno.value" @click="ano--"><ChevronLeft aria-hidden="true" /></button>
          <span class="num ano-numero">{{ ano }}</span>
          <button type="button" class="botao botao-icone botao-pequeno" :aria-label="`Ver ${ano + 1}`" @click="ano++"><ChevronRight aria-hidden="true" /></button>
        </div>

        <p class="resumo">
          A carteira começou {{ ano }} com <strong class="num">{{ reais(carteira.saldoInicial) }}</strong>.
          No ano, foram aplicados <strong class="num">{{ reais(carteira.totalAportes) }}</strong> e rendeu
          <strong class="num receita">{{ reais(carteira.totalRendimentos) }}</strong><template v-if="carteira.rentabilidadeAno !== null">
          ({{ pct(carteira.rentabilidadeAno, 2) }} no ano)</template>. Saldo agora: <strong class="num">{{ reais(saldoFinal) }}</strong>.
        </p>

        <div class="tabela-rolagem">
          <table class="tabela carteira">
            <thead>
              <tr>
                <th scope="col">Saldo no fim do mês (R$)</th>
                <th v-for="m in MESES_CURTOS" :key="m" scope="col">{{ m }}</th>
                <th scope="col">No ano</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in carteira.investimentos" :key="p.investimento.id" :class="{ encerrado: !p.investimento.ativo }">
                <th scope="row">
                  <button v-if="dono && p.investimento.ativo" type="button" class="nome-inv" @click="abrirMovimento(p.investimento)">
                    {{ p.investimento.nome }}
                  </button>
                  <span v-else>{{ p.investimento.nome }}</span>
                  <span class="tipo">{{ TIPOS_INVESTIMENTO[p.investimento.tipo] }}</span>
                  <span v-if="p.abertura" class="tipo" :title="'Saldo que já existia quando o investimento começou a ser acompanhado: não conta como rendimento nem aporte'">
                    abertura {{ reaisCurtos(p.abertura.valor) }} em {{ competenciaCurta(p.abertura.competencia) }}
                  </span>
                </th>
                <td v-for="(v, i) in p.saldos" :key="i" class="num">{{ reaisCurtos(v) }}</td>
                <td class="num">{{ pct(p.rentabilidadeAno, 2) }}</td>
              </tr>
              <tr class="forte">
                <th scope="row">Carteira</th>
                <td v-for="(v, i) in carteira.saldos" :key="i" class="num">{{ reaisCurtos(v) }}</td>
                <td class="num">{{ pct(carteira.rentabilidadeAno, 2) }}</td>
              </tr>
              <tr class="sub">
                <th scope="row">Aportes</th>
                <td v-for="(v, i) in carteira.aportes" :key="i" class="num">{{ semNegativo(v) }}</td>
                <td class="num">{{ semNegativo(carteira.totalAportes) }}</td>
              </tr>
              <tr class="sub">
                <th scope="row">Rendimentos</th>
                <td v-for="(v, i) in carteira.rendimentos" :key="i" class="num">{{ semNegativo(v) }}</td>
                <td class="num">{{ semNegativo(carteira.totalRendimentos) }}</td>
              </tr>
              <tr class="sub">
                <th scope="row">Rentabilidade</th>
                <td v-for="(v, i) in carteira.rentabilidadeMensal" :key="i" class="num">{{ pct(v, 2) }}</td>
                <td class="num">{{ pct(carteira.rentabilidadeAno, 2) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <section v-if="dono" class="secao">
          <h2>Na carteira</h2>
          <ul class="lista painel">
            <li v-for="inv in lista" :key="inv.id">
              <span class="item-texto">
                <span class="nome">{{ inv.nome }}</span>
                <span class="meta">{{ TIPOS_INVESTIMENTO[inv.tipo] }}<template v-if="!inv.ativo"> · encerrado</template></span>
              </span>
              <button v-if="inv.ativo" type="button" class="botao botao-sutil botao-pequeno" @click="abrirMovimento(inv)">Registrar mês</button>
              <button type="button" class="botao botao-sutil botao-pequeno" @click="alternarAtivo(inv)">{{ inv.ativo ? 'Encerrar' : 'Reativar' }}</button>
            </li>
          </ul>
        </section>
      </template>
    </template>

    <PainelLateral :aberto="novo.aberto" titulo="Novo investimento" @fechar="novo.aberto = false">
      <form class="formulario" novalidate @submit.prevent="criar">
        <div class="campo">
          <label for="i-nome">Nome</label>
          <input id="i-nome" v-model="novo.nome" class="entrada" maxlength="100" placeholder="Ex.: CDB do banco, Tesouro IPCA 2035" />
        </div>
        <div class="campo">
          <label for="i-tipo">Tipo</label>
          <select id="i-tipo" v-model="novo.tipo" class="selecao">
            <option v-for="(rotulo, chave) in TIPOS_INVESTIMENTO" :key="chave" :value="chave">{{ rotulo }}</option>
          </select>
        </div>
        <Mensagem v-if="novo.erro" tipo="erro">{{ novo.erro }}</Mensagem>
        <div class="acoes fim">
          <button type="button" class="botao" @click="novo.aberto = false">Cancelar</button>
          <button type="submit" class="botao botao-primario">Adicionar</button>
        </div>
      </form>
    </PainelLateral>

    <PainelLateral :aberto="mov.aberto" titulo="Registrar mês" descricao="O que aconteceu neste investimento no mês."
                   @fechar="mov.aberto = false">
      <form class="formulario" novalidate @submit.prevent="salvarMovimento">
        <div class="grade-campos duas">
          <div class="campo">
            <label for="m-inv">Investimento</label>
            <select id="m-inv" v-model="mov.investimentoId" class="selecao" @change="carregarMovimentos">
              <option v-for="inv in lista.filter((i) => i.ativo)" :key="inv.id" :value="inv.id">{{ inv.nome }}</option>
            </select>
          </div>
          <div class="campo">
            <label for="m-mes">Mês</label>
            <input id="m-mes" v-model="mov.mes" class="entrada" type="month" @change="preencher" />
          </div>
        </div>
        <div class="campo">
          <label for="m-aporte">Aporte do mês</label>
          <CampoValor id="m-aporte" v-model="mov.aporte" negativo />
          <span class="ajuda">Resgate entra com sinal de menos (ex.: -500,00).</span>
        </div>
        <div class="campo">
          <label for="m-rent">Rentabilidade do mês (%)</label>
          <input id="m-rent" v-model.number="mov.rentabilidade" class="entrada num" type="number" step="0.01" inputmode="decimal"
                 placeholder="Ex.: 0,95" />
        </div>
        <div class="campo">
          <label for="m-saldo">Saldo do extrato <span class="texto-2">(opcional)</span></label>
          <CampoValor id="m-saldo" v-model="mov.saldoInformado" />
          <span class="ajuda">Se informado, vale no lugar do saldo calculado.</span>
        </div>
        <Mensagem v-if="mov.saldoInformado !== null && primeiroRegistro" tipo="info">
          Primeiro registro deste investimento: o saldo do extrato entra como saldo de abertura (o dinheiro já
          existia), não como rendimento.
        </Mensagem>
        <Mensagem v-else-if="rendimentoEstranho" tipo="info">
          Com este saldo, {{ nomeMes }} teria {{ reais(rendimentoEstranho.rendimento) }} de rendimento
          ({{ pct(rendimentoEstranho.pct, 1) }}), o que parece {{ rendimentoEstranho.rendimento > 0 ? 'alto' : 'baixo' }}.
          Teve {{ rendimentoEstranho.rendimento > 0 ? 'aporte' : 'resgate' }}? Informe-o acima para não contar como
          rendimento.
        </Mensagem>
        <Mensagem v-if="movimentoExiste" tipo="info">{{ nomeMes }} já tem registro; salvar substitui.</Mensagem>
        <Mensagem v-if="mov.erro" tipo="erro">{{ mov.erro }}</Mensagem>
        <div class="acoes fim">
          <button v-if="movimentoExiste" type="button" class="botao botao-perigo" :disabled="mov.salvando" @click="removerMovimento">Remover</button>
          <button type="submit" class="botao botao-primario" :disabled="mov.salvando">Salvar</button>
        </div>
      </form>
    </PainelLateral>
  </div>
</template>

<style scoped>
.ano {
  display: flex;
  align-items: center;
  gap: var(--e-1);
  margin-bottom: var(--e-3);
}

.ano-numero {
  min-width: 3.5rem;
  text-align: center;
  font-size: var(--t-xl);
  font-weight: 700;
  color: var(--petroleo-escuro);
}

.resumo {
  max-width: 76ch;
  margin-bottom: var(--e-5);
  font-size: var(--t-lg);
  line-height: 1.6;
}

.carteira th:first-child {
  position: sticky;
  left: 0;
  background: var(--papel);
  min-width: 14rem;
  font-weight: 500;
}

.carteira thead th:first-child {
  background: var(--areia-funda);
}

.carteira thead th {
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.carteira td {
  height: 2.5rem;
}

.nome-inv {
  border: 0;
  padding: 0;
  background: none;
  color: var(--acao);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
  text-decoration: underline;
  text-decoration-color: var(--pedra-forte);
  text-underline-offset: 0.2em;
}

.tipo {
  display: block;
  font-size: 0.75rem;
  color: var(--texto-2);
}

.forte th,
.forte td {
  background: var(--areia);
  border-top: 2px solid var(--pedra-forte);
}

.carteira .forte th:first-child {
  background: var(--areia);
}

.sub th,
.sub td {
  color: var(--texto-2);
}

.encerrado th,
.encerrado td {
  color: var(--cinza-claro);
}

.item-texto {
  display: grid;
  flex: 1;
}

.nome {
  font-weight: 600;
}

.meta {
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.fim {
  justify-content: flex-end;
}
</style>
