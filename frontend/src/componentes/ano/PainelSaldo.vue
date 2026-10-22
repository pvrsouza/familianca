<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Pencil, Plus, Trash2 } from 'lucide-vue-next'
import type { LugarSaldo, MovimentoDoMes, Saldo, VisaoAnual } from '../../api/tipos'
import { lugaresSaldo, saldos } from '../../api/servicos'
import { mensagemDeErro } from '../../api/http'
import { sessao } from '../../estado/sessao'
import { avisar } from '../../estado/avisos'
import { MESES, TIPOS_LUGAR_SALDO, competencia, hoje, reais } from '../../util/formato'
import PainelLateral from '../PainelLateral.vue'
import CampoValor from '../CampoValor.vue'
import Mensagem from '../Mensagem.vue'
import FormLugarSaldo from '../FormLugarSaldo.vue'

// Saldo em conta de um mês. A família informa uma vez o saldo inicial, de preferência lugar a lugar (conta,
// poupança, dinheiro na mão, cofrinho...); depois o sistema começa cada mês com a sobra do anterior. Aqui também se
// corrige um mês quando o banco mostra outro valor: a diferença vale daquele mês em diante.
// São dois saldos, cada um no seu campo: o do dia 1º (o ponto de partida do fluxo) e o de hoje (para conferir). Com
// os dois, o de hoje só é comparado com o dia 1º + o lançado até hoje. Só com o de hoje, o servidor guarda o valor
// com o dia e volta até o dia 1º desfazendo o que foi lançado de 1º até hoje, refazendo a conta a cada visão.
/** foraDoFluxo: aberto pelo guia "Como começar"; o aviso ao guardar diz onde conferir depois. */
const props = defineProps<{ visao: VisaoAnual; mes: number | null; informados: Saldo[]; foraDoFluxo?: boolean }>()
/** trocarMes: outro mês do mesmo ano escolhido para o saldo inicial (de outro ano, o painel abre lá pela rota). */
const emit = defineEmits<{ fechar: []; mudou: []; trocarMes: [mes: number] }>()
const route = useRoute()
const router = useRouter()

type Forma = 'lugares' | 'total'
const hojeIso = hoje()

const valorHoje = ref<number | null>(null)
const dataSaldo = ref('')
/** Lugares de um saldo antigo guardado só com o de um dia: voltam ao servidor se o valor desse dia não mudar. */
const lugaresDoDia = ref<{ localId: number; valor: number }[]>([])
const movimento = ref<MovimentoDoMes | null>(null)
const forma = ref<Forma>('lugares')
const valorTotal = ref<number | null>(null)
const valores = ref<Record<number, number | null>>({})
const lugares = ref<LugarSaldo[]>([])
const incluindo = ref(false)
const editandoLugar = ref<number | null>(null)
const obs = ref('')
const salvando = ref(false)
const erro = ref('')

const comp = computed(() => (props.mes === null ? '' : competencia(props.visao.ano, props.mes + 1)))
const nomeMes = computed(() => (props.mes === null ? '' : MESES[props.mes]))
const mesAnterior = computed(() => (props.mes === null ? '' : MESES[(props.mes + 11) % 12]))
const salvo = computed(() => props.informados.find((s) => s.competencia === comp.value))

/** Saldo inicial = nenhum mês antes deste tem saldo informado (neste ano ou nos anteriores); depois dele, é correção. */
const informadoAntes = computed(() => props.mes !== null
  && (props.visao.resumo.saldoAntesDoAno || props.visao.resumo.sobraInformada.slice(0, props.mes).some(Boolean)))
const inicial = computed(() => !informadoAntes.value)
/** O que o sistema calcula para o dia 1º: a sobra do mês anterior (só faz sentido depois do saldo inicial). */
const calculado = computed(() => {
  if (props.mes === null || !informadoAntes.value) return null
  if (props.mes > 0) return props.visao.resumo.fluxoCaixaLiquido.valores[props.mes - 1]
  // Janeiro: a sobra de dezembro só aparece aqui quando o mês não tem saldo informado por cima.
  const r = props.visao.resumo
  return r.saldoAntesDoAno && !r.sobraInformada[0] ? r.sobraMesAnterior.valores[0] : null
})

