<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { CalendarClock, Check, ChevronDown, PencilLine, SkipForward, Undo2 } from 'lucide-vue-next'
import { recorrencias as api } from '../api/servicos'
import type { NotaFiscal, Recorrencia } from '../api/tipos'
import { mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import { catalogo } from '../estado/catalogo'
import { competenciaPorExtenso, dataPorExtenso, diasEntre, hoje, reais, vencimentoDe } from '../util/formato'
import CampoValor from './CampoValor.vue'
import CamposNotaFiscal from './CamposNotaFiscal.vue'
import Mensagem from './Mensagem.vue'

// Conferir uma ocorrência prevista. Receita recorrente pode não chegar (ou chegar com outro valor), então a
// pergunta é "chegou?"; despesa recorrente quase sempre acontece, então é "confirmar o valor".
// compacto (no cartão junto à célula): só o essencial à vista (data, Confirmar, Outro valor); pular, vencimento e
// o que vier no slot "mais" (ex.: excluir) ficam atrás de "Mais", e os textos de ajuda saem.
// emLinha (vários itens na faixa da tabela): tudo numa linha só, como uma planilha: valor e vencimento, data,
// Confirmar, Outro valor e Mais. O que abre (outro valor, pular, mais) desce para a linha de baixo.
const props = defineProps<{
  recorrencia: Recorrencia; competencia: string; valorPrevisto: number; compacto?: boolean; emLinha?: boolean
}>()
const emit = defineEmits<{ feito: [mensagem: string] }>()

const receita = computed(() => props.recorrencia.tipo === 'RECEITA')
const podeAlterar = computed(() => sessao.dono.value || props.recorrencia.membro?.id === sessao.estado.usuario?.membroId)
const outroValor = ref(false)
const maisAberto = ref(false)
const confirmandoPulo = ref(false)
const botaoPular = ref<HTMLButtonElement | null>(null)

// Pular não lança nada e tira o valor do ano: antes, explica o que vai acontecer e pede confirmação.
async function perguntarPulo() {
  confirmandoPulo.value = true
  await nextTick()
  botaoPular.value?.focus()
}
const valor = ref<number | null>(props.valorPrevisto)
const ocupado = ref(false)
const erro = ref('')
const mes = computed(() => competenciaPorExtenso(props.competencia))

// ——— Vencimento e data do pagamento (despesa com vencimento) ———
const vencimento = computed(() => (receita.value ? null : vencimentoDe(props.recorrencia, props.competencia)))
const debito = computed(() => props.recorrencia.formaPagamento === 'DEBITO_AUTOMATICO')
const ajustado = computed(() => props.recorrencia.vencimentosAjustados.some((v) => v.competencia === props.competencia))
// Já venceu: supõe pago no vencimento (corrija se foi depois). Ainda vai vencer: pago hoje.
// O lançamento conta no mês da data: hoje, num mês que ainda vai chegar, levaria o valor para o mês atual.
// Mês futuro: sugere o próprio vencimento.
const mesFuturo = props.competencia > hoje().slice(0, 7)
const pagoEm = ref(vencimento.value && (vencimento.value <= hoje() || mesFuturo) ? vencimento.value : hoje())
const atraso = computed(() => (vencimento.value && pagoEm.value ? Math.max(0, diasEntre(vencimento.value, pagoEm.value)) : null))
const textoAtraso = computed(() => {
  if (atraso.value == null) return ''
  if (atraso.value === 0) return 'em dia'
  return `${atraso.value} ${atraso.value === 1 ? 'dia' : 'dias'} de atraso`
})

// ——— Nota fiscal (receita tributável) ———
// O mês em que a nota conta é o do pagamento, então ele é informado a cada mês (o servidor também exige). Começa em
// branco de propósito: o dia previsto não serve. Competência e emissão da nota são opcionais.
onMounted(() => catalogo.carregar())
const ehNota = computed(() => receita.value && !!catalogo.estado.grupos.flatMap((g) => g.categorias)
  .find((c) => c.id === props.recorrencia.categoria.id)?.tributavel)
const recebidoEm = ref('')

/** A data escolhida cai noutro mês: o valor vai contar lá, não no mês da ocorrência. Avisa antes de confirmar. */
function avisoDeMes(data: string): string {
  if (!data || data.slice(0, 7) === props.competencia) return ''
  return `Com esta data, o valor conta em ${competenciaPorExtenso(data.slice(0, 7))}, não em ${mes.value}.`
}
const nfCompetencia = ref('')
const nfEmissao = ref('')
const notaFiscal = computed<NotaFiscal | null>(() =>
  nfCompetencia.value || nfEmissao.value ? { competencia: nfCompetencia.value || null, emissao: nfEmissao.value || null } : null,
)

const mudandoVencimento = ref(false)
const novoVencimento = ref(vencimento.value ?? '')

async function salvarVencimento(valor: string | null) {
  ocupado.value = true
  erro.value = ''
  try {
    await api.ajustarVencimento(props.recorrencia.id, props.competencia, valor)
    sessao.avisarMudanca()
    emit('feito', valor
      ? `${props.recorrencia.descricao}: em ${mes.value} vence em ${dataPorExtenso(valor)}. Os outros meses continuam no dia ${props.recorrencia.dia}.`
      : `${props.recorrencia.descricao}: ${mes.value} voltou a vencer no dia ${props.recorrencia.dia}.`)
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    ocupado.value = false
  }
}

// ——— Previsto diferente só neste mês (a conta veio mais cara ou mais barata), sem confirmar nada ———
const previstoAjustado = computed(() => props.recorrencia.valoresAjustados.some((v) => v.competencia === props.competencia))
/** Há meses depois deste? Então pergunta se o novo previsto vale só aqui ou daqui em diante. */
const temMesesDepois = computed(() => props.recorrencia.frequencia !== 'UNICA'
  && (!props.recorrencia.fim || props.recorrencia.fim > props.competencia))
const escolhendoAlcance = ref(false)

function pedirPrevisto() {
  if (!valor.value || valor.value <= 0) {
    erro.value = 'Informe o valor previsto.'
    return
  }
  erro.value = ''
  if (temMesesDepois.value) escolhendoAlcance.value = true
  else salvarPrevisto(valor.value)
}

async function salvarPrevisto(novo: number | null, daquiEmDiante = false) {
  if (novo !== null && (!novo || novo <= 0)) {
    erro.value = 'Informe o valor previsto.'
    return
  }
  ocupado.value = true
  erro.value = ''
  try {
    await api.ajustarValor(props.recorrencia.id, props.competencia, novo, daquiEmDiante)
    sessao.avisarMudanca()
    emit('feito', novo === null
      ? `${props.recorrencia.descricao}: ${mes.value} voltou ao previsto de sempre.`
      : daquiEmDiante
        ? `${props.recorrencia.descricao}: de ${mes.value} em diante o previsto é ${reais(novo)}. Nada foi confirmado; os meses antes continuam como estavam.`
        : `${props.recorrencia.descricao}: previsto de ${mes.value} agora é ${reais(novo)}. Nada foi confirmado; os outros meses não mudam.`)
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    ocupado.value = false
  }
}

function abrirOutroValor() {
  valor.value = props.valorPrevisto
  outroValor.value = true
}

async function confirmar() {
  if (outroValor.value && (!valor.value || valor.value <= 0)) {
    erro.value = 'Informe o valor que veio.'
    return
  }
  if (ehNota.value && !recebidoEm.value) {
    erro.value = 'Informe o dia em que o dinheiro da nota entrou na conta.'
    return
  }
  ocupado.value = true
  erro.value = ''
  try {
    const v = outroValor.value ? valor.value! : props.valorPrevisto
    await api.confirmar(props.recorrencia.id, props.competencia, outroValor.value ? v : undefined,
      ehNota.value ? recebidoEm.value : vencimento.value ? pagoEm.value : undefined,
      ehNota.value ? notaFiscal.value : undefined)
    sessao.avisarMudanca()
    emit('feito', receita.value
      ? `${props.recorrencia.descricao}: ${reais(v)} recebidos em ${mes.value}.`
      : `${props.recorrencia.descricao} de ${mes.value} confirmada: ${reais(v)}`
        + (textoAtraso.value ? `, paga ${textoAtraso.value}.` : '.'))
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    ocupado.value = false
  }
}

async function pular() {
  confirmandoPulo.value = false
  ocupado.value = true
  erro.value = ''
  try {
    await api.pular(props.recorrencia.id, props.competencia)
    sessao.avisarMudanca()
    emit('feito', receita.value
      ? `${props.recorrencia.descricao}: ${mes.value} ficou sem receber. Sai da renda do ano.`
      : `${mes.value} pulado para ${props.recorrencia.descricao}.`)
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    ocupado.value = false
  }
}
</script>

<template>
  <div class="conferir" :class="{ 'em-linha': emLinha }">
    <p v-if="receita" class="pergunta">
      <span class="rotulo-previsto">Chegou? Previsto: </span><span class="selo selo-previsto num">{{ reais(valorPrevisto) }}</span>
      <span v-if="previstoAjustado" class="so-mes">só neste mês</span>
    </p>
    <p v-else class="pergunta">
      <span class="rotulo-previsto">Previsto: </span><span class="selo selo-previsto num">{{ reais(valorPrevisto) }}</span>
      <span v-if="previstoAjustado" class="so-mes">só neste mês</span>
      <span v-if="vencimento" class="vence" :class="{ vencida: vencimento < hoje() }">
        <CalendarClock aria-hidden="true" />
        {{ vencimento < hoje() ? 'Venceu' : 'Vence' }} em {{ dataPorExtenso(vencimento) }}<template v-if="ajustado"> (só neste mês)</template>
      </span>
    </p>

    <div v-if="vencimento && podeAlterar && !confirmandoPulo" class="pagamento">
      <template v-if="mudandoVencimento">
        <label class="rotulo" :for="`venc-${recorrencia.id}-${competencia}`">Vencimento só em {{ mes }}</label>
        <div class="linha">
          <input :id="`venc-${recorrencia.id}-${competencia}`" v-model="novoVencimento" class="entrada data" type="date" />
          <button type="button" class="botao botao-primario botao-pequeno" :disabled="ocupado || !novoVencimento"
                  @click="salvarVencimento(novoVencimento)">Salvar vencimento</button>
          <button v-if="ajustado" type="button" class="botao botao-sutil botao-pequeno" :disabled="ocupado"
                  @click="salvarVencimento(null)">Voltar ao dia {{ recorrencia.dia }}</button>
          <button type="button" class="botao botao-sutil botao-pequeno" :disabled="ocupado" @click="mudandoVencimento = false">
            <Undo2 aria-hidden="true" /> Voltar
          </button>
        </div>
        <p class="ajuda">
          Para quando o boleto deste mês veio com outra data. Muda só {{ mes }}: os outros meses continuam vencendo no
          dia {{ recorrencia.dia }}. Pode ser até 2 meses antes ou depois.
        </p>
      </template>
      <template v-else>
        <div class="linha">
          <label class="rotulo" :for="`pago-${recorrencia.id}-${competencia}`">{{ debito ? 'Debitado em' : 'Pago em' }}</label>
          <input :id="`pago-${recorrencia.id}-${competencia}`" v-model="pagoEm" class="entrada data" type="date"
                 :aria-describedby="compacto ? undefined : `pago-ajuda-${recorrencia.id}-${competencia}`" />
          <span v-if="textoAtraso" class="selo" :class="atraso ? 'selo-atraso' : 'selo-verde'">{{ textoAtraso }}</span>
          <span v-if="avisoDeMes(pagoEm)" class="aviso-mes" role="status">{{ avisoDeMes(pagoEm) }}</span>
          <button v-if="!compacto" type="button" class="botao botao-sutil botao-pequeno"
                  @click="mudandoVencimento = true; novoVencimento = vencimento ?? ''">
            Vencimento diferente neste mês
          </button>
        </div>
        <p v-if="!compacto" :id="`pago-ajuda-${recorrencia.id}-${competencia}`" class="ajuda">
          {{ debito ? 'O dia em que o débito saiu da conta.' : 'O dia em que a conta foi paga.' }} Comparado com o
          vencimento, mostra se foi paga em dia ou com atraso.
        </p>
      </template>
    </div>

    <div v-if="ehNota && podeAlterar && !confirmandoPulo" class="pagamento">
      <div class="linha">
        <label class="rotulo" :for="`recebido-${recorrencia.id}-${competencia}`">Entrou na conta em</label>
        <input :id="`recebido-${recorrencia.id}-${competencia}`" v-model="recebidoEm" class="entrada data" type="date"
               required :aria-invalid="(!!erro && !recebidoEm) || undefined" />
        <span v-if="avisoDeMes(recebidoEm)" class="aviso-mes" role="status">{{ avisoDeMes(recebidoEm) }}</span>
      </div>
      <CamposNotaFiscal v-if="!compacto || maisAberto" v-model:competencia="nfCompetencia" v-model:emissao="nfEmissao"
                        :id-base="`conf-${recorrencia.id}-${competencia}`" />
    </div>

    <template v-if="!podeAlterar">
      <p class="texto-2 nota">Só {{ recorrencia.membro?.nome ?? 'quem cadastrou' }} ou quem organiza a família confirma.</p>
    </template>

    <div v-else-if="outroValor" class="outro">
      <label class="rotulo" :for="`valor-${recorrencia.id}-${competencia}`">
        {{ receita ? 'Quanto chegou' : 'Valor real' }}
      </label>
      <div class="linha">
        <CampoValor :id="`valor-${recorrencia.id}-${competencia}`" v-model="valor" obrigatorio class="campo-valor" />
        <button type="button" class="botao botao-primario botao-pequeno" :disabled="ocupado" @click="confirmar">
          <Check aria-hidden="true" /> {{ receita ? 'Recebi este valor' : 'Confirmar' }}
        </button>
        <button type="button" class="botao botao-pequeno" :disabled="ocupado" :aria-expanded="temMesesDepois ? escolhendoAlcance : undefined"
                @click="pedirPrevisto">
          <PencilLine aria-hidden="true" /> Só mudar o previsto
        </button>
        <button v-if="previstoAjustado" type="button" class="botao botao-sutil botao-pequeno" :disabled="ocupado"
                @click="salvarPrevisto(null)">
          Voltar ao previsto de sempre
        </button>
        <button type="button" class="botao botao-sutil botao-pequeno" :disabled="ocupado"
                @click="outroValor = false; escolhendoAlcance = false">
          <Undo2 aria-hidden="true" /> Voltar
        </button>
      </div>
      <div v-if="escolhendoAlcance" class="alcance" role="group" :aria-labelledby="`alcance-${recorrencia.id}-${competencia}`">
        <p :id="`alcance-${recorrencia.id}-${competencia}`" class="pergunta-pulo">
          Prever {{ reais(valor ?? 0) }} só em {{ mes }} ou daqui em diante?
        </p>
        <div class="linha">
          <button type="button" class="botao botao-primario botao-pequeno" :disabled="ocupado" @click="salvarPrevisto(valor)">
            Só em {{ mes }}
          </button>
          <button type="button" class="botao botao-pequeno" :disabled="ocupado" @click="salvarPrevisto(valor, true)">
            De {{ mes }} em diante
          </button>
          <button type="button" class="botao botao-sutil botao-pequeno" :disabled="ocupado" @click="escolhendoAlcance = false">
            <Undo2 aria-hidden="true" /> Voltar
          </button>
        </div>
        <p class="ajuda">
          Daqui em diante muda o valor da recorrência a partir de {{ mes }} (um reajuste). Os meses antes que ainda estão
          previstos ficam como estavam, e o que já foi confirmado não muda.
        </p>
      </div>
      <p v-else class="ajuda">
        {{ receita ? 'Recebi este valor: o dinheiro já entrou.' : 'Confirmar: a conta já foi paga com este valor.' }}
        Só mudar o previsto: ainda não {{ receita ? 'chegou' : 'foi paga' }}, então nada é lançado; você escolhe se
        vale só para {{ mes }} ou daqui em diante.
      </p>
    </div>

    <div v-else-if="confirmandoPulo" class="aviso-pulo" role="group" :aria-labelledby="`pulo-${recorrencia.id}-${competencia}`">
      <p :id="`pulo-${recorrencia.id}-${competencia}`" class="pergunta-pulo">
        {{ receita ? `Marcar ${mes} como não recebido?` : `Pular ${mes}?` }}
      </p>
      <ul class="consequencias">
        <template v-if="receita">
          <li>{{ recorrencia.descricao }} não entra na renda de {{ mes }}: {{ reais(valorPrevisto) }} a menos no ano.</li>
          <li>Nada é lançado e o mês sai da lista para conferir.</li>
        </template>
        <template v-else>
          <li>{{ recorrencia.descricao }} deixa de aparecer como prevista em {{ mes }}: {{ reais(valorPrevisto) }} a menos
            nas despesas do ano.</li>
          <li>Nada é lançado. Use quando a conta não existiu neste mês; se ela foi paga, confirme o valor.</li>
        </template>
        <li>Os outros meses continuam previstos. Mudou de ideia? Clique de novo neste mês na visão anual, ou abra a
          recorrência em Recorrências, e use "Voltar a prever".</li>
      </ul>
      <div class="linha">
        <button ref="botaoPular" type="button" class="botao botao-primario botao-pequeno" :disabled="ocupado" @click="pular">
          <SkipForward aria-hidden="true" /> {{ receita ? 'Sim, não recebi' : `Sim, pular ${mes}` }}
        </button>
        <button type="button" class="botao botao-sutil botao-pequeno" :disabled="ocupado" @click="confirmandoPulo = false">
          <Undo2 aria-hidden="true" /> Voltar
        </button>
      </div>
    </div>

    <div v-else class="linha">
      <button type="button" class="botao botao-primario botao-pequeno" :disabled="ocupado" @click="confirmar">
        <Check aria-hidden="true" /> {{ receita ? `Recebi ${reais(valorPrevisto)}` : 'Confirmar' }}
      </button>
      <button type="button" class="botao botao-pequeno" :disabled="ocupado" @click="abrirOutroValor">
        <PencilLine aria-hidden="true" /> {{ receita ? 'Veio outro valor' : 'Outro valor' }}
      </button>
      <button v-if="!compacto" type="button" class="botao botao-sutil botao-pequeno" :disabled="ocupado" @click="perguntarPulo">
        <SkipForward aria-hidden="true" /> {{ receita ? 'Não recebi' : 'Pular o mês' }}
      </button>
      <button v-else type="button" class="botao botao-sutil botao-pequeno mais" :aria-expanded="maisAberto"
              :aria-controls="`mais-${recorrencia.id}-${competencia}`" @click="maisAberto = !maisAberto">
        Mais <ChevronDown aria-hidden="true" />
      </button>
    </div>

    <div v-if="compacto && maisAberto && !outroValor && !confirmandoPulo && !mudandoVencimento"
         :id="`mais-${recorrencia.id}-${competencia}`" class="opcoes-mais">
      <button type="button" class="botao botao-sutil botao-pequeno" :disabled="ocupado" @click="perguntarPulo">
        <SkipForward aria-hidden="true" /> {{ receita ? 'Não recebi neste mês' : 'Pular o mês' }}
      </button>
      <button v-if="vencimento" type="button" class="botao botao-sutil botao-pequeno"
              @click="mudandoVencimento = true; novoVencimento = vencimento ?? ''">
        <CalendarClock aria-hidden="true" /> Vencimento diferente neste mês
      </button>
      <slot name="mais" />
    </div>

    <Mensagem v-if="erro" tipo="erro">{{ erro }}</Mensagem>
  </div>
</template>

<style scoped>
.conferir {
  display: grid;
  gap: var(--e-2);
}

.pergunta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--e-2);
  font-size: var(--t-sm);
  color: var(--texto-2);
}

