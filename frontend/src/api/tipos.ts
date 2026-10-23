// Tipos das respostas da API (espelham os DTOs de br.com.familianca.adapter.in.web.dto).

export type Tipo = 'RECEITA' | 'DESPESA'
export type Natureza = 'FIXA' | 'VARIAVEL'
export type Papel = 'DONO' | 'MEMBRO'
export type CodigoPlano = 'GRATUITO' | 'FAMILIA' | 'PREMIUM'
export type Recurso = 'INVESTIMENTOS' | 'DESPESAS_PJ'
export type Frequencia = 'UNICA' | 'MENSAL' | 'BIMESTRAL' | 'TRIMESTRAL' | 'SEMESTRAL' | 'ANUAL'
/** Como a despesa é paga: diz se ela pede uma ação até o vencimento. */
export type FormaPagamento = 'BOLETO_PIX' | 'DEBITO_AUTOMATICO' | 'CARTAO_CREDITO'
export type Periodicidade =
  | 'UNICA' | 'DIARIA' | 'DIAS_UTEIS' | 'SEMANAL' | 'QUINZENAL'
  | 'MENSAL' | 'BIMESTRAL' | 'TRIMESTRAL' | 'SEMESTRAL' | 'ANUAL'
export type TipoInvestimento =
  | 'RENDA_FIXA' | 'TESOURO' | 'POUPANCA' | 'ACOES' | 'FUNDOS' | 'PREVIDENCIA' | 'CRIPTO' | 'OUTRO'

export interface UsuarioLogado {
  id: number
  nome: string
  email: string
  papel: string
  familiaId: number
  membroId: number
  papelFamilia: Papel
}

export interface Plano {
  codigo: CodigoPlano
  rotulo: string
  limiteMembros: number
  recursos: Recurso[]
}

export interface Assinatura {
  plano: CodigoPlano
  status: 'TESTE' | 'ATIVA' | 'CANCELADA' | string
  validaAte: string | null
}

export interface Membro {
  id: number
  usuarioId: number
  nome: string
  email: string
  papel: Papel
  ativo: boolean
}

export interface Convite {
  id: number
  email: string
  papel: Papel
  status: string
  expiraEm: string
  token?: string
  link?: string
}

/** Como estimar os próximos meses de uma despesa que não se repete, pelos meses que já passaram no ano. */
export type Estimativa = 'NENHUMA' | 'MEDIA' | 'MAXIMO' | 'MINIMO'

export interface Familia {
  id: number
  nome: string
  metaAportePercentual: number | null
  estimativa: Estimativa
  assinatura: Assinatura
  planoEmVigor: Plano
  membros: Membro[]
  convitesPendentes: Convite[]
  /** Sem saldo inicial, nada se lança (o servidor responde 422). */
  temSaldoInicial: boolean
}

export interface ConviteAberto {
  familia: string
  email: string
  papel: Papel
  expiraEm: string
}

export interface Categoria {
  id: number
  grupoId: number
  nome: string
  ordem: number
  ativa: boolean
  tributavel: boolean
  aliquota: number | null
  calculada: boolean
  /** Costuma se repetir: ao lançar, o formulário já sugere repetir no ano. */
  recorrente: boolean
  /** Regra própria para estimar os próximos meses; nulo = segue a família. */
  estimativa: Estimativa | null
}

export interface Grupo {
  id: number
  tipo: Tipo
  natureza: Natureza
  nome: string
  empresa: boolean
  ordem: number
  ativo: boolean
  categorias: Categoria[]
}

export interface Ref {
  id: number
  nome: string
}

/** Nota fiscal de receita tributável: o mês do serviço (AAAA-MM) e a emissão. O lançamento conta no pagamento. */
export interface NotaFiscal {
  competencia: string | null
  emissao: string | null
}

export interface Lancamento {
  id: number
  tipo: Tipo
  categoria: Ref
  grupo: Ref & { natureza: Natureza }
  membro: Ref | null
  descricao: string
  valor: number
  data: string
  competencia: string
  recorrenciaId: number | null
  competenciaRecorrencia: string | null
  parcela: { parcelamentoId: string; numero: number; total: number } | null
  observacao: string | null
  /** Vencimento da conta que este lançamento pagou; nulo = sem vencimento. */
  vencimento: string | null
  /** 0 = pago em dia; nulo = sem vencimento. */
  diasAtraso: number | null
  notaFiscal: NotaFiscal | null
}

export interface Recorrencia {
  id: number
  tipo: Tipo
  categoria: Ref
  grupo: Ref & { natureza: Natureza }
  membro: Ref | null
  descricao: string
  valor: number
  dia: number
  frequencia: Frequencia
  inicio: string
  fim: string | null
  observacao: string | null
  puladas: string[]
  ano: number
  valorNoAno: number
  valorAnualizado: number
  confirmarSozinha: boolean
  /** AAAA-MM-DD: vale para o que vence a partir daí. */
  confirmarSozinhaDesde: string | null
  /** Previsto das ocorrências do mês atual em diante: a estimativa pela regra ou o valor cadastrado. */
  valorPrevisto: number
  /** Regra que gerou o valorPrevisto; nulo = valor cadastrado. */
  previstoPor: Estimativa | null
  confirmacoesNaPrevisao: number
  semVencimento: boolean
  formaPagamento: FormaPagamento | null
  /** Despesa com data certa para pagar (não marcada sem vencimento e não cobrada no cartão). */
  temVencimento: boolean
  /** Vencimentos diferentes do dia cadastrado, por mês. */
  vencimentosAjustados: { competencia: string; vencimento: string }[]
}

