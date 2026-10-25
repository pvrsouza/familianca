<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Copy, MessageCircle, Pencil, Plus, Trash2 } from 'lucide-vue-next'
import { familia as api, lugaresSaldo } from '../api/servicos'
import type { Convite, Estimativa, LugarSaldo, Membro, Papel } from '../api/tipos'
import { mensagemDeErro } from '../api/http'
import { sessao } from '../estado/sessao'
import { catalogo } from '../estado/catalogo'
import { avisar } from '../estado/avisos'
import { ESTIMATIVAS, ORDEM_ESTIMATIVAS, TIPOS_LUGAR_SALDO, dataCurta, dataPorExtenso, primeiroNome } from '../util/formato'
import Mensagem from '../componentes/Mensagem.vue'
import PainelLateral from '../componentes/PainelLateral.vue'
import CampoSenha from '../componentes/CampoSenha.vue'
import FormLugarSaldo from '../componentes/FormLugarSaldo.vue'

const router = useRouter()
const f = computed(() => sessao.estado.familia)
const dono = computed(() => sessao.dono.value)
const eu = computed(() => sessao.estado.usuario?.membroId)
const ativos = computed(() => f.value?.membros.filter((m) => m.ativo) ?? [])
const inativos = computed(() => f.value?.membros.filter((m) => !m.ativo) ?? [])
const ocupadas = computed(() => ativos.value.length + (f.value?.convitesPendentes.length ?? 0))
const limite = computed(() => f.value?.planoEmVigor.limiteMembros ?? 0)
const cheio = computed(() => ocupadas.value >= limite.value)

const PAPEIS: Record<Papel, string> = { DONO: 'organiza', MEMBRO: 'participa' }
const RECURSOS: Record<string, string> = { INVESTIMENTOS: 'Investimentos', DESPESAS_PJ: 'Despesas da empresa (PJ)' }

const statusPlano = computed(() => {
  const a = f.value?.assinatura
  if (!a) return ''
  if (a.status === 'TESTE') return a.validaAte ? `Em teste até ${dataPorExtenso(a.validaAte, true)}` : 'Em teste'
  if (a.status === 'ATIVA') return a.validaAte ? `Ativo até ${dataPorExtenso(a.validaAte, true)}` : 'Ativo'
  return 'Cancelado'
})

// ——— Onde está o dinheiro ———
// Os lugares do saldo (conta, poupança, cofrinho...). O saldo de cada um se informa no fluxo de caixa; aqui se
// renomeia, troca o dono, desativa (some dos meses novos, fica nos antigos) ou exclui o que nunca entrou num saldo.
const lugares = ref<LugarSaldo[]>([])
const lugarEmEdicao = ref<number | 'novo' | null>(null)
const erroLugar = ref('')

onMounted(async () => {
  try {
    lugares.value = await lugaresSaldo.listar()
  } catch (e) {
    erroLugar.value = mensagemDeErro(e)
  }
})

const nomeMembro = (id: number | null) => f.value?.membros.find((m) => m.id === id)?.nome ?? null

function lugarSalvo(l: LugarSaldo) {
  const existe = lugares.value.some((x) => x.id === l.id)
  lugares.value = existe ? lugares.value.map((x) => (x.id === l.id ? l : x)) : [...lugares.value, l]
  lugarEmEdicao.value = null
  avisar(existe ? `${l.nome} atualizado.` : `${l.nome} incluído. O saldo dele se informa no fluxo de caixa.`)
}

async function alternarLugar(l: LugarSaldo) {
  erroLugar.value = ''
  try {
    lugarSalvo(await lugaresSaldo.atualizar(l.id, { nome: l.nome, tipo: l.tipo, membroId: l.membroId, ativo: !l.ativo }))
  } catch (e) {
    erroLugar.value = mensagemDeErro(e)
  }
}

async function excluirLugar(l: LugarSaldo) {
  erroLugar.value = ''
  try {
    await lugaresSaldo.remover(l.id)
    lugares.value = lugares.value.filter((x) => x.id !== l.id)
    avisar(`${l.nome} excluído.`)
  } catch (e) {
    erroLugar.value = mensagemDeErro(e)
  }
}

// ——— Nome e meta ———
const editandoNome = ref(false)
const nome = ref('')
const meta = ref<number | null>(null)
const regraAporte = ref<Estimativa>('MEDIA_3M')
const erroNome = ref('')
const salvandoNome = ref(false)

