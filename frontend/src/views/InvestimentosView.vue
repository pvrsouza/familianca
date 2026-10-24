<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft, ChevronRight, Plus } from 'lucide-vue-next'
import { investimentos as api } from '../api/servicos'
import type { Carteira, Investimento, SaldosDoMes, TipoInvestimento } from '../api/tipos'
import { ErroApi, mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import { avisar } from '../estado/avisos'
import { MESES_CURTOS, TIPOS_INVESTIMENTO, anoAtual, competencia, competenciaCurta, competenciaPorExtenso, mesAtual, pct, reais, reaisCurtos } from '../util/formato'
import PainelLateral from '../componentes/PainelLateral.vue'
import CampoValor from '../componentes/CampoValor.vue'
import EstadoVazio from '../componentes/EstadoVazio.vue'
import FaixaEdicao from '../componentes/FaixaEdicao.vue'
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
    const [c, l] = await Promise.all([api.carteira(ano.value), api.listar(), carregarSituacao()])
    carteira.value = c
    lista.value = l
  } catch (e) {
    if (e instanceof ErroApi && e.status === 402) bloqueado.value = true
    else erro.value = mensagemDeErro(e)
  } finally {
    carregando.value = false
  }
}


function semNegativo(v: number) {
  return v < -0.004 ? '−' + reaisCurtos(-v) : reaisCurtos(v)
}

/** Mês em que resgatou tudo: o saldo fica zero, então a célula mostra o resgate (negativo), que é o que aconteceu. */
function resgateTotal(p: { saldos: number[]; aportes: number[] }, i: number) {
  return Math.abs(p.saldos[i]) < 0.005 && p.aportes[i] < -0.004
}

function valorCelula(p: { saldos: number[]; aportes: number[] }, i: number) {
  return resgateTotal(p, i) ? semNegativo(p.aportes[i]) : reaisCurtos(p.saldos[i])
}

const foraDaTabela = computed(() => lista.value.filter((i) => !i.ativo
  && !carteira.value?.investimentos.some((p) => p.investimento.id === i.id)))

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

// ——— Atualizar o saldo de um mês, na própria tabela ———
// Clicar no mês de um investimento abre uma faixa logo abaixo da linha com o saldo do extrato daquele mês e, se
// houve, quanto colocou ou tirou; o rendimento é sempre o calculado. O mês é a coluna clicada.
const edicao = reactive({
  id: null as number | null, nome: '', mes: '', saldoAnterior: 0, saldo: null as number | null,
  aporte: null as number | null, erro: '', salvando: false,
})
/** O mês de referência (o anterior ao atual) e quem já foi atualizado nele: o aviso no topo e "Atualizar saldos". */
const situacao = ref<SaldosDoMes | null>(null)
const mesAtualComp = competencia(anoAtual(), mesAtual())
const faltam = computed(() => (situacao.value ? situacao.value.total - situacao.value.atualizados : 0))

async function carregarSituacao() {
  if (!dono.value) return
  try {
    situacao.value = await api.saldos()
  } catch {
    situacao.value = null
  }
}

/** Mês que dá para atualizar: já chegou e não é antes do primeiro ano com dados. */
function editavel(mes: string) {
  return mes <= mesAtualComp && mes >= `${sessao.primeiroAno.value}-01`
}

async function editar(inv: Investimento, mes: string) {
  if (edicao.id === inv.id && edicao.mes === mes) {
    edicao.id = null
    return
  }
  encerrando.id = null
  Object.assign(edicao, { id: inv.id, nome: inv.nome, mes, saldoAnterior: 0, saldo: null, aporte: null, erro: '' })
  if (Number(mes.slice(0, 4)) !== ano.value) ano.value = Number(mes.slice(0, 4))
  try {
    const linha = (await api.saldos(mes)).linhas.find((l) => l.investimento.id === inv.id)
    if (linha) Object.assign(edicao, { saldoAnterior: linha.saldoAnterior, saldo: linha.saldoInformado, aporte: linha.aporte })
  } catch (e) {
    edicao.erro = mensagemDeErro(e)
  }
  await nextTick()
  document.getElementById('e-saldo')?.focus()
}

/** "Atualizar saldos" e o aviso: abre o primeiro investimento ainda sem saldo no mês de referência. */
function atualizarPendentes() {
  const s = situacao.value
  const l = s?.linhas.find((x) => !x.atualizado) ?? s?.linhas[0]
  if (s && l) editar(l.investimento, s.referencia)
}