export interface Pendente {
  competencia: string
  data: string
  valorPrevisto: number
  recorrencia: Recorrencia
  /** Vencimento desta ocorrência; nulo = sem vencimento. */
  vencimento: string | null
}

export interface Serie {
  valores: number[]
  ano: number
  mediaMensal: number
}

export interface SeriePercentual {
  valores: (number | null)[]
  ano: number | null
}

export interface CategoriaVisao {
  id: number
  nome: string
  ativa: boolean
  calculada: boolean
  aliquota: number | null
  recorrente: boolean
  tributavel: boolean
  /** Regra própria para estimar os próximos meses; nulo = segue a família. */
  estimativa: Estimativa | null
  total: Serie
  realizado: number[]
  previsto: number[]
  /** Parte do previsto que é estimativa (média, máximo ou mínimo dos meses que já passaram). */
  estimado: number[]
  participacao: number | null
  rank: number | null
  /** O que compõe a categoria, do maior para o menor: cada recorrência, cada descrição avulsa e a estimativa. */
  itens: ItemVisao[]
}

export interface ItemVisao {
  descricao: string
  /** A recorrência que é este item; nulo = descrição avulsa ou estimativa. */
  recorrenciaId: number | null
  /** É a estimativa dos próximos meses. */
  estimativa: boolean
  total: Serie
  previsto: number[]
}

export interface GrupoVisao {
  id: number
  nome: string
  natureza: Natureza
  empresa: boolean
  ativo: boolean
  total: Serie
  realizado: number[]
  previsto: number[]
  participacao: number | null
  inflacao: SeriePercentual
  pesoSobreTributaveis: number | null
  categorias: CategoriaVisao[]
}

export interface Bloco {
  total: Serie
  grupos: GrupoVisao[]
}

export interface Resumo {
  sobraMesAnterior: Serie
  sobraInformada: boolean[]
  /** Há saldo informado num ano anterior: janeiro começa com a sobra de dezembro. */
  saldoAntesDoAno: boolean
  rendaTotal: Serie
  receitasFixas: Serie
  receitasVariaveis: Serie
  aplicacoes: Serie
  saldoDisponivel: Serie
  despesasTotal: Serie
  despesasFixas: Serie
  despesasVariaveis: Serie
  fluxoCaixaLiquido: Serie
  rendaInvestimentos: Serie
  grauIndependencia: SeriePercentual
  aportePrevisto: Serie
  inflacaoFamiliar: SeriePercentual
}

export interface MembroVisao {
  membroId: number | null
  nome: string
  receitas: Serie
  despesas: Serie
  participacaoReceitas: number | null
  participacaoDespesas: number | null
}

export interface VisaoAnual {
  ano: number
  meses: string[]
  resumo: Resumo
  receitas: Bloco
  despesas: Bloco
  membros: MembroVisao[]
}

export interface Impacto {
  valor: number
  periodicidade: Periodicidade
  ano: number
  valorAnual: number
  valorEm5Anos: number
  valorEm10Anos: number
  sobreRendaAnual: number | null
  sobreDespesasAnuais: number | null
  diasDeRenda: number | null
  mensagem: string
}

export interface Saldo {
  competencia: string
  valor: number
  observacao: string | null
  /** Dia a que o valor se refere; nulo = já é o do dia 1º. */
  dataReferencia: string | null
  /** Saldo de cada lugar (conta, cofrinho...); vazio = só o total. */
  porLocal: { localId: number; valor: number }[]
  /** Com o saldo do dia 1º: o de outro dia (ex.: hoje), guardado à parte só para conferir. */
  conferencia: { dia: string; valor: number } | null
}

export type TipoLugarSaldo = 'CONTA_CORRENTE' | 'POUPANCA' | 'DINHEIRO' | 'COFRINHO' | 'INVESTIMENTO' | 'OUTRO'

/** Onde está o dinheiro da família. membroId nulo = da família toda ou de quem não usa o Familiança. */
export interface LugarSaldo {
  id: number
  nome: string
  tipo: TipoLugarSaldo
  membroId: number | null
  ativo: boolean
}

export interface MovimentoDoMes {
  ate: string
  recebido: number
  pago: number
}

export interface Investimento {
  id: number
  nome: string
  tipo: TipoInvestimento
  ativo: boolean
}

export interface Movimento {
  investimentoId: number
  competencia: string
  aporte: number
  rentabilidade: number | null
  saldoInformado: number | null
}

export interface Posicao {
  investimento: Investimento
  saldoInicial: number
  aportes: number[]
  rendimentos: number[]
  saldos: number[]
  rentabilidadeMensal: (number | null)[]
  rentabilidadeAno: number | null
}

export interface Carteira {
  ano: number
  saldoInicial: number
  aportes: number[]
  totalAportes: number
  rendimentos: number[]
  totalRendimentos: number
  saldos: number[]
  rentabilidadeMensal: (number | null)[]
  rentabilidadeAno: number | null
  investimentos: Posicao[]
}

export interface SituacaoMfa {
  ativo: boolean
  disponivel: boolean
}