function editarNome() {
  nome.value = f.value?.nome ?? ''
  meta.value = f.value?.metaAportePercentual ?? null
  regraAporte.value = f.value?.estimativaAporte ?? 'MEDIA_3M'
  erroNome.value = ''
  editandoNome.value = true
}

async function salvarNome() {
  if (!nome.value.trim()) {
    erroNome.value = 'A família precisa de um nome.'
    return
  }
  if (meta.value !== null && (meta.value < 0 || meta.value > 100)) {
    erroNome.value = 'A meta vai de 0% a 100%.'
    return
  }
  salvandoNome.value = true
  try {
    sessao.estado.familia = await api.atualizar(nome.value.trim(), meta.value)
    if (regraAporte.value !== sessao.estado.familia.estimativaAporte) {
      sessao.estado.familia = await api.definirEstimativaAporte(regraAporte.value)
    }
    sessao.avisarMudanca()
    editandoNome.value = false
    avisar('Dados da família atualizados.')
  } catch (e) {
    erroNome.value = mensagemDeErro(e)
  } finally {
    salvandoNome.value = false
  }
}

// ——— Membros ———
const confirmarRemocao = ref<number | null>(null)
const erroMembro = ref('')

async function trocarPapel(m: Membro) {
  erroMembro.value = ''
  try {
    await api.alterarPapel(m.id, m.papel === 'DONO' ? 'MEMBRO' : 'DONO')
    await sessao.recarregarFamilia()
    avisar(`${primeiroNome(m.nome)} agora ${m.papel === 'DONO' ? 'participa' : 'também organiza'}. Para valer, a pessoa entra de novo no próximo acesso.`)
  } catch (e) {
    erroMembro.value = mensagemDeErro(e)
  }
}

async function remover(m: Membro) {
  erroMembro.value = ''
  try {
    await api.remover(m.id)
    confirmarRemocao.value = null
    await sessao.recarregarFamilia()
    avisar(`${primeiroNome(m.nome)} saiu da família. Os lançamentos continuam no histórico.`)
  } catch (e) {
    erroMembro.value = mensagemDeErro(e)
  }
}

// ——— Convites ———
const email = ref('')
const papel = ref<Papel>('MEMBRO')
const convidando = ref(false)
const erroConvite = ref('')
const criado = ref<Convite | null>(null)

async function convidar() {
  erroConvite.value = ''
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())) {
    erroConvite.value = 'Confira o e-mail da pessoa.'
    return
  }
  convidando.value = true
  try {
    criado.value = await api.convidar(email.value.trim(), papel.value)
    email.value = ''
    papel.value = 'MEMBRO'
    await sessao.recarregarFamilia()
  } catch (e) {
    erroConvite.value = mensagemDeErro(e)
  } finally {
    convidando.value = false
  }
}

const mensagemConvite = computed(() =>
  criado.value?.link
    ? `Oi! Quero você no orçamento da ${f.value?.nome} no Familiança, para a gente ver o ano da casa juntos. ` +
      `Crie sua conta por este link (vale 7 dias): ${criado.value.link}`
    : '',
)
const linkWhatsapp = computed(() => `https://wa.me/?text=${encodeURIComponent(mensagemConvite.value)}`)

async function copiar() {
  if (!criado.value?.link) return
  try {
    await navigator.clipboard.writeText(criado.value.link)
    avisar('Link copiado.')
  } catch {
    avisar('Não deu para copiar. Selecione o link e copie à mão.')
  }
}

async function cancelarConvite(c: Convite) {
  erroConvite.value = ''
  try {
    await api.cancelarConvite(c.id)
    if (criado.value?.id === c.id) criado.value = null
    await sessao.recarregarFamilia()
    avisar('Convite cancelado. O link não vale mais.')
  } catch (e) {
    erroConvite.value = mensagemDeErro(e)
  }
}

// ——— Apagar os dados ———
// Sem volta: pede o nome da família digitado (contra o clique sem querer) e a senha de quem organiza.
const apagando = ref(false)
const nomeConfirmado = ref('')
const senhaApagar = ref('')
const erroApagar = ref('')
const enviandoApagar = ref(false)
const nomeConfere = computed(() =>
  !!f.value && nomeConfirmado.value.trim().toLowerCase() === f.value.nome.trim().toLowerCase(),
)