/** Rendimento implícito. Sem saldo anterior, o primeiro saldo entra como abertura (não é rendimento). */
const resultado = computed(() => {
  if (edicao.saldo === null) return null
  if (edicao.saldoAnterior <= 0) return { abertura: true, rendimento: 0, pct: null as number | null, estranho: false }
  const rendimento = edicao.saldo - edicao.saldoAnterior - (edicao.aporte ?? 0)
  const pctMes = (rendimento / edicao.saldoAnterior) * 100
  // Acima de 3% (para mais ou para menos) costuma ser aporte ou resgate não informado. Só avisa.
  return { abertura: false, rendimento, pct: pctMes, estranho: Math.abs(pctMes) > 3 }
})

async function salvarSaldo() {
  if (edicao.saldo === null) {
    edicao.erro = 'Informe o saldo do extrato.'
    return
  }
  edicao.salvando = true
  edicao.erro = ''
  try {
    const mes = edicao.mes
    await api.atualizarSaldos(mes, [{ investimentoId: edicao.id!, saldo: edicao.saldo, aporte: edicao.aporte }])
    avisar(`${edicao.nome}: saldo de ${competenciaPorExtenso(mes)} atualizado.`)
    edicao.id = null
    sessao.avisarMudanca()
    await carregar()
    // No mês de referência, segue para o próximo que ainda falta, como numa planilha.
    const proximo = situacao.value?.referencia === mes ? situacao.value.linhas.find((l) => !l.atualizado) : undefined
    if (proximo) editar(proximo.investimento, mes)
  } catch (e) {
    edicao.erro = mensagemDeErro(e)
  } finally {
    edicao.salvando = false
  }
}

// ——— Encerrar = resgatou tudo ———
// Pergunta o mês do resgate: nele o saldo vira zero e o que havia volta para a conta (resgate no fluxo de caixa).
const encerrando = reactive({ id: null as number | null, nome: '', mes: mesAtualComp, erro: '', salvando: false })

function abrirEncerramento(inv: Investimento) {
  edicao.id = null
  Object.assign(encerrando, { id: encerrando.id === inv.id ? null : inv.id, nome: inv.nome, mes: mesAtualComp, erro: '' })
}

/** O que havia no fim do mês anterior ao resgate (se o mês é do ano mostrado), para a tela dizer quanto volta. */
function saldoAntesDoResgate(p: { saldoInicial: number; saldos: number[] }): number | null {
  const [a, m] = encerrando.mes.split('-').map(Number)
  if (a !== ano.value) return null
  return m === 1 ? p.saldoInicial : p.saldos[m - 2]
}

async function encerrar() {
  if (!encerrando.mes) {
    encerrando.erro = 'Informe o mês do resgate.'
    return
  }
  encerrando.salvando = true
  encerrando.erro = ''
  try {
    await api.encerrar(encerrando.id!, encerrando.mes)
    avisar(`${encerrando.nome} encerrado: zerado a partir de ${competenciaPorExtenso(encerrando.mes)}. O histórico continua.`)
    encerrando.id = null
    sessao.avisarMudanca()
    carregar()
  } catch (e) {
    encerrando.erro = mensagemDeErro(e)
  } finally {
    encerrando.salvando = false
  }
}

async function alternarAtivo(inv: Investimento) {
  try {
    await api.atualizar(inv.id, inv.nome, inv.tipo, !inv.ativo)
    avisar(`${inv.nome} reativado. Informe o saldo do extrato: num investimento zerado, ele entra como abertura.`)
    carregar()
  } catch (e) {
    erro.value = mensagemDeErro(e)
  }
}

// Por último: o carregamento imediato usa o estado declarado acima (situacao, edicao).
watch([ano, liberado], carregar, { immediate: true })

// Vindo do lembrete da tela principal: já abre o primeiro investimento que falta.
const route = useRoute()
const router = useRouter()
watch(() => [route.query.atualizar, situacao.value] as const, ([atualizar]) => {
  if (atualizar !== '1' || !dono.value || !situacao.value) return
  router.replace({ query: {} }) // vale uma vez: recarregar os dados não reabre
  if (faltam.value > 0) atualizarPendentes()
})
</script>