const titulo = computed(() => {
  if (props.mes === null) return ''
  if (inicial.value) return `Saldo inicial: ${nomeMes.value}`
  return salvo.value ? `Saldo de ${nomeMes.value}` : `Corrigir o saldo de ${nomeMes.value}`
})
const descricao = computed(() =>
  inicial.value
    ? `Quanto a família tinha em ${nomeMes.value}, somando tudo: contas, poupança, dinheiro na mão, cofrinho. Informem uma vez só: nos meses seguintes o sistema começa cada mês com a sobra do anterior.`
    : `O sistema começa ${nomeMes.value} com a sobra de ${mesAnterior.value}. Se o banco mostra outro valor, informem o real: a diferença passa a valer de ${nomeMes.value} em diante.`,
)

// ——— Mês do saldo inicial ———
// Sem saldo nenhum ainda, a família escolhe o mês em que começa: este ou qualquer um que já passou, nunca um futuro.
const mesDeHoje = hojeIso.slice(0, 7)
const podeEscolherMes = computed(() => !sessao.estado.familia?.temSaldoInicial && !salvo.value && inicial.value)
function escolherMes(valor: string) {
  if (!/^\d{4}-\d{2}$/.test(valor) || valor === comp.value) return
  if (valor > mesDeHoje) {
    erro.value = 'O saldo inicial precisa ser deste mês ou de um mês que já passou.'
    return
  }
  erro.value = ''
  const [ano, mes] = valor.split('-').map(Number)
  if (ano === props.visao.ano) {
    emit('trocarMes', mes - 1)
    return
  }
  // Outro ano: a visão daquele ano abre o painel no mês escolhido.
  emit('fechar')
  router.push({ path: ano === Number(mesDeHoje.slice(0, 4)) ? '/ano' : `/ano/${ano}`, query: { ...route.query, saldo: valor } })
}

// ——— Dia do saldo de hoje ———
/** "2026-10-18" → "18/10" */
function diaMes(iso: string): string {
  const [, mes, dia] = iso.split('-')
  return `${dia}/${mes}`
}
const mesComecou = computed(() => comp.value !== '' && comp.value <= hojeIso.slice(0, 7))
const primeiroDia = computed(() => `${comp.value}-01`)
const ultimoDia = computed(() => {
  if (!comp.value) return ''
  const [ano, mes] = comp.value.split('-').map(Number)
  const fim = `${comp.value}-${String(new Date(ano, mes, 0).getDate()).padStart(2, '0')}`
  return fim < hojeIso ? fim : hojeIso
})
const dataValida = computed(() => dataSaldo.value >= primeiroDia.value && dataSaldo.value <= ultimoDia.value)
/** "hoje, 18/10" ou "em 12/10" */
// O saldo de outro dia só existe no mês corrente: num mês que já passou o do dia 1º está no extrato, e a
// conferência dele é a correção do mês seguinte. Saldo guardado com dia quando o mês era o corrente continua
// aparecendo (e se corrige mantendo o dia).
const mesCorrente = computed(() => comp.value === mesDeHoje)
const diaGuardado = computed(() => salvo.value?.dataReferencia ?? salvo.value?.conferencia?.dia ?? null)
const temOutroDia = computed(() => mesCorrente.value || (mesComecou.value && !!diaGuardado.value))
/** "hoje" no mês corrente; num mês que já passou, o dia guardado. */
const outroDia = computed(() => (mesCorrente.value ? 'hoje' : `${diaMes(diaGuardado.value ?? ultimoDia.value)}`))
const quandoDia = computed(() => {
  if (!dataValida.value) return 'do dia'
  return dataSaldo.value === hojeIso ? `de hoje, ${diaMes(hojeIso)}` : `de ${diaMes(dataSaldo.value)}`
})

