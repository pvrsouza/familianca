// Números e datas no jeito brasileiro. Tudo com algarismos tabulares no CSS (font-variant-numeric).
import { privacidade } from '../estado/privacidade'
import { centavos } from '../estado/centavos'

/** Valor escondido pelo olhinho da barra. Ler `ocultos` aqui faz a tela redesenhar ao alternar. */
const OCULTO = '•••••'

const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const inteiro = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 })
const decimal1 = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
const decimal2 = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** R$ 1.234,56 */
export function reais(v: number | null | undefined): string {
  if (privacidade.ocultos.value) return `R$ ${OCULTO}`
  return moeda.format(v ?? 0)
}

/** 1.235 para as tabelas do ano, ou 1.234,56 se a pessoa ligou os centavos. Zero vira traço. */
export function reaisCurtos(v: number | null | undefined): string {
  if (!v) return '–'
  if (privacidade.ocultos.value) return OCULTO
  return centavos.ligados.value ? decimal2.format(v) : inteiro.format(Math.round(v))
}

/** "3º" para posições (rank). */
export function ordinal(n: number): string {
  return `${n}º`
}

/** 1.234,56 sem o símbolo (campos de valor). */
export function numeroBr(v: number | null | undefined): string {
  return v == null ? '' : decimal2.format(v)
}

/** 12,3% (nulo = traço). */
export function pct(v: number | null | undefined, casas = 1): string {
  if (v == null) return '–'
  return (casas === 1 ? decimal1 : decimal2).format(v) + '%'
}

/** +4,8% / −1,2% para variações. */
export function variacao(v: number | null | undefined): string {
  if (v == null) return '–'
  const s = decimal1.format(Math.abs(v)) + '%'
  return v > 0 ? `+${s}` : v < 0 ? `−${s}` : s
}

/** "1.234,56", "1234.56", "1234,5" → 1234.56. Inválido = null. */
export function lerValor(texto: string): number | null {
  const limpo = texto.replace(/[R$\s]/g, '')
  if (!limpo) return null
  const normal = limpo.includes(',') ? limpo.replace(/\./g, '').replace(',', '.') : limpo
  const n = Number(normal)
  return Number.isFinite(n) ? Math.round(n * 100) / 100 : null
}

export const MESES = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro',
  'outubro', 'novembro', 'dezembro']
export const MESES_CURTOS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

/** "2026-03" → "março de 2026" */
export function competenciaPorExtenso(c: string): string {
  const [ano, mes] = c.split('-').map(Number)
  return `${MESES[mes - 1]} de ${ano}`
}

/** "2026-03" → "mar/26" */
export function competenciaCurta(c: string): string {
  const [ano, mes] = c.split('-').map(Number)
  return `${MESES_CURTOS[mes - 1]}/${String(ano).slice(2)}`
}

export function competencia(ano: number, mes: number): string {
  return `${ano}-${String(mes).padStart(2, '0')}`
}

/** "2026-03-15" → "15 de março" (ou "15/03/2026" no formato curto). */
export function dataPorExtenso(iso: string, comAno = false): string {
  const [ano, mes, dia] = iso.slice(0, 10).split('-').map(Number)
  return `${dia} de ${MESES[mes - 1]}${comAno ? ` de ${ano}` : ''}`
}

export function dataCurta(iso: string): string {
  const [ano, mes, dia] = iso.slice(0, 10).split('-')
  return `${dia}/${mes}/${ano}`
}