function abrirApagar() {
  nomeConfirmado.value = ''
  senhaApagar.value = ''
  erroApagar.value = ''
  apagando.value = true
}

async function apagarDados() {
  erroApagar.value = ''
  if (!nomeConfere.value) {
    erroApagar.value = 'Digite o nome da família exatamente como aparece acima.'
    return
  }
  if (!senhaApagar.value) {
    erroApagar.value = 'Informe a sua senha.'
    return
  }
  enviandoApagar.value = true
  try {
    await api.apagarDados(senhaApagar.value)
    apagando.value = false
    await Promise.all([catalogo.carregar(true), sessao.recarregarFamilia()]) // sem saldo inicial de novo
    sessao.avisarMudanca()
    avisar('Os dados da família foram apagados. As categorias voltaram ao padrão. Recomecem pelo saldo de hoje.')
    router.push({ path: '/ano', query: { bemvindo: '1' } })
  } catch (e) {
    erroApagar.value = mensagemDeErro(e)
  } finally {
    senhaApagar.value = ''
    enviandoApagar.value = false
  }
}
</script>

<template>
  <div v-if="f" class="pagina">
    <header class="pagina-cabecalho">
      <div>
        <h1>{{ f.nome }}</h1>
        <p>Todos da família veem o mesmo quadro. Cada pessoa lança o que é seu; quem organiza cuida das categorias, dos convites e do plano.</p>
      </div>
      <button v-if="dono && !editandoNome" type="button" class="botao" @click="editarNome">
        <Pencil aria-hidden="true" /> Nome e meta
      </button>
    </header>

    <form v-if="editandoNome" class="painel painel-corpo formulario editar-familia" novalidate @submit.prevent="salvarNome">
      <div class="grade-campos duas">
        <div class="campo">
          <label for="f-nome">Nome da família</label>
          <input id="f-nome" v-model="nome" class="entrada" maxlength="100" required />
        </div>
        <div class="campo">
          <label for="f-regra-aporte">Meta de aporte de cada mês</label>
          <select id="f-regra-aporte" v-model="regraAporte" class="entrada">
            <option v-for="e in ORDEM_ESTIMATIVAS" :key="e" :value="e">{{ ESTIMATIVAS[e].rotulo }}</option>
          </select>
          <span class="ajuda">Como estimar o % do que sobra que vocês querem investir, a partir do que de fato investiram
            nos meses anteriores. Cada mês dá para mudar no fluxo de caixa.</span>
        </div>
        <div class="campo">
          <label for="f-meta">Meta de partida <span class="texto-2">(opcional)</span></label>
          <div class="entrada-pct">
            <input id="f-meta" v-model.number="meta" class="entrada num" type="number" min="0" max="100" step="1"
                   inputmode="decimal" placeholder="Ex.: 20" />
          </div>
          <span class="ajuda">% usado enquanto ainda não há meses para estimar (ou com "Não estimar").</span>
        </div>
      </div>
      <Mensagem v-if="erroNome" tipo="erro">{{ erroNome }}</Mensagem>
      <div class="acoes">
        <button type="submit" class="botao botao-primario" :disabled="salvandoNome">Salvar</button>
        <button type="button" class="botao" @click="editandoNome = false">Cancelar</button>
      </div>
    </form>

    <div class="colunas">
      <div class="principal">
        <section class="secao">
          <div class="secao-cabecalho">
            <h2>Pessoas</h2>
            <p class="num">{{ ocupadas }} de {{ limite }} no plano {{ f.planoEmVigor.rotulo }}</p>
          </div>
          <Mensagem v-if="erroMembro" tipo="erro">{{ erroMembro }}</Mensagem>
          <ul class="lista painel">
            <li v-for="m in ativos" :key="m.id" class="membro">
              <span class="avatar" aria-hidden="true">{{ m.nome.charAt(0) }}</span>
              <span class="quem">
                <span class="nome">{{ m.nome }}<template v-if="m.id === eu"> (você)</template></span>
                <span class="email">{{ m.email }}</span>
              </span>
              <span class="selo" :class="{ 'selo-petroleo': m.papel === 'DONO' }">{{ PAPEIS[m.papel] }}</span>
              <div v-if="dono && m.id !== eu" class="acoes acoes-membro">
                <template v-if="confirmarRemocao === m.id">
                  <span class="confirmar-texto">Tirar {{ primeiroNome(m.nome) }} da família?</span>
                  <button type="button" class="botao botao-perigo botao-pequeno" @click="remover(m)">Tirar</button>
                  <button type="button" class="botao botao-pequeno" @click="confirmarRemocao = null">Manter</button>
                </template>
                <template v-else>
                  <button type="button" class="botao botao-sutil botao-pequeno" @click="trocarPapel(m)">
                    {{ m.papel === 'DONO' ? 'Passar a participar' : 'Também organiza' }}
                  </button>
                  <button type="button" class="botao botao-sutil botao-pequeno" @click="confirmarRemocao = m.id">Tirar da família</button>
                </template>
              </div>
            </li>
          </ul>
          <p v-if="inativos.length" class="inativos texto-2">
            Saíram da família: {{ inativos.map((m) => m.nome).join(', ') }}. Os lançamentos deles continuam no histórico.
          </p>
        </section>

        <section class="secao">
          <div class="secao-cabecalho">
            <h2>Onde está o dinheiro</h2>
            <p>Contas, poupança, dinheiro na mão, cofrinho. Servem para informar o saldo inicial lugar a lugar, no
              fluxo de caixa.</p>
          </div>
          <Mensagem v-if="erroLugar" tipo="erro">{{ erroLugar }}</Mensagem>
          <ul v-if="lugares.length" class="lista painel">
            <li v-for="l in lugares" :key="l.id" class="lugar" :class="{ inativo: !l.ativo }">
              <FormLugarSaldo v-if="lugarEmEdicao === l.id" :lugar="l" :id-base="`lugar-${l.id}`" class="editar-lugar"
                              @salvo="lugarSalvo" @cancelar="lugarEmEdicao = null" />
              <template v-else>
                <span class="quem">
                  <span class="nome">{{ l.nome }}</span>
                  <span class="email">
                    {{ TIPOS_LUGAR_SALDO[l.tipo].rotulo }} · {{ nomeMembro(l.membroId) ?? 'da família toda' }}
                  </span>
                </span>
                <span v-if="!l.ativo" class="selo">desativado</span>
                <div v-if="dono" class="acoes acoes-membro">
                  <button type="button" class="botao botao-sutil botao-pequeno" @click="lugarEmEdicao = l.id">
                    <Pencil aria-hidden="true" /> Editar
                  </button>
                  <button type="button" class="botao botao-sutil botao-pequeno" @click="alternarLugar(l)"
                          :title="l.ativo ? 'Deixa de aparecer nos meses novos; os saldos antigos continuam iguais.' : undefined">
                    {{ l.ativo ? 'Desativar' : 'Reativar' }}
                  </button>
                  <button type="button" class="botao botao-sutil botao-pequeno" :aria-label="`Excluir ${l.nome}`"
                          title="Só dá para excluir o que nunca entrou num saldo; o resto, desativem."
                          @click="excluirLugar(l)">
                    <Trash2 aria-hidden="true" />
                  </button>
                </div>
              </template>
            </li>
          </ul>
          <p v-else-if="lugarEmEdicao !== 'novo'" class="texto-2 vazio-lugares">
            Nenhum lugar ainda. Cadastrem aqui ou direto no saldo inicial, no fluxo de caixa.
          </p>
          <FormLugarSaldo v-if="lugarEmEdicao === 'novo'" id-base="lugar-novo" @salvo="lugarSalvo"
                          @cancelar="lugarEmEdicao = null" />
          <button v-else-if="dono" type="button" class="botao botao-sutil botao-pequeno incluir-lugar"
                  @click="lugarEmEdicao = 'novo'">
            <Plus aria-hidden="true" /> Incluir lugar
          </button>
        </section>

        <section v-if="f.convitesPendentes.length" class="secao">
          <h2>Convites esperando resposta</h2>
          <ul class="lista painel">
            <li v-for="c in f.convitesPendentes" :key="c.id">
              <span class="quem">
                <span class="nome">{{ c.email }}</span>
                <span class="email">{{ PAPEIS[c.papel] }} · vale até {{ dataCurta(c.expiraEm) }}</span>
              </span>
              <button v-if="dono" type="button" class="botao botao-sutil botao-pequeno" @click="cancelarConvite(c)">Cancelar</button>
            </li>
          </ul>
        </section>
      </div>

      <aside class="lateral">
        <section v-if="dono" class="painel painel-corpo convidar">
          <h2>Convidar alguém</h2>
          <template v-if="criado?.link">
            <p class="texto-2">Mande este link para <strong>{{ criado.email }}</strong>. Ele vale uma vez, por 7 dias, e só aparece agora.</p>
            <input class="entrada link" :value="criado.link" readonly aria-label="Link do convite" @focus="($event.target as HTMLInputElement).select()" />
            <div class="acoes">
              <a :href="linkWhatsapp" target="_blank" rel="noopener noreferrer" class="botao botao-primario">
                <MessageCircle aria-hidden="true" /> Enviar pelo WhatsApp
              </a>
              <button type="button" class="botao" @click="copiar"><Copy aria-hidden="true" /> Copiar link</button>
            </div>
            <button type="button" class="botao botao-sutil botao-pequeno" @click="criado = null">Convidar outra pessoa</button>
          </template>
          <form v-else class="formulario" novalidate @submit.prevent="convidar">
            <Mensagem v-if="cheio">
              A família chegou ao limite de {{ limite }} pessoas do plano {{ f.planoEmVigor.rotulo }}. Cancele um convite
              ou tire alguém para convidar outra pessoa.
            </Mensagem>
            <div class="campo">
              <label for="c-email">E-mail da pessoa</label>
              <input id="c-email" v-model="email" class="entrada" type="email" inputmode="email" maxlength="200"
                     :disabled="cheio" />
            </div>
            <div class="campo">
              <span class="rotulo">Ela vai</span>
              <div class="segmentos" role="group" aria-label="Papel">
                <button type="button" :aria-pressed="papel === 'MEMBRO'" :disabled="cheio" @click="papel = 'MEMBRO'">Participar</button>
                <button type="button" :aria-pressed="papel === 'DONO'" :disabled="cheio" @click="papel = 'DONO'">Organizar também</button>
              </div>
              <span class="ajuda">
                {{ papel === 'MEMBRO' ? 'Lança e altera os próprios gastos e vê o orçamento inteiro.' : 'Pode tudo: categorias, convites, saldos e os lançamentos de todos.' }}
              </span>
            </div>
            <Mensagem v-if="erroConvite" tipo="erro">{{ erroConvite }}</Mensagem>
            <button type="submit" class="botao botao-primario" :disabled="convidando || cheio">
              {{ convidando ? 'Gerando o link…' : 'Gerar link do convite' }}
            </button>
          </form>
        </section>

        <section class="painel painel-corpo plano">
          <h2>Plano {{ f.planoEmVigor.rotulo }}</h2>
          <p class="texto-2">{{ statusPlano }}</p>
          <ul class="recursos">
            <li>Orçamento, recorrências, visão do ano, fluxo de caixa, por pessoa e quanto pesa</li>
            <li v-for="r in f.planoEmVigor.recursos" :key="r">{{ RECURSOS[r] ?? r }}</li>
            <li>Até {{ limite }} pessoas</li>
          </ul>
          <p v-if="f.assinatura.status === 'TESTE'" class="ajuda-plano">
            Depois do teste, a família volta ao plano Gratuito se não assinar. Nada se perde: o que for de outro plano
            só fica fechado.
          </p>
        </section>

        <section v-if="dono" class="painel painel-corpo apagar">
          <h2>Recomeçar do zero</h2>
          <p class="texto-2">
            Apaga de vez todo o orçamento da família: lançamentos, recorrências, saldos e lugares do dinheiro,
            investimentos e as categorias criadas por vocês. Serve para recomeçar ou para ter certeza de que os dados
            saíram daqui.
          </p>
          <button type="button" class="botao botao-perigo" @click="abrirApagar">
            <Trash2 aria-hidden="true" /> Apagar todos os dados
          </button>
        </section>
      </aside>
    </div>

    <PainelLateral :aberto="apagando" titulo="Apagar todos os dados"
                   descricao="Não tem como desfazer. Confira antes de seguir." @fechar="apagando = false">
      <form class="formulario" novalidate @submit.prevent="apagarDados">
        <div class="o-que-sai">
          <h3>Sai, para todos da família</h3>
          <ul>
            <li>Todos os lançamentos de todos os anos, inclusive parcelas futuras</li>
            <li>Todas as recorrências e os meses confirmados ou pulados</li>
            <li>Saldos em conta informados e investimentos com os movimentos</li>
            <li>Grupos e categorias criados ou alterados: voltam as categorias padrão de família nova</li>
          </ul>
          <h3>Fica</h3>
          <ul>
            <li>A família, as pessoas, os convites e o plano</li>
            <li>As contas e senhas de cada um</li>
          </ul>
        </div>
        <div class="campo">
          <label for="apagar-nome">Para confirmar, digite o nome da família: <strong>{{ f.nome }}</strong></label>
          <input id="apagar-nome" v-model="nomeConfirmado" class="entrada" maxlength="100" autocomplete="off"
                 spellcheck="false" />
        </div>
        <div class="campo">
          <label for="apagar-senha">Sua senha</label>
          <CampoSenha id="apagar-senha" v-model="senhaApagar" autocomplete="current-password" />
        </div>
        <Mensagem v-if="erroApagar" tipo="erro">{{ erroApagar }}</Mensagem>
        <div class="acoes rodape-apagar">
          <button type="button" class="botao" @click="apagando = false">Manter os dados</button>
          <button type="submit" class="botao botao-perigo" :disabled="enviandoApagar || !nomeConfere || !senhaApagar">
            {{ enviandoApagar ? 'Apagando…' : 'Apagar tudo' }}
          </button>
        </div>
      </form>
    </PainelLateral>
  </div>