// ——— Lugares ———
const doMes = computed(() => new Set(salvo.value?.porLocal.map((p) => p.localId) ?? []))
/** Os ativos e os que já estão neste mês (mesmo desativados depois: o mês antigo continua certo). */
const lugaresVisiveis = computed(() => lugares.value.filter((l) => l.ativo || doMes.value.has(l.id)))
const preenchidos = computed(() =>
  lugaresVisiveis.value.filter((l) => valores.value[l.id] !== null && valores.value[l.id] !== undefined),
)
const totalLugares = computed(() => preenchidos.value.reduce((s, l) => s + (valores.value[l.id] ?? 0), 0))
const nomeDono = (id: number | null) => sessao.estado.familia?.membros.find((m) => m.id === id)?.nome ?? null

function lugarIncluido(l: LugarSaldo) {
  lugares.value = [...lugares.value, l]
  valores.value = { ...valores.value, [l.id]: null }
  incluindo.value = false
  avisar(`${l.nome} incluído. Agora informem o saldo dele.`)
}

// Errou o nome, o tipo ou de quem é? Corrige aqui mesmo. Excluir só vale para o que nunca entrou num saldo (o
// servidor confere); o que já entrou se desativa na página Família.
function lugarEditado(l: LugarSaldo) {
  lugares.value = lugares.value.map((x) => (x.id === l.id ? l : x))
  editandoLugar.value = null
}

async function excluirLugar(l: LugarSaldo) {
  erro.value = ''
  try {
    await lugaresSaldo.remover(l.id)
    lugares.value = lugares.value.filter((x) => x.id !== l.id)
    const { [l.id]: _, ...resto } = valores.value
    valores.value = resto
    avisar(`${l.nome} excluído.`)
  } catch (e) {
    erro.value = mensagemDeErro(e)
  }
}

// ——— Valores ———
/** O saldo do dia 1º como a família digitou (nulo = em branco). */
const valorDia1 = computed(() =>
  forma.value === 'total' ? valorTotal.value : preenchidos.value.length ? totalLugares.value : null,
)
const temDia1 = computed(() => valorDia1.value !== null)
const temHoje = computed(() => temOutroDia.value && valorHoje.value !== null)
// Sem o do dia 1º, ele sai do de hoje desfazendo o lançado; com ele, o de hoje fica só para conferir.
const saldoDiaPrimeiro = computed(() => {
  if (temDia1.value) return valorDia1.value
  if (!temHoje.value || !movimento.value) return null
  return valorHoje.value! - movimento.value.recebido + movimento.value.pago
})
/** O que o sistema espera no dia escolhido pelo que está lançado. */
const esperadoNoDia = computed(() =>
  temDia1.value && movimento.value ? valorDia1.value! + movimento.value.recebido - movimento.value.pago : null,
)
/** Banco − lançado: negativo = falta lançar despesa; positivo = falta lançar receita. */
const diferencaHoje = computed(() =>
  temHoje.value && esperadoNoDia.value !== null ? valorHoje.value! - esperadoNoDia.value : null,
)
const diferenca = computed(() =>
  calculado.value !== null && saldoDiaPrimeiro.value !== null ? saldoDiaPrimeiro.value - calculado.value : null,
)

function sinal(v: number): string {
  return v < 0 ? `− ${reais(-v)}` : `+ ${reais(v)}`
}

watch(() => props.mes, async (i) => {
  if (i === null) return
  erro.value = ''
  incluindo.value = false
  editandoLugar.value = null
  movimento.value = null
  const s = salvo.value
  obs.value = s?.observacao ?? ''
  // Saldo guardado só com o de um dia: ele vai para o campo de hoje e o do dia 1º fica em branco (é calculado).
  const soDoDia = !!s?.dataReferencia
  valorHoje.value = soDoDia ? s!.valor : s?.conferencia?.valor ?? null
  dataSaldo.value = (soDoDia ? s!.dataReferencia : s?.conferencia?.dia) ?? ultimoDia.value
  lugaresDoDia.value = soDoDia ? s!.porLocal : []
  valorTotal.value = s && !soDoDia && !s.porLocal.length ? s.valor : null
  valores.value = soDoDia ? {} : Object.fromEntries((s?.porLocal ?? []).map((p) => [p.localId, p.valor]))
  // Saldo inicial: lugar a lugar. Correção: o jeito rápido, só o total (a não ser que o mês já esteja por lugar).
  forma.value = s && !soDoDia ? (s.porLocal.length ? 'lugares' : 'total') : inicial.value ? 'lugares' : 'total'
  try {
    lugares.value = await lugaresSaldo.listar()
  } catch (e) {
    erro.value = mensagemDeErro(e)
  }
}, { immediate: true })