/** Data de hoje no fuso do navegador, em AAAA-MM-DD. */
export function hoje(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function anoAtual(): number {
  return new Date().getFullYear()
}

export function mesAtual(): number {
  return new Date().getMonth() + 1
}

export const FREQUENCIAS: Record<string, { rotulo: string; por: string; vezes: number }> = {
  UNICA: { rotulo: 'Uma vez só (conta avulsa)', por: 'uma vez', vezes: 1 },
  MENSAL: { rotulo: 'Todo mês', por: 'por mês', vezes: 12 },
  BIMESTRAL: { rotulo: 'A cada 2 meses', por: 'por bimestre', vezes: 6 },
  TRIMESTRAL: { rotulo: 'A cada 3 meses', por: 'por trimestre', vezes: 4 },
  SEMESTRAL: { rotulo: 'A cada 6 meses', por: 'por semestre', vezes: 2 },
  ANUAL: { rotulo: 'Uma vez por ano', por: 'por ano', vezes: 1 },
}

export const PERIODICIDADES: { valor: string; rotulo: string; por: string }[] = [
  { valor: 'UNICA', rotulo: 'Uma vez', por: 'uma vez' },
  { valor: 'DIARIA', rotulo: 'Todo dia', por: 'por dia' },
  { valor: 'DIAS_UTEIS', rotulo: 'Dia útil', por: 'por dia útil' },
  { valor: 'SEMANAL', rotulo: 'Semana', por: 'por semana' },
  { valor: 'QUINZENAL', rotulo: 'Quinzena', por: 'por quinzena' },
  { valor: 'MENSAL', rotulo: 'Mês', por: 'por mês' },
  { valor: 'BIMESTRAL', rotulo: 'Bimestre', por: 'por bimestre' },
  { valor: 'TRIMESTRAL', rotulo: 'Trimestre', por: 'por trimestre' },
  { valor: 'SEMESTRAL', rotulo: 'Semestre', por: 'por semestre' },
  { valor: 'ANUAL', rotulo: 'Ano', por: 'por ano' },
]

/** Como a despesa é paga: rótulo e o que acontece com ela, para mostrar ao escolher. */
export const FORMAS_PAGAMENTO: Record<string, { rotulo: string; explica: string }> = {
  BOLETO_PIX: {
    rotulo: 'Boleto ou Pix',
    explica: 'Alguém da família precisa pagar até o vencimento. É o tipo de conta que vai aparecer na lista de contas a pagar.',
  },
  DEBITO_AUTOMATICO: {
    rotulo: 'Débito automático',
    explica: 'Sai sozinho da conta no vencimento: ninguém precisa pagar, só conferir se o valor veio certo. Se o valor é sempre o mesmo, ligue "Confirmar sozinha no vencimento".',
  },
  CARTAO_CREDITO: {
    rotulo: 'No cartão de crédito',
    explica: 'É cobrada no cartão e entra na fatura, então não tem vencimento próprio: quem vence é a fatura. O dia abaixo só diz quando a cobrança costuma cair.',
  },
}

/** Vencimento de uma ocorrência (AAAA-MM-DD): o ajustado daquele mês ou o dia cadastrado; nulo = sem vencimento. */
export function vencimentoDe(r: { temVencimento: boolean; dia: number; vencimentosAjustados: { competencia: string; vencimento: string }[] },
  comp: string): string | null {
  if (!r.temVencimento) return null
  return r.vencimentosAjustados.find((v) => v.competencia === comp)?.vencimento ?? diaNoMes(comp, r.dia)
}

/** Dia do mês na competência; 31 vira o último dia nos meses curtos. */
export function diaNoMes(comp: string, dia: number): string {
  const [a, m] = comp.split('-').map(Number)
  const ultimo = new Date(a, m, 0).getDate()
  return `${comp}-${String(Math.min(dia, ultimo)).padStart(2, '0')}`
}

/** Dias entre duas datas AAAA-MM-DD (positivo quando "ate" é depois de "de"). */
export function diasEntre(de: string, ate: string): number {
  return Math.round((Date.parse(ate + 'T00:00:00Z') - Date.parse(de + 'T00:00:00Z')) / 86_400_000)
}

/** "Pago em dia" / "Pago com 3 dias de atraso"; nulo quando não há vencimento. */
export function situacaoPagamento(diasAtraso: number | null | undefined): string | null {
  if (diasAtraso == null) return null
  if (diasAtraso === 0) return 'Pago em dia'
  return `Pago com ${diasAtraso} ${diasAtraso === 1 ? 'dia' : 'dias'} de atraso`
}

/** Regras de estimativa dos próximos meses: rótulo curto e "como" (para frases). */
export const ESTIMATIVAS: Record<string, { rotulo: string; como: string }> = {
  MEDIA: { rotulo: 'Média', como: 'pela média dos meses que já passaram' },
  MAXIMO: { rotulo: 'Maior mês', como: 'pelo maior mês do ano até aqui' },
  MINIMO: { rotulo: 'Menor mês', como: 'pelo menor mês do ano até aqui' },
  NENHUMA: { rotulo: 'Não estimar', como: 'sem estimativa' },
}

/** "pela média de 3 confirmações" (recorrência de valor variável prevista pela regra). */
export function previstoPelaRegra(regra: string, confirmacoes: number): string {
  if (confirmacoes === 1) return 'pela única confirmação do ano'
  const qual = regra === 'MAXIMO' ? 'pela maior' : regra === 'MINIMO' ? 'pela menor' : 'pela média'
  return `${qual} das ${confirmacoes} confirmações do ano`
}

export const TIPOS_INVESTIMENTO: Record<string, string> = {
  RENDA_FIXA: 'Renda fixa', TESOURO: 'Tesouro Direto', POUPANCA: 'Poupança', ACOES: 'Ações', FUNDOS: 'Fundos',
  PREVIDENCIA: 'Previdência', CRIPTO: 'Cripto', OUTRO: 'Outro',
}

/** Onde está o dinheiro: rótulo, explicação curta e um exemplo de nome. */
export const TIPOS_LUGAR_SALDO: Record<string, { rotulo: string; explica: string; exemplo: string }> = {
  CONTA_CORRENTE: { rotulo: 'Conta corrente ou digital', explica: 'Conta no banco ou no aplicativo.', exemplo: 'Ex.: Nubank da Ana' },
  POUPANCA: { rotulo: 'Poupança', explica: 'O que está guardado na poupança.', exemplo: 'Ex.: Poupança Caixa' },
  DINHEIRO: { rotulo: 'Dinheiro na mão', explica: 'Dinheiro guardado em casa ou na carteira.', exemplo: 'Ex.: Carteira do Pablo' },
  COFRINHO: { rotulo: 'Cofrinho', explica: 'O porquinho das crianças também conta.', exemplo: 'Ex.: Cofrinho do João' },
  INVESTIMENTO: {
    rotulo: 'Investimento de resgate rápido',
    explica: 'Só a reserva que vocês não acompanham na aba Investimentos. Se ela já está lá, não incluam aqui: o valor contaria duas vezes.',
    exemplo: 'Ex.: CDB de liquidez diária',
  },
  OUTRO: { rotulo: 'Outro', explica: 'Qualquer outro lugar com dinheiro disponível.', exemplo: 'Ex.: saldo do vale-alimentação' },
}

export function primeiroNome(nome: string): string {
  return nome.trim().split(/\s+/)[0] ?? nome
}