<template>
  <div class="pagina pagina-larga">
    <header class="pagina-cabecalho">
      <div>
        <h1>Investimentos</h1>
        <p>A carteira da família mês a mês: o que foi aplicado, o que rendeu e o saldo. Os aportes entram no fluxo de caixa.</p>
      </div>
      <div v-if="liberado && !bloqueado && dono" class="acoes">
        <button type="button" class="botao" @click="novo.aberto = true"><Plus aria-hidden="true" /> Novo investimento</button>
        <button v-if="lista.some((i) => i.ativo)" type="button" class="botao botao-primario" @click="atualizarPendentes()">Atualizar saldos</button>
      </div>
    </header>

    <RecursoBloqueado v-if="!liberado || bloqueado" recurso="Investimentos" plano="Família" />

    <template v-else>
      <div v-if="carregando" class="esqueleto" style="height: 20rem" aria-busy="true" />
      <Mensagem v-else-if="erro" tipo="erro">{{ erro }}</Mensagem>

      <div v-else-if="!lista.length" class="painel">
        <EstadoVazio titulo="A carteira ainda está vazia">
          Cadastre onde a família guarda dinheiro (poupança, Tesouro, previdência). Todo mês, é só atualizar o saldo do
          extrato de cada um: o rendimento é calculado.
          <template v-if="dono" #acoes>
            <button type="button" class="botao botao-primario" @click="novo.aberto = true">Novo investimento</button>
          </template>
        </EstadoVazio>
      </div>

      <template v-else-if="carteira">
        <Mensagem v-if="situacao && faltam > 0 && lista.some((i) => i.ativo)" tipo="info" class="falta-saldo">
          Faltam os saldos de {{ competenciaPorExtenso(situacao.referencia) }}: {{ situacao.atualizados }} de
          {{ situacao.total }} investimentos atualizados.
          <template #acoes><button type="button" class="botao botao-pequeno" @click="atualizarPendentes()">Atualizar</button></template>
        </Mensagem>
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
              <template v-for="p in carteira.investimentos" :key="p.investimento.id">
              <tr :class="{ encerrado: !p.investimento.ativo }">
                <th scope="row">
                  <span class="nome-linha">{{ p.investimento.nome }}</span>
                  <span class="tipo">{{ TIPOS_INVESTIMENTO[p.investimento.tipo] }}</span>
                  <span v-if="p.abertura" class="tipo" :title="'Saldo que já existia quando o investimento começou a ser acompanhado: não conta como rendimento nem aporte'">
                    abertura {{ reaisCurtos(p.abertura.valor) }} em {{ competenciaCurta(p.abertura.competencia) }}
                  </span>
                  <span v-if="dono" class="acoes-inv">
                    <button v-if="p.investimento.ativo && situacao" type="button" class="link-inv" @click="editar(p.investimento, situacao.referencia)">Atualizar saldo</button>
                    <button v-if="p.investimento.ativo" type="button" class="link-inv" @click="abrirEncerramento(p.investimento)">Encerrar</button>
                    <button v-else type="button" class="link-inv" @click="alternarAtivo(p.investimento)">Reativar</button>
                  </span>
                </th>
                <td v-for="(v, i) in p.saldos" :key="i" class="num"
                    :class="{ editando: edicao.id === p.investimento.id && edicao.mes === competencia(ano, i + 1) }">
                  <button v-if="dono && p.investimento.ativo && editavel(competencia(ano, i + 1))" type="button" class="celula"
                          :aria-label="`${p.investimento.nome}, ${MESES_CURTOS[i]}: ${reaisCurtos(v)}. Atualizar o saldo`"
                          @click="editar(p.investimento, competencia(ano, i + 1))"><span :class="{ resgate: resgateTotal(p, i) }">{{ valorCelula(p, i) }}</span></button>
                  <span v-else :class="{ resgate: resgateTotal(p, i) }">{{ valorCelula(p, i) }}</span>
                </td>
                <td class="num">{{ pct(p.rentabilidadeAno, 2) }}</td>
              </tr>
              <tr v-if="edicao.id === p.investimento.id" class="linha-edicao">
                <td colspan="14">
                  <FaixaEdicao aberto :titulo="`${edicao.nome} · ${competenciaPorExtenso(edicao.mes)}`"
                               :descricao="`Saldo no fim do mês anterior: ${reais(edicao.saldoAnterior)}`" @fechar="edicao.id = null">
                    <form class="form-saldo" novalidate @submit.prevent="salvarSaldo">
                      <div class="campo">
                        <label for="e-saldo">Saldo do extrato</label>
                        <CampoValor id="e-saldo" v-model="edicao.saldo" />
                      </div>
                      <div class="campo">
                        <label for="e-aporte" title="Tirou dinheiro: com sinal de menos (ex.: -500,00)">Colocou ou tirou <span class="texto-2">(opcional)</span></label>
                        <CampoValor id="e-aporte" v-model="edicao.aporte" negativo />
                      </div>
                      <button type="submit" class="botao botao-primario" :disabled="edicao.salvando">Salvar</button>
                    </form>
                    <p v-if="resultado?.abertura" class="resultado">Primeiro saldo: entra como saldo de abertura (o dinheiro já existia), não como rendimento.</p>
                    <p v-else-if="resultado" class="resultado">
                      {{ resultado.rendimento >= 0 ? 'Rendeu' : 'Perdeu' }} {{ reais(Math.abs(resultado.rendimento)) }} ({{ pct(resultado.pct, 2) }}) no mês.
                    </p>
                    <Mensagem v-if="resultado?.estranho" tipo="info">
                      Parece {{ resultado.rendimento > 0 ? 'alto' : 'baixo' }} para um mês. Teve
                      {{ resultado.rendimento > 0 ? 'aporte' : 'resgate' }}? Informe em "Colocou ou tirou" para não contar como rendimento.
                    </Mensagem>
                    <Mensagem v-if="edicao.erro" tipo="erro">{{ edicao.erro }}</Mensagem>
                  </FaixaEdicao>
                </td>
              </tr>
              <tr v-if="encerrando.id === p.investimento.id" class="linha-edicao">
                <td colspan="14">
                  <FaixaEdicao aberto :titulo="`Encerrar ${encerrando.nome}`"
                               descricao="Resgatou tudo: no mês do resgate o saldo vira zero e o que havia volta para a conta (entra como resgate no fluxo de caixa). Dali em diante o investimento fica zerado; o histórico continua."
                               @fechar="encerrando.id = null">
                    <form class="form-saldo" novalidate @submit.prevent="encerrar">
                      <div class="campo">
                        <label for="enc-mes">Mês do resgate</label>
                        <input id="enc-mes" v-model="encerrando.mes" class="entrada" type="month" :max="mesAtualComp" />
                      </div>
                      <button type="submit" class="botao botao-perigo" :disabled="encerrando.salvando">Encerrar</button>
                      <button type="button" class="botao" @click="encerrando.id = null">Cancelar</button>
                    </form>
                    <p v-if="saldoAntesDoResgate(p)" class="resultado">
                      Volta para a conta: {{ reais(saldoAntesDoResgate(p)) }} (o saldo do mês anterior).
                    </p>
                    <Mensagem v-if="encerrando.erro" tipo="erro">{{ encerrando.erro }}</Mensagem>
                  </FaixaEdicao>
                </td>
              </tr>
              </template>
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

        <!-- Encerrado sem saldo neste ano não aparece na tabela: fica aqui para poder reativar. -->
        <p v-if="dono && foraDaTabela.length" class="fora-tabela texto-2">
          Encerrados sem saldo em {{ ano }}:
          <template v-for="(inv, i) in foraDaTabela" :key="inv.id">{{ i ? ' · ' : ' ' }}{{ inv.nome }}
            <button type="button" class="link-inv" @click="alternarAtivo(inv)">Reativar</button></template>
        </p>
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

  </div>