watch([dataSaldo, () => props.mes], async () => {
  movimento.value = null
  const ate = dataSaldo.value
  if (props.mes === null || !temOutroDia.value || !dataValida.value) return
  try {
    const m = await saldos.movimento(ate)
    if (ate === dataSaldo.value) movimento.value = m
  } catch (e) {
    erro.value = mensagemDeErro(e)
  }
})

async function salvar() {
  if (props.mes === null) return
  if (temHoje.value && !dataValida.value) {
    erro.value = `Escolham um dia entre ${diaMes(primeiroDia.value)} e ${diaMes(ultimoDia.value)}.`
    return
  }
  if (!temDia1.value && !temHoje.value) {
    if (temOutroDia.value) erro.value = `Informem o saldo do dia 1º, o de ${outroDia.value}, ou os dois.`
    else if (forma.value === 'total') erro.value = 'Informem o saldo somado.'
    else erro.value = lugaresVisiveis.value.length
      ? 'Informem o saldo de pelo menos um lugar, ou escolham "Só o total".'
      : 'Incluam pelo menos um lugar, ou escolham "Só o total".'
    return
  }
  salvando.value = true
  erro.value = ''
  try {
    const mes = nomeMes.value
    const inicio = saldoDiaPrimeiro.value
    const doDia = temHoje.value ? { dia: dataSaldo.value, valor: valorHoje.value! } : null
    if (temDia1.value) {
      await saldos.definir(comp.value, {
        valor: forma.value === 'total' ? valorTotal.value : null,
        porLocal: forma.value === 'lugares' ? preenchidos.value.map((l) => ({ localId: l.id, valor: valores.value[l.id] ?? 0 })) : null,
        observacao: obs.value.trim(),
        conferencia: doDia,
      })
    } else {
      const mesmosLugares = lugaresDoDia.value.length && valorHoje.value === salvo.value?.valor
      await saldos.definir(comp.value, {
        valor: valorHoje.value,
        porLocal: mesmosLugares ? lugaresDoDia.value : null,
        observacao: obs.value.trim(),
        dataReferencia: dataSaldo.value,
      })
    }
    const guardado = inicio !== null ? `Saldo do dia 1º de ${mes} guardado: ${reais(inicio)}.` : `Saldo de ${mes} guardado.`
    avisar(props.foraDoFluxo ? `${guardado} Para conferir ou corrigir, abram a aba Fluxo de caixa.` : guardado)
    await sessao.recarregarFamilia() // o primeiro saldo libera os lançamentos
    emit('mudou')
    emit('fechar')
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    salvando.value = false
  }
}

async function remover() {
  salvando.value = true
  try {
    await saldos.remover(comp.value)
    avisar(`Saldo removido. ${nomeMes.value.charAt(0).toUpperCase() + nomeMes.value.slice(1)} volta a começar com a sobra do mês anterior.`)
    emit('mudou')
    emit('fechar')
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    salvando.value = false
  }
}
</script>

