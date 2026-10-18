<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Plus } from 'lucide-vue-next'
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
// O saldo vale sempre como o do dia 1º; quem tem em mãos o de hoje informa esse, e o sistema volta até o dia 1º
// desfazendo o que foi lançado de 1º até hoje (o servidor guarda o valor com o dia e refaz a conta a cada visão).
const props = defineProps<{ visao: VisaoAnual; mes: number | null; informados: Saldo[] }>()
const emit = defineEmits<{ fechar: []; mudou: [] }>()

type ModoDia = 'dia1' | 'outroDia'
type Forma = 'lugares' | 'total'
const hojeIso = hoje()

const modoDia = ref<ModoDia>('dia1')
const dataSaldo = ref('')
const movimento = ref<MovimentoDoMes | null>(null)
const forma = ref<Forma>('lugares')
const valorTotal = ref<number | null>(null)
const valores = ref<Record<number, number | null>>({})
const lugares = ref<LugarSaldo[]>([])
const incluindo = ref(false)
const obs = ref('')
const salvando = ref(false)
const erro = ref('')

const comp = computed(() => (props.mes === null ? '' : competencia(props.visao.ano, props.mes + 1)))
const nomeMes = computed(() => (props.mes === null ? '' : MESES[props.mes]))
const mesAnterior = computed(() => (props.mes === null ? '' : MESES[(props.mes + 11) % 12]))
const salvo = computed(() => props.informados.find((s) => s.competencia === comp.value))

/** Saldo inicial = nenhum mês antes deste tem saldo informado; depois dele, é correção. */
const informadoAntes = computed(() => props.mes !== null && props.visao.resumo.sobraInformada.slice(0, props.mes).some(Boolean))
const inicial = computed(() => !informadoAntes.value)
/** O que o sistema calcula para o dia 1º: a sobra do mês anterior (só faz sentido depois do saldo inicial). */
const calculado = computed(() =>
  props.mes !== null && props.mes > 0 && informadoAntes.value ? props.visao.resumo.fluxoCaixaLiquido.valores[props.mes - 1] : null,
)

const titulo = computed(() => {
  if (props.mes === null) return ''
  if (inicial.value) return `Saldo inicial: ${nomeMes.value}`
  return salvo.value ? `Saldo de ${nomeMes.value}` : `Corrigir o saldo de ${nomeMes.value}`
})
const descricao = computed(() =>
  inicial.value
    ? `Quanto a família tinha no dia 1º de ${nomeMes.value}, somando tudo: contas, poupança, dinheiro na mão, cofrinho. Informem uma vez só: nos meses seguintes o sistema começa cada mês com a sobra do anterior.`
    : `O sistema começa ${nomeMes.value} com a sobra de ${mesAnterior.value}. Se o banco mostra outro valor, informem o real: a diferença passa a valer de ${nomeMes.value} em diante.`,
)

// ——— Dia do saldo ———
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
const quando = computed(() => {
  if (modoDia.value === 'dia1' || !dataValida.value) return `no dia 1º de ${nomeMes.value}`
  return dataSaldo.value === hojeIso ? `hoje, ${diaMes(hojeIso)}` : `em ${diaMes(dataSaldo.value)}`
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

// ——— Valores ———
const valorInformado = computed(() =>
  forma.value === 'total' ? valorTotal.value : preenchidos.value.length ? totalLugares.value : null,
)
const saldoDiaPrimeiro = computed(() => {
  if (valorInformado.value === null) return null
  if (modoDia.value === 'dia1') return valorInformado.value
  return movimento.value ? valorInformado.value - movimento.value.recebido + movimento.value.pago : null
})
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
  movimento.value = null
  const s = salvo.value
  obs.value = s?.observacao ?? ''
  valorTotal.value = s && !s.porLocal.length ? s.valor : null
  valores.value = Object.fromEntries((s?.porLocal ?? []).map((p) => [p.localId, p.valor]))
  // Saldo inicial: lugar a lugar. Correção: o jeito rápido, só o total (a não ser que o mês já esteja por lugar).
  forma.value = s ? (s.porLocal.length ? 'lugares' : 'total') : inicial.value ? 'lugares' : 'total'
  const usarDia = s ? !!s.dataReferencia : comp.value === hojeIso.slice(0, 7)
  modoDia.value = usarDia && mesComecou.value ? 'outroDia' : 'dia1'
  dataSaldo.value = s?.dataReferencia ?? ultimoDia.value
  try {
    lugares.value = await lugaresSaldo.listar()
  } catch (e) {
    erro.value = mensagemDeErro(e)
  }
}, { immediate: true })

