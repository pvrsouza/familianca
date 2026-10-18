// Tipos das respostas da API (espelham os DTOs de br.com.familianca.adapter.in.web.dto).

export type Tipo = 'RECEITA' | 'DESPESA'
export type Natureza = 'FIXA' | 'VARIAVEL'
export type Papel = 'DONO' | 'MEMBRO'
export type CodigoPlano = 'GRATUITO' | 'FAMILIA' | 'PREMIUM'
export type Recurso = 'INVESTIMENTOS' | 'DESPESAS_PJ'
export type Frequencia = 'MENSAL' | 'BIMESTRAL' | 'TRIMESTRAL' | 'SEMESTRAL' | 'ANUAL'
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
}

export interface Pendente {
  competencia: string
  data: string
  valorPrevisto: number
  recorrencia: Recorrencia
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