</template>

<style scoped>
.editar-familia {
  margin-bottom: var(--e-6);
  max-width: 48rem;
}

.colunas {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(18rem, 24rem);
  gap: var(--e-8);
  align-items: start;
}

.principal .secao:first-child {
  margin-top: 0;
}

.lateral {
  display: grid;
  gap: var(--e-5);
}

.membro,
.lugar {
  flex-wrap: wrap;
}

.avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: 50%;
  background: var(--petroleo-tinta);
  color: var(--petroleo-escuro);
  font-weight: 700;
  text-transform: uppercase;
}

.quem {
  display: grid;
  flex: 1;
  min-width: 10rem;
}

.nome {
  font-weight: 600;
  overflow-wrap: anywhere;
}

.email {
  font-size: var(--t-xs);
  color: var(--texto-2);
  overflow-wrap: anywhere;
}

.acoes-membro {
  margin-left: auto;
}

.confirmar-texto {
  font-size: var(--t-sm);
  font-weight: 600;
}

.inativos {
  margin-top: var(--e-3);
  font-size: var(--t-sm);
}

.convidar h2,
.plano h2 {
  margin-bottom: var(--e-3);
}

.convidar .formulario {
  gap: var(--e-4);
}

.convidar > p {
  font-size: var(--t-sm);
  margin-bottom: var(--e-3);
}

