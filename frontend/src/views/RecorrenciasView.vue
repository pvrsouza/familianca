<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Plus, Undo2 } from 'lucide-vue-next'
import { recorrencias as api } from '../api/servicos'
import type { Pendente, Recorrencia, Tipo } from '../api/tipos'
import { ErroApi, mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import { avisar } from '../estado/avisos'
import { FREQUENCIAS, competenciaCurta, competenciaPorExtenso, previstoPelaRegra, reais } from '../util/formato'
import PainelLateral from '../componentes/PainelLateral.vue'
import FormRecorrencia from '../componentes/FormRecorrencia.vue'
import ConferirOcorrencia from '../componentes/ConferirOcorrencia.vue'
import EstadoVazio from '../componentes/EstadoVazio.vue'
import Mensagem from '../componentes/Mensagem.vue'

const route = useRoute()
const lista = ref<Recorrencia[]>([])
const pendentes = ref<Pendente[]>([])
const carregando = ref(true)
const erro = ref('')
const nova = ref(route.query.nova === '1')
const tipoNova = ref<Tipo>('DESPESA')
const editar = ref<Recorrencia | null>(null)

async function carregar() {
  erro.value = ''
  try {
    const [l, p] = await Promise.all([api.listar(), api.pendentes()])
    lista.value = l
    pendentes.value = p
  } catch (e) {
    erro.value = mensagemDeErro(e)
  } finally {
    carregando.value = false
  }
}

watch(() => sessao.estado.versaoDados, carregar, { immediate: true })

const despesas = computed(() => lista.value.filter((r) => r.tipo === 'DESPESA'))
const receitas = computed(() => lista.value.filter((r) => r.tipo === 'RECEITA'))
const pesoDespesas = computed(() => despesas.value.filter(ativa).reduce((s, r) => s + r.valorAnualizado, 0))
const anoRef = computed(() => lista.value[0]?.ano ?? new Date().getFullYear())

function ativa(r: Recorrencia) {
  return !r.fim || Number(r.fim.slice(0, 4)) >= anoRef.value
}

function podeAlterar(r: Recorrencia) {
  return sessao.dono.value || r.membro?.id === sessao.estado.usuario?.membroId
}

function periodo(r: Recorrencia): string {
  return r.fim ? `de ${competenciaCurta(r.inicio)} a ${competenciaCurta(r.fim)}` : `desde ${competenciaCurta(r.inicio)}`
}

function abrirNova(t: Tipo) {
  tipoNova.value = t
  nova.value = true
}

function salvo(msg: string) {
  nova.value = false
  editar.value = null
  avisar(msg)
}

// ——— Conferir ———
function chave(p: Pendente) {
  return `${p.recorrencia.id}-${p.competencia}`
}

const receitasPendentes = computed(() => pendentes.value.filter((p) => p.recorrencia.tipo === 'RECEITA'))
const contasPendentes = computed(() => pendentes.value.filter((p) => p.recorrencia.tipo === 'DESPESA'))

// ——— Excluir e desfazer pulo (no painel de edição) ———
const confirmandoExclusao = ref(false)
const erroEdicao = ref('')
const ocupado = ref(false)

function abrir(r: Recorrencia) {
  if (!podeAlterar(r)) return
  editar.value = r
  confirmandoExclusao.value = false
  erroEdicao.value = ''
}

async function excluir() {
  if (!editar.value) return
  ocupado.value = true
  erroEdicao.value = ''
  try {
    await api.remover(editar.value.id)
    editar.value = null
    sessao.avisarMudanca()
    avisar('Recorrência excluída.')
  } catch (e) {
    erroEdicao.value = e instanceof ErroApi && e.status === 409
      ? 'Ela já tem meses confirmados, que viraram lançamentos. Para parar daqui em diante, marque "Tem data para acabar" e escolha o último mês.'
      : mensagemDeErro(e)
    confirmandoExclusao.value = false
  } finally {
    ocupado.value = false
  }
}

async function desfazerPulo(c: string) {
  if (!editar.value) return
  ocupado.value = true
  try {
    editar.value = await api.desfazerPulo(editar.value.id, c)
    sessao.avisarMudanca()
    avisar(`${competenciaPorExtenso(c)} volta a ser previsto.`)
  } catch (e) {
    erroEdicao.value = mensagemDeErro(e)
  } finally {
    ocupado.value = false
  }
}
</script>

<template>
  <div class="pagina">
    <header class="pagina-cabecalho">
      <div>
        <h1>O que se repete</h1>
        <p>Salário, contas fixas, assinaturas. Cada uma aparece como prevista no ano até vocês confirmarem o valor real.</p>
      </div>
      <button type="button" class="botao botao-primario" @click="abrirNova('DESPESA')"><Plus aria-hidden="true" /> Nova recorrência</button>
    </header>

    <div v-if="carregando" class="painel" aria-busy="true">
      <div v-for="i in 4" :key="i" class="esqueleto linha-esqueleto" />
    </div>

    <Mensagem v-else-if="erro" tipo="erro">
      {{ erro }}
      <template #acoes><button type="button" class="botao botao-pequeno" @click="carregar">Tentar de novo</button></template>
    </Mensagem>

    <template v-else>
      <section v-if="pendentes.length" class="secao conferir">
        <div class="secao-cabecalho">
          <h2>Para conferir</h2>
          <p>O que já venceu e ainda está como previsto.</p>
        </div>
        <template v-for="g in [
          { titulo: 'Receitas: chegaram?', texto: 'Receita que se repete pode não vir, ou vir com outro valor. Só entra na renda do jeito que chegou.', itens: receitasPendentes },
          { titulo: 'Contas: confirmar o valor', texto: 'Confirme o valor que foi pago ou pule o mês se não houve.', itens: contasPendentes },
        ]" :key="g.titulo">
          <div v-if="g.itens.length" class="grupo-conferir">
            <h3>{{ g.titulo }}</h3>
            <p class="texto-2">{{ g.texto }}</p>
            <ul class="lista painel">
              <li v-for="p in g.itens" :key="chave(p)" class="pendente">
                <div class="item-texto">
                  <span class="descricao">{{ p.recorrencia.descricao }}</span>
                  <span class="meta">
                    {{ competenciaPorExtenso(p.competencia) }} · {{ p.recorrencia.categoria.nome }}<template v-if="p.recorrencia.membro"> · {{ p.recorrencia.membro.nome }}</template>
                  </span>
                </div>
                <ConferirOcorrencia :recorrencia="p.recorrencia" :competencia="p.competencia"
                                    :valor-previsto="p.valorPrevisto" @feito="avisar" />
              </li>
            </ul>
          </div>
        </template>
      </section>

      <div v-if="!lista.length" class="painel">
        <EstadoVazio titulo="Nada que se repete ainda">
          Comece pelo salário e pelas contas de todo mês. Com elas, o ano inteiro já aparece desde janeiro.
          <template #acoes>
            <button type="button" class="botao botao-primario" @click="abrirNova('RECEITA')">Cadastrar uma receita</button>
            <button type="button" class="botao" @click="abrirNova('DESPESA')">Cadastrar uma despesa</button>
          </template>
        </EstadoVazio>
      </div>

      <template v-else>
        <section v-for="bloco in [{ titulo: 'Despesas', itens: despesas, tipo: 'DESPESA' as Tipo }, { titulo: 'Receitas', itens: receitas, tipo: 'RECEITA' as Tipo }]"
                 :key="bloco.titulo" class="secao">
          <div class="secao-cabecalho">
            <h2>{{ bloco.titulo }}</h2>
            <p v-if="bloco.tipo === 'DESPESA' && pesoDespesas > 0">
              Juntas pesam <strong class="num">{{ reais(pesoDespesas) }}</strong> num ano cheio.
            </p>
          </div>
          <p v-if="!bloco.itens.length" class="texto-2 vazio-bloco">
            Nenhuma {{ bloco.tipo === 'DESPESA' ? 'despesa' : 'receita' }} que se repete.
            <button type="button" class="botao botao-sutil botao-pequeno" @click="abrirNova(bloco.tipo)">Cadastrar</button>
          </p>
          <ul v-else class="lista painel">
            <li v-for="r in bloco.itens" :key="r.id">
              <component :is="podeAlterar(r) ? 'button' : 'div'" :type="podeAlterar(r) ? 'button' : undefined"
                         class="item" :class="{ clicavel: podeAlterar(r), encerrada: !ativa(r) }" @click="abrir(r)">
                <span class="item-texto">
                  <span class="descricao">{{ r.descricao }}<span v-if="r.confirmarSozinha" class="selo selo-sozinha">confirma sozinha</span><span v-if="r.puladas.length" class="selo">{{ r.puladas.length === 1 ? `${competenciaCurta(r.puladas[0])} pulado` : `${r.puladas.length} meses pulados` }}</span></span>
                  <span class="meta">
                    {{ r.categoria.nome }} · {{ FREQUENCIAS[r.frequencia].rotulo.toLowerCase() }}, dia {{ r.dia }} ·
                    {{ periodo(r) }}<template v-if="r.membro"> · {{ r.membro.nome }}</template>
                  </span>
                </span>
                <span class="peso num">
                  <span class="peso-linha" :class="{ receita: r.tipo === 'RECEITA' }">
                    {{ reais(r.valor) }} {{ FREQUENCIAS[r.frequencia].por }}
                  </span>
                  <span class="peso-ano">= {{ reais(r.valorAnualizado) }} por ano<template v-if="r.valorNoAno !== r.valorAnualizado"> · {{ reais(r.valorNoAno) }} em {{ r.ano }}</template></span>
                  <span v-if="r.previstoPor" class="peso-ano previsto-regra">
                    previsto <span class="selo selo-previsto">{{ reais(r.valorPrevisto) }}</span> {{ previstoPelaRegra(r.previstoPor, r.confirmacoesNaPrevisao) }}
                  </span>
                </span>
              </component>
            </li>
          </ul>
        </section>
      </template>
    </template>

    <PainelLateral :aberto="nova" titulo="Nova recorrência" descricao="Algo que se repete. Aparece como previsto em todos os meses."
                   @fechar="nova = false">
      <FormRecorrencia v-if="nova" :tipo-inicial="tipoNova" @salvo="salvo" @cancelar="nova = false" />
    </PainelLateral>

    <PainelLateral :aberto="!!editar" titulo="Editar recorrência" @fechar="editar = null">
      <template v-if="editar">
        <!-- No topo: quem pulou um mês sem querer encontra o "voltar" sem rolar o formulário inteiro. -->
        <section v-if="editar.puladas.length" class="puladas">
          <h3>Meses pulados</h3>
          <p class="texto-2">Ficaram fora do ano, marcados como "não houve". Voltar a prever devolve o valor ao mês.</p>
          <ul class="lista">
            <li v-for="c in editar.puladas" :key="c">
              <span class="item-texto">{{ competenciaPorExtenso(c) }}</span>
              <button type="button" class="botao botao-pequeno" :disabled="ocupado" @click="desfazerPulo(c)">
                <Undo2 aria-hidden="true" /> Voltar a prever
              </button>
            </li>
          </ul>
        </section>

        <FormRecorrencia :key="editar.id" :recorrencia="editar" @salvo="salvo" @cancelar="editar = null" />

        <div class="zona-exclusao">
          <button v-if="!confirmandoExclusao" type="button" class="botao botao-perigo" @click="confirmandoExclusao = true">
            Excluir recorrência
          </button>
          <div v-else class="confirmar">
            <p>Excluir "{{ editar.descricao }}"? Os meses previstos somem do ano.</p>
            <div class="acoes">
              <button type="button" class="botao botao-perigo" :disabled="ocupado" @click="excluir">Excluir</button>
              <button type="button" class="botao" @click="confirmandoExclusao = false">Manter</button>
            </div>
          </div>
          <Mensagem v-if="erroEdicao" tipo="erro">{{ erroEdicao }}</Mensagem>
        </div>
      </template>
    </PainelLateral>
  </div>