<template>
  <PainelLateral :aberto="mes !== null" :titulo="titulo" :descricao="descricao" @fechar="emit('fechar')">
    <form v-if="mes !== null" class="formulario" novalidate @submit.prevent="salvar">
      <p v-if="calculado !== null" class="calculado">
        <span>Calculado pelo sistema para o dia 1º</span>
        <strong class="num">{{ reais(calculado) }}</strong>
        <span class="de-onde">a sobra de {{ mesAnterior }}</span>
      </p>

      <div v-if="podeEscolherMes" class="campo">
        <label for="s-mes">Mês em que vocês começam</label>
        <input id="s-mes" :value="comp" type="month" class="entrada mes-inicio" min="2000-01" :max="mesDeHoje"
               @change="escolherMes(($event.target as HTMLInputElement).value)" />
        <span class="ajuda">
          Este mês ou qualquer um que já passou, até de outro ano. A partir dele, cada mês começa com a sobra do
          anterior. Mês que ainda não chegou não serve: o saldo dele ainda não existe.
        </span>
      </div>

      <p v-if="temOutroDia" class="dois-saldos">
        São <strong>dois saldos</strong>: o do <strong>dia 1º</strong>, de onde o fluxo de caixa parte, e o de
        <strong>{{ outroDia }}</strong>, para conferir com o banco. Informem os dois, ou só um: sem o do dia 1º, o
        sistema calcula ele a partir do outro.
      </p>

      <fieldset class="bloco">
        <legend class="titulo-bloco">Saldo no dia 1º de {{ nomeMes }}</legend>
        <p class="ajuda">
          Ex.: o extrato do fim de {{ mesAnterior }}, antes de qualquer conta de {{ nomeMes }}.<template
            v-if="temOutroDia"> Não sabem? Deixem em branco e informem só o de {{ outroDia }}.</template>
        </p>

        <div class="campo">
          <span id="s-forma" class="rotulo">Como informar</span>
          <div class="segmentos" role="group" aria-labelledby="s-forma">
            <button type="button" :aria-pressed="forma === 'lugares'" @click="forma = 'lugares'">Lugar a lugar</button>
            <button type="button" :aria-pressed="forma === 'total'" @click="forma = 'total'">Só o total</button>
          </div>
          <span class="ajuda">
            <template v-if="forma === 'lugares'">
              Conta, poupança, dinheiro na mão, cofrinho: cada lugar é cadastrado uma vez e volta pronto nas próximas.
            </template>
            <template v-else>Um valor só, somando tudo. O jeito rápido de corrigir.</template>
          </span>
        </div>

        <fieldset v-if="forma === 'lugares'" class="lugares">
          <legend class="rotulo">Saldo de cada lugar no dia 1º</legend>
          <p v-if="!lugaresVisiveis.length && !incluindo" class="vazio">
            Nenhum lugar cadastrado ainda. Incluam onde está o dinheiro da família, um por um.
          </p>
          <template v-for="l in lugaresVisiveis" :key="l.id">
          <div class="lugar">
            <label :for="`s-lugar-${l.id}`">
              <span class="nome-lugar">{{ l.nome }}</span>
              <span class="meta-lugar">
                {{ TIPOS_LUGAR_SALDO[l.tipo].rotulo }}<template v-if="nomeDono(l.membroId)"> · {{ nomeDono(l.membroId) }}</template><template
                  v-if="!l.ativo"> · desativado</template>
              </span>
            </label>
            <div class="acoes-lugar">
              <button type="button" class="botao botao-sutil botao-pequeno" :aria-label="`Editar ${l.nome}`"
                      :aria-expanded="editandoLugar === l.id" title="Corrigir nome, tipo ou de quem é"
                      @click="editandoLugar = editandoLugar === l.id ? null : l.id">
                <Pencil aria-hidden="true" />
              </button>
              <button v-if="!doMes.has(l.id)" type="button" class="botao botao-sutil botao-pequeno"
                      :aria-label="`Excluir ${l.nome}`" title="Excluir este lugar" @click="excluirLugar(l)">
                <Trash2 aria-hidden="true" />
              </button>
            </div>
            <CampoValor :id="`s-lugar-${l.id}`" v-model="valores[l.id]" negativo />
          </div>
          <!-- Abaixo da linha, que continua com o valor digitado: corrigir o nome não apaga o saldo. -->
          <FormLugarSaldo v-if="editandoLugar === l.id" :lugar="l" :id-base="`s-editar-${l.id}`" class="editar-lugar"
                          @salvo="lugarEditado" @cancelar="editandoLugar = null" />
          </template>
          <FormLugarSaldo v-if="incluindo" id-base="s-novo" @salvo="lugarIncluido" @cancelar="incluindo = false" />
          <button v-else type="button" class="botao botao-sutil botao-pequeno incluir" @click="incluindo = true">
            <Plus aria-hidden="true" /> Incluir lugar
          </button>
          <p class="ajuda">
            Deixem em branco o que não entra neste mês; zero e negativo valem.<template v-if="inicial"> Alguém entrou na família depois? Incluam
            aqui os lugares dessa pessoa com o saldo do mesmo dia: o saldo inicial é refeito com eles.</template>
          </p>
          <p class="total-lugares">
            <span>Total no dia 1º</span>
            <strong class="num">{{ preenchidos.length ? reais(totalLugares) : '—' }}</strong>
          </p>
        </fieldset>

        <div v-else class="campo">
          <label for="s-valor">Saldo somado no dia 1º</label>
          <CampoValor id="s-valor" v-model="valorTotal" negativo />
          <span class="ajuda">Todo o dinheiro da família junto. Pode ser zero, ou negativo (cheque especial, por
            exemplo).</span>
        </div>
      </fieldset>

      <fieldset v-if="temOutroDia" class="bloco">
        <legend class="titulo-bloco">Saldo {{ quandoDia }}</legend>
        <p class="ajuda">
          <template v-if="temDia1">
            Opcional, para conferir: não muda o saldo do dia 1º nem o fluxo. O sistema compara com o dia 1º mais o que
            está lançado até esse dia e mostra a diferença.
          </template>
          <template v-else>
            Sem o do dia 1º, o sistema calcula ele a partir deste, desfazendo o que foi lançado de 1º até esse dia,
            para as contas já pagas não serem descontadas duas vezes.
          </template>
        </p>
        <p v-if="!mesCorrente" class="ajuda">
          Guardado quando {{ nomeMes }} era o mês corrente. Para um mês que já passou vale só o do dia 1º: podem
          corrigir este valor mantendo o dia, ou apagá-lo e informar o do dia 1º.
        </p>
        <div class="hoje">
          <div class="campo">
            <label for="s-hoje">Saldo somado</label>
            <CampoValor id="s-hoje" v-model="valorHoje" negativo />
          </div>
          <div class="campo">
            <label for="s-dia">Dia</label>
            <input id="s-dia" v-model="dataSaldo" type="date" class="entrada" :min="primeiroDia" :max="ultimoDia"
                   :disabled="!mesCorrente" :aria-invalid="!dataValida || undefined" />
          </div>
        </div>
        <span v-if="!dataValida" class="erro-campo">
          Escolham um dia entre {{ diaMes(primeiroDia) }} e {{ diaMes(ultimoDia) }}.
        </span>
      </fieldset>

      <section v-if="movimento && (temDia1 || temHoje)" class="conta-saldo" aria-labelledby="s-conta" aria-live="polite">
        <h3 id="s-conta">Do dia 1º a {{ diaMes(movimento.ate) }}, pelo que está lançado</h3>
        <dl>
          <div :class="{ informado: temDia1 }">
            <dt>
              Saldo no dia 1º de {{ nomeMes }}
              <span class="origem">{{ temDia1 ? 'vocês informaram' : 'calculado' }}</span>
            </dt>
            <dd class="num">{{ saldoDiaPrimeiro === null ? '—' : reais(saldoDiaPrimeiro) }}</dd>
          </div>
          <div>
            <dt>+ Receitas lançadas de 1º a {{ diaMes(movimento.ate) }}</dt>
            <dd class="num">{{ reais(movimento.recebido) }}</dd>
          </div>
          <div>
            <dt>− Despesas lançadas de 1º a {{ diaMes(movimento.ate) }}</dt>
            <dd class="num">{{ reais(movimento.pago) }}</dd>
          </div>
          <div class="total" :class="{ informado: !temDia1 }">
            <dt>
              = {{ temDia1 ? 'Esperado em' : 'Saldo em' }} {{ diaMes(movimento.ate) }}
              <span class="origem">{{ temDia1 ? 'calculado' : 'vocês informaram' }}</span>
            </dt>
            <dd class="num">{{ reais(temDia1 ? esperadoNoDia! : valorHoje!) }}</dd>
          </div>
          <div v-if="diferencaHoje !== null" class="informado conferido">
            <dt>
              Saldo em {{ diaMes(movimento.ate) }}
              <span class="origem">vocês informaram</span>
            </dt>
            <dd class="num">{{ reais(valorHoje!) }}</dd>
          </div>
        </dl>
        <p v-if="diferencaHoje !== null" class="resultado" :class="{ bate: Math.abs(diferencaHoje) <= 0.004 }">
          <template v-if="Math.abs(diferencaHoje) <= 0.004">Bate com o que está lançado.</template>
          <template v-else-if="diferencaHoje < 0">
            O banco tem <strong class="num">{{ reais(-diferencaHoje) }}</strong> a menos que o lançado: falta lançar
            alguma despesa desse período (ou uma receita foi lançada a mais).
          </template>
          <template v-else>
            O banco tem <strong class="num">{{ reais(diferencaHoje) }}</strong> a mais que o lançado: falta lançar
            alguma receita desse período (ou uma despesa foi lançada a mais).
          </template>
          <template v-if="Math.abs(diferencaHoje) > 0.004"> O fluxo continua partindo do saldo do dia 1º; lancem o
            que falta e a diferença some.</template>
        </p>
        <p v-else class="ajuda">
          A conta usa o que já está lançado no Familiança. Ainda vão lançar uma despesa ou uma receita desse período?
          Podem lançar depois: a conta se refaz sozinha.
        </p>
        <p v-if="!temDia1 && sessao.tem('INVESTIMENTOS')" class="ajuda">
          Aplicaram em investimentos nesse período? Somem o valor aplicado ao saldo do dia, porque a aplicação já
          é descontada no fluxo do mês.
        </p>
      </section>

      <p v-if="diferenca !== null && Math.abs(diferenca) > 0.004" class="diferenca" aria-live="polite">
        Diferença do dia 1º para o calculado: <strong class="num">{{ sinal(diferenca) }}</strong>. Passa a valer de
        {{ nomeMes }} em diante.
      </p>

      <div class="campo">
        <label for="s-obs">Observação <span class="texto-2">(opcional)</span></label>
        <input id="s-obs" v-model="obs" class="entrada" maxlength="200"
               :placeholder="forma === 'total' ? 'Ex.: conta corrente + poupança' : 'Ex.: conferido no extrato'" />
      </div>
      <Mensagem v-if="erro" tipo="erro">{{ erro }}</Mensagem>
      <div class="acoes fim">
        <button v-if="salvo" type="button" class="botao botao-perigo" :disabled="salvando" @click="remover">
          Remover saldo
        </button>
        <button type="submit" class="botao botao-primario" :disabled="salvando">Guardar</button>
      </div>
    </form>
  </PainelLateral>