.pergunta .selo {
  font-weight: 700;
}

.linha {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--e-2);
}

.outro {
  display: grid;
  gap: var(--e-1);
}

.rotulo {
  font-size: var(--t-sm);
  font-weight: 600;
}

.campo-valor {
  width: 9.5rem;
}

.aviso-pulo {
  display: grid;
  gap: var(--e-2);
  padding: var(--e-3) var(--e-4);
  border-radius: var(--raio);
  background: var(--areia);
  border: 1px solid var(--linha);
}

.pergunta-pulo {
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

.nota {
  font-size: var(--t-sm);
}

.alcance {
  display: grid;
  gap: var(--e-2);
  margin-top: var(--e-3);
  padding: var(--e-3);
  border: 1px solid var(--linha);
  border-radius: var(--raio);
  background: var(--areia);
}

.so-mes {
  font-size: var(--t-xs);
  font-weight: 600;
  color: var(--texto-2);
}

.vence {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-size: var(--t-xs);
  font-weight: 600;
  color: var(--texto-2);
}

.vence svg {
  width: 0.9rem;
  height: 0.9rem;
}

.vence.vencida {
  color: var(--coral-borda);
}

.mais svg {
  transition: transform 160ms var(--ease);
}

.mais[aria-expanded='true'] svg {
  transform: rotate(180deg);
}

.opcoes-mais {
  display: flex;
  flex-wrap: wrap;
  gap: var(--e-1);
  padding-top: var(--e-1);
  border-top: 1px dashed var(--linha);
}

.pagamento {
  display: grid;
  gap: var(--e-1);
}

.pagamento .ajuda {
  font-size: var(--t-xs);
  line-height: 1.45;
  color: var(--texto-2);
}

.data {
  width: 10.5rem;
  min-height: 2.25rem;
}

/* Em linha: cada parte é uma coluna; o que abre embaixo ocupa a linha inteira. */
.conferir.em-linha {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--e-2) var(--e-4);
}

.em-linha .pergunta {
  flex: 0 0 19rem;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.em-linha .rotulo-previsto {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

.em-linha > .outro,
.em-linha > .aviso-pulo,
.em-linha > .opcoes-mais,
.em-linha > .nota,
.em-linha > .aviso {
  flex-basis: 100%;
}

.aviso-mes {
  font-size: var(--t-xs);
  font-weight: 600;
  color: var(--erro-texto);
}
</style>