</template>

<style scoped>
.falta-saldo {
  margin-bottom: var(--e-4);
}

.celula {
  border: 0;
  padding: 0;
  background: none;
  color: inherit;
  font: inherit;
  cursor: pointer;
  border-radius: 4px;
}

.celula:hover {
  color: var(--acao);
  text-decoration: underline;
}

.carteira td.editando {
  outline: 2px solid var(--acao);
  outline-offset: -2px;
}

.linha-edicao > td {
  padding: 0;
  text-align: left;
  white-space: normal;
}

.form-saldo {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--e-3);
}

.form-saldo .campo {
  flex: 0 1 14rem;
}

.linha-saldo {
  display: grid;
  gap: var(--e-2);
  margin: 0;
  padding: var(--e-3) 0 var(--e-4);
  border: 0;
  border-top: 1px solid var(--linha);
}

.linha-saldo legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--e-2);
  padding: var(--e-3) 0 0;
}

.mes-saldos {
  min-width: 9rem;
  text-align: center;
  font-weight: 700;
}

.resultado {
  font-size: var(--t-xs);
  color: var(--texto-2);
}

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

/* Os 12 meses cabem sem rolagem lateral: página larga como a do ano e células mais justas. */
.pagina-larga {
  max-width: 100rem;
}

.carteira th,
.carteira td {
  padding-inline: var(--e-2);
}

.carteira th:first-child {
  position: sticky;
  left: 0;
  background: var(--papel);
  min-width: 11rem;
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

.nome-linha {
  font-weight: 600;
}

.resgate {
  color: var(--erro);
}

.acoes-inv {
  display: flex;
  gap: var(--e-3);
  margin-top: var(--e-1);
}

.link-inv {
  border: 0;
  padding: 0;
  background: none;
  color: var(--acao);
  font: inherit;
  font-size: var(--t-xs);
  font-weight: 600;
  cursor: pointer;
}

.link-inv:hover {
  text-decoration: underline;
}

.fora-tabela {
  margin-top: var(--e-3);
  font-size: var(--t-sm);
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