</template>

<style scoped>
.fim {
  justify-content: flex-end;
}

.campo .segmentos {
  align-self: flex-start;
}

.calculado {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--e-1) var(--e-2);
  padding: var(--e-3) var(--e-4);
  border-radius: var(--raio);
  background: var(--petroleo-tinta);
  font-size: var(--t-sm);
  color: var(--petroleo-escuro);
}

.calculado strong {
  font-size: var(--t-md);
}

.de-onde {
  color: var(--texto-2);
  font-size: var(--t-xs);
}

.lugares {
  display: grid;
  gap: var(--e-2);
  margin: 0;
  padding: 0;
  border: 0;
  min-width: 0;
}

.lugares legend {
  margin-bottom: var(--e-1);
  padding: 0;
  font-size: var(--t-sm);
  font-weight: 600;
}

.lugar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(8.5rem, 11rem);
  align-items: center;
  gap: var(--e-3);
  padding: var(--e-2) 0;
  border-bottom: 1px solid var(--linha);
}

.acoes-lugar {
  display: flex;
}

.editar-lugar {
  margin: var(--e-2) 0;
}

.lugar label {
  display: grid;
  min-width: 0;
}

.nome-lugar {
  font-size: var(--t-sm);
  font-weight: 600;
  overflow-wrap: anywhere;
}