.link {
  margin-bottom: var(--e-3);
  font-size: var(--t-sm);
  color: var(--petroleo-escuro);
}

.convidar .acoes + .botao {
  margin-top: var(--e-3);
}

.segmentos {
  display: flex;
}

.segmentos button {
  flex: 1;
  justify-content: center;
}

.recursos {
  margin: var(--e-3) 0 0;
  padding-left: var(--e-5);
  font-size: var(--t-sm);
  display: grid;
  gap: var(--e-1);
}

.recursos li::marker {
  color: var(--acao);
}

.ajuda-plano {
  margin-top: var(--e-4);
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.apagar h2 {
  margin-bottom: var(--e-2);
}

.apagar p {
  margin-bottom: var(--e-4);
  font-size: var(--t-sm);
}

.o-que-sai {
  padding: var(--e-4);
  border-radius: var(--raio);
  background: var(--erro-claro);
  font-size: var(--t-sm);
}

.o-que-sai h3 {
  font-size: var(--t-sm);
  font-weight: 700;
}

.o-que-sai ul {
  margin: var(--e-1) 0 var(--e-3);
  padding-left: var(--e-5);
  display: grid;
  gap: var(--e-1);
}

.o-que-sai ul:last-child {
  margin-bottom: 0;
}

.rodape-apagar {
  justify-content: flex-end;
}

@media (max-width: 960px) {
  .colunas {
    grid-template-columns: 1fr;
  }

}
.lugar.inativo .nome {
  color: var(--texto-2);
}

.editar-lugar {
  flex: 1;
}

.incluir-lugar {
  margin-top: var(--e-3);
}

.vazio-lugares {
  font-size: var(--t-sm);
}
</style>