watch([modoDia, dataSaldo, () => props.mes], async () => {
  movimento.value = null
  if (props.mes === null || modoDia.value !== 'outroDia' || !dataValida.value) return
  const ate = dataSaldo.value
  try {
    const m = await saldos.movimento(ate)
    if (ate === dataSaldo.value) movimento.value = m
  } catch (e) {
    erro.value = mensagemDeErro(e)
  }
})

async function salvar() {
  if (props.mes === null) return
  if (modoDia.value === 'outroDia' && !dataValida.value) {
    erro.value = `Escolham um dia entre ${diaMes(primeiroDia.value)} e ${diaMes(ultimoDia.value)}.`
    return
  }
  if (forma.value === 'lugares' && !preenchidos.value.length) {
    erro.value = lugaresVisiveis.value.length
      ? 'Informem o saldo de pelo menos um lugar, ou escolham "Só o total".'
      : 'Incluam pelo menos um lugar, ou escolham "Só o total".'
    return
  }
  if (forma.value === 'total' && valorTotal.value === null) {
    erro.value = 'Informem o saldo somado.'
    return
  }
  salvando.value = true
  erro.value = ''
  try {
    const mes = nomeMes.value
    const inicio = saldoDiaPrimeiro.value
    await saldos.definir(comp.value, {
      valor: forma.value === 'total' ? valorTotal.value : null,
      porLocal: forma.value === 'lugares' ? preenchidos.value.map((l) => ({ localId: l.id, valor: valores.value[l.id] ?? 0 })) : null,
      observacao: obs.value.trim(),
      dataReferencia: modoDia.value === 'outroDia' ? dataSaldo.value : null,
    })
    avisar(inicio !== null ? `Saldo do dia 1º de ${mes} guardado: ${reais(inicio)}.` : `Saldo de ${mes} guardado.`)
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

      <div v-if="mesComecou" class="campo">
        <span id="s-modo" class="rotulo">Qual saldo vocês têm em mãos?</span>
        <div class="segmentos" role="group" aria-labelledby="s-modo">
          <button type="button" :aria-pressed="modoDia === 'outroDia'" @click="modoDia = 'outroDia'">
            {{ dataSaldo === hojeIso ? 'O de hoje' : 'O de um dia do mês' }}
          </button>
          <button type="button" :aria-pressed="modoDia === 'dia1'" @click="modoDia = 'dia1'">O do dia 1º</button>
        </div>
        <span class="ajuda">
          <template v-if="modoDia === 'outroDia'">
            Informem o saldo do dia e o sistema volta até o dia 1º, desfazendo o que vocês lançaram de 1º até esse
            dia. Assim as contas já pagas não são descontadas duas vezes.
          </template>
          <template v-else>Ex.: o extrato do fim de {{ mesAnterior }}. Vale como está.</template>
        </span>
      </div>

      <div v-if="modoDia === 'outroDia'" class="campo">
        <label for="s-dia">Dia do saldo</label>
        <input id="s-dia" v-model="dataSaldo" type="date" class="entrada" :min="primeiroDia" :max="ultimoDia"
               :aria-invalid="!dataValida || undefined" />
        <span v-if="!dataValida" class="erro-campo">
          Escolham um dia entre {{ diaMes(primeiroDia) }} e {{ diaMes(ultimoDia) }}.
        </span>
      </div>

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
        <legend class="rotulo">Saldo de cada lugar {{ quando }}</legend>
        <p v-if="!lugaresVisiveis.length && !incluindo" class="vazio">
          Nenhum lugar cadastrado ainda. Incluam onde está o dinheiro da família, um por um.
        </p>
        <div v-for="l in lugaresVisiveis" :key="l.id" class="lugar">
          <label :for="`s-lugar-${l.id}`">
            <span class="nome-lugar">{{ l.nome }}</span>
            <span class="meta-lugar">
              {{ TIPOS_LUGAR_SALDO[l.tipo].rotulo }}<template v-if="nomeDono(l.membroId)"> · {{ nomeDono(l.membroId) }}</template><template
                v-if="!l.ativo"> · desativado</template>
            </span>
          </label>
          <CampoValor :id="`s-lugar-${l.id}`" v-model="valores[l.id]" negativo />
        </div>
        <FormLugarSaldo v-if="incluindo" id-base="s-novo" @salvo="lugarIncluido" @cancelar="incluindo = false" />
        <button v-else type="button" class="botao botao-sutil botao-pequeno incluir" @click="incluindo = true">
          <Plus aria-hidden="true" /> Incluir lugar
        </button>
        <p class="ajuda">
          Deixem em branco o que não entra neste mês.<template v-if="inicial"> Alguém entrou na família depois? Incluam
          aqui os lugares dessa pessoa com o saldo do mesmo dia: o saldo inicial é refeito com eles.</template>
        </p>
        <p class="total-lugares">
          <span>Total {{ quando }}</span>
          <strong class="num">{{ preenchidos.length ? reais(totalLugares) : '—' }}</strong>
        </p>
      </fieldset>

      <div v-else class="campo">
        <label for="s-valor">Saldo somado {{ quando }}</label>
        <CampoValor id="s-valor" v-model="valorTotal" negativo />
        <span class="ajuda">Todo o dinheiro da família junto. Pode ser negativo (cheque especial, por exemplo).</span>
      </div>

      <section v-if="modoDia === 'outroDia' && movimento" class="conta-saldo" aria-labelledby="s-conta" aria-live="polite">
        <h3 id="s-conta">Como fica o saldo do dia 1º</h3>
        <dl>
          <div>
            <dt>Saldo em {{ diaMes(movimento.ate) }}</dt>
            <dd class="num">{{ valorInformado === null ? '—' : reais(valorInformado) }}</dd>
          </div>
          <div>
            <dt>+ Contas pagas de 1º a {{ diaMes(movimento.ate) }}</dt>
            <dd class="num">{{ reais(movimento.pago) }}</dd>
          </div>
          <div>
            <dt>− Receitas recebidas de 1º a {{ diaMes(movimento.ate) }}</dt>
            <dd class="num">{{ reais(movimento.recebido) }}</dd>
          </div>
          <div class="total">
            <dt>= Saldo no dia 1º de {{ nomeMes }}</dt>
            <dd class="num">{{ saldoDiaPrimeiro === null ? '—' : reais(saldoDiaPrimeiro) }}</dd>
          </div>
        </dl>
        <p class="ajuda">
          A conta usa o que já está lançado no Familiança. Ainda vão lançar uma conta paga ou uma receita desse
          período? Podem lançar depois: o saldo do dia 1º se refaz sozinho.
        </p>
        <p v-if="sessao.tem('INVESTIMENTOS')" class="ajuda">
          Aplicaram em investimentos nesse período? Somem o valor aplicado ao saldo do dia, porque a aplicação já
          é descontada no fluxo do mês.
        </p>
      </section>

      <p v-if="diferenca !== null && Math.abs(diferenca) > 0.004" class="diferenca" aria-live="polite">
        Diferença para o calculado: <strong class="num">{{ sinal(diferenca) }}</strong>. Passa a valer de
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
  grid-template-columns: minmax(0, 1fr) minmax(8.5rem, 11rem);
  align-items: center;
  gap: var(--e-3);
  padding: var(--e-2) 0;
  border-bottom: 1px solid var(--linha);
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