.meta-lugar {
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.vazio,
.lugares .ajuda {
  font-size: var(--t-xs);
  line-height: 1.45;
  color: var(--texto-2);
}

.incluir {
  justify-self: start;
}

.total-lugares {
  display: flex;
  justify-content: space-between;
  gap: var(--e-3);
  padding-top: var(--e-2);
  border-top: 1px solid var(--pedra-forte);
  font-size: var(--t-sm);
  font-weight: 700;
  color: var(--petroleo-escuro);
}

.diferenca {
  font-size: var(--t-sm);
  color: var(--texto-2);
}

.diferenca strong {
  color: var(--texto);
}

/* A conta do saldo de um dia para o do dia 1º, como se faz no papel. */
.conta-saldo {
  display: grid;
  gap: var(--e-2);
  padding: var(--e-3) var(--e-4);
  border: 1px solid var(--pedra);
  border-radius: var(--raio);
  background: var(--areia);
}

.conta-saldo h3 {
  font-size: var(--t-sm);
  font-weight: 650;
}

.conta-saldo dl {
  display: grid;
  gap: var(--e-1);
  margin: 0;
}

.conta-saldo dl > div {
  display: flex;
  justify-content: space-between;
  gap: var(--e-3);
  font-size: var(--t-sm);
}

.conta-saldo dd {
  margin: 0;
  white-space: nowrap;
}

.mes-inicio {
  max-width: 14rem;
}

.dois-saldos {
  font-size: var(--t-sm);
  line-height: 1.45;
  color: var(--texto-2);
}

.dois-saldos strong {
  color: var(--texto);
}

/* Cada saldo no seu bloco: o do dia 1º e o de hoje não dividem campo. */
.bloco {
  display: grid;
  gap: var(--e-3);
  margin: 0;
  padding: var(--e-3) var(--e-4) var(--e-4);
  border: 1px solid var(--pedra);
  border-radius: var(--raio);
  min-width: 0;
}

.titulo-bloco {
  padding: 0 var(--e-1);
  font-size: var(--t-sm);
  font-weight: 700;
  color: var(--petroleo-escuro);
}

.bloco > .ajuda {
  font-size: var(--t-xs);
  line-height: 1.45;
  color: var(--texto-2);
}

.hoje {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 10rem);
  gap: var(--e-3);
  align-items: end;
}

@media (max-width: 30rem) {
  .hoje {
    grid-template-columns: minmax(0, 1fr);
  }
}

.conta-saldo .conferido {
  padding-top: var(--e-1);
}

.resultado {
  font-size: var(--t-sm);
  line-height: 1.45;
  color: var(--texto);
}

.resultado.bate {
  color: var(--petroleo-escuro);
  font-weight: 600;
}

.conta-saldo dt {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0 var(--e-2);
}

.origem {
  font-size: var(--t-xs);
  font-weight: 500;
  color: var(--texto-2);
}

.conta-saldo .informado .origem {
  color: var(--petroleo-escuro);
  font-weight: 650;
}

.conta-saldo .informado:not(.total) {
  font-weight: 700;
  color: var(--petroleo-escuro);
}

.conta-saldo .total {
  margin-top: var(--e-1);
  padding-top: var(--e-2);
  border-top: 1px solid var(--pedra-forte);
  font-weight: 700;
  color: var(--petroleo-escuro);
}

.conta-saldo .ajuda {
  font-size: var(--t-xs);
  line-height: 1.45;
  color: var(--texto-2);
}
</style>