</template>

<style scoped>
.linha-esqueleto {
  height: 3rem;
  margin: var(--e-3) var(--e-4);
}

.secao:first-child,
.conferir {
  margin-top: 0;
}

.conferir .lista {
  border-color: color-mix(in srgb, var(--coral-borda) 35%, var(--pedra));
}

.pendente {
  flex-wrap: wrap;
  align-items: flex-start;
  gap: var(--e-3) var(--e-6);
}

.grupo-conferir + .grupo-conferir {
  margin-top: var(--e-6);
}

.grupo-conferir h3 {
  margin-bottom: var(--e-1);
}

.grupo-conferir > .texto-2 {
  max-width: 60ch;
  margin-bottom: var(--e-3);
  font-size: var(--t-sm);
}

.item-texto {
  display: grid;
  flex: 1;
  min-width: 12rem;
}

.descricao {
  font-weight: 600;
  overflow-wrap: anywhere;
}

.meta {
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.lista > li:has(.item) {
  padding: 0;
}

.item {
  display: flex;
  align-items: center;
  gap: var(--e-4);
  width: 100%;
  min-height: 4rem;
  padding: var(--e-3) var(--e-4);
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

.selo-sozinha {
  margin-left: var(--e-2);
  vertical-align: 0.1em;
}

.encerrada .descricao,
.encerrada .peso {
  color: var(--texto-2);
}

.peso {
  display: grid;
  justify-items: end;
  text-align: right;
}

.peso-linha {
  font-weight: 700;
  white-space: nowrap;
}

.previsto-regra {
  display: block;
  margin-top: var(--e-1);
}

.peso-ano {
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.vazio-bloco {
  display: flex;
  align-items: center;
  gap: var(--e-2);
  font-size: var(--t-sm);
}

.puladas {
  display: grid;
  gap: var(--e-2);
  margin-bottom: var(--e-6);
  padding: var(--e-4);
  border-radius: var(--raio);
  background: var(--areia);
}

.puladas > .texto-2 {
  font-size: var(--t-sm);
}

.puladas .lista {
  background: var(--papel);
}

.puladas .lista {
  border: 1px solid var(--linha);
  border-radius: var(--raio);
}

.zona-exclusao {
  display: grid;
  gap: var(--e-3);
  margin-top: var(--e-8);
  padding-top: var(--e-5);
  border-top: 1px solid var(--linha);
}

.confirmar {
  display: grid;
  gap: var(--e-3);
  font-size: var(--t-sm);
}

@media (max-width: 640px) {
  .item {
    flex-wrap: wrap;
    gap: var(--e-1);
  }

  .peso {
    justify-items: start;
    text-align: left;
  }

  .pendente .acoes {
    width: 100%;
  }

  .pendente .acoes .botao {
    flex: 1;
  }
}
</style>
