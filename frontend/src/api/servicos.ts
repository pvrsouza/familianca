import { api } from './http'
import type {
  Carteira, Categoria, Convite, ConviteAberto, Estimativa, Familia, Frequencia, Grupo, Impacto, Investimento, Lancamento, Membro,
  Movimento, Natureza, Papel, Pendente, Periodicidade, Recorrencia, Saldo, SituacaoMfa, Tipo, TipoInvestimento,
  UsuarioLogado, VisaoAnual,
} from './tipos'

const publica = { publica: true }

export const auth = {
  eu: () => api.get<UsuarioLogado>('/api/auth/me', publica),
  entrar: (email: string, senha: string, codigoMfa?: string) =>
    api.post<UsuarioLogado>('/api/auth/login', { email, senha, codigoMfa: codigoMfa || undefined }, publica),
  sair: () => api.post<void>('/api/auth/logout', undefined, publica),
  cadastrar: (dados: { nome: string; email: string; senha: string; nomeFamilia?: string }) =>
    api.post('/api/cadastro', dados, publica),
  convite: (token: string) => api.get<ConviteAberto>(`/api/convites/${encodeURIComponent(token)}`, publica),
  aceitarConvite: (token: string, nome: string, senha: string) =>
    api.post(`/api/convites/${encodeURIComponent(token)}/aceite`, { nome, senha }, publica),
}

export const conta = {
  trocarSenha: (senhaAtual: string, novaSenha: string) => api.put<void>('/api/conta/senha', { senhaAtual, novaSenha }),
  renomear: (nome: string) => api.put<void>('/api/conta/nome', { nome }),
  mfa: () => api.get<SituacaoMfa>('/api/conta/mfa'),
  configurarMfa: () => api.post<{ segredo: string; uri: string }>('/api/conta/mfa/configuracao'),
  ativarMfa: (codigo: string) => api.post<{ codigosRecuperacao: string[] }>('/api/conta/mfa/ativacao', { codigo }),
  novosCodigos: (codigo: string) =>
    api.post<{ codigosRecuperacao: string[] }>('/api/conta/mfa/codigos-recuperacao', { codigo }),
  desativarMfa: (senha: string, codigo: string) => api.post<void>('/api/conta/mfa/desativacao', { senha, codigo }),
}

export const familia = {
  consultar: () => api.get<Familia>('/api/familia'),
  atualizar: (nome: string, metaAportePercentual: number | null) =>
    api.put<Familia>('/api/familia', { nome, metaAportePercentual }),
  definirEstimativa: (estimativa: Estimativa) => api.put<Familia>('/api/familia/estimativa', { estimativa }),
  alterarPapel: (membroId: number, papel: Papel) => api.put<Membro>(`/api/familia/membros/${membroId}/papel`, { papel }),
  remover: (membroId: number) => api.delete(`/api/familia/membros/${membroId}`),
  convidar: (email: string, papel: Papel) => api.post<Convite>('/api/familia/convites', { email, papel }),
  cancelarConvite: (id: number) => api.delete(`/api/familia/convites/${id}`),
}

export interface DadosGrupo {
  tipo?: Tipo
  natureza: Natureza
  nome: string
  empresa?: boolean
  ordem?: number | null
  ativo?: boolean
}

export interface DadosCategoria {
  grupoId: number
  nome: string
  ordem?: number | null
  tributavel?: boolean
  aliquota?: number | null
  ativa?: boolean
  recorrente?: boolean
  /** FAMILIA = volta a seguir a regra da família; ausente = mantém. */
  estimativa?: Estimativa | 'FAMILIA'
}

export const categorias = {
  listar: () => api.get<{ grupos: Grupo[] }>('/api/categorias').then((r) => r.grupos),
  criarGrupo: (d: DadosGrupo) => api.post<Grupo>('/api/categorias/grupos', d),
  atualizarGrupo: (id: number, d: DadosGrupo) => api.put<Grupo>(`/api/categorias/grupos/${id}`, d),
  removerGrupo: (id: number) => api.delete(`/api/categorias/grupos/${id}`),
  criar: (d: DadosCategoria) => api.post<Categoria>('/api/categorias', d),
  atualizar: (id: number, d: DadosCategoria) => api.put<Categoria>(`/api/categorias/${id}`, d),
  remover: (id: number) => api.delete(`/api/categorias/${id}`),
  aplicarModeloPj: () => api.post<{ grupos: Grupo[] }>('/api/categorias/modelos/pj'),
}

export interface DadosLancamento {
  categoriaId: number
  membroId?: number | null
  descricao: string
  valor: number
  data: string
  observacao?: string | null
}

export interface FiltroLancamentos {
  de?: string
  ate?: string
  categoriaId?: number
  grupoId?: number
  membroId?: number
  tipo?: Tipo
}

function consulta(filtro: object): string {
  const p = new URLSearchParams()
  Object.entries(filtro).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v))
  })
  const s = p.toString()
  return s ? `?${s}` : ''
}

export const lancamentos = {
  listar: (filtro: FiltroLancamentos = {}) => api.get<Lancamento[]>(`/api/lancamentos${consulta(filtro)}`),
  lancar: (d: DadosLancamento) => api.post<Lancamento>('/api/lancamentos', d),
  lancarParcelado: (d: Omit<DadosLancamento, 'valor' | 'data'> & { valorTotal: number; parcelas: number; primeiraData: string }) =>
    api.post<Lancamento[]>('/api/lancamentos/parcelados', d),
  atualizar: (id: number, d: DadosLancamento) => api.put<Lancamento>(`/api/lancamentos/${id}`, d),
  remover: (id: number, todasAsParcelas = false) =>
    api.delete(`/api/lancamentos/${id}${todasAsParcelas ? '?todasAsParcelas=true' : ''}`),
}

export interface DadosRecorrencia {
  categoriaId: number
  membroId?: number | null
  descricao: string
  valor: number
  dia: number
  frequencia: Frequencia
  inicio: string
  fim?: string | null
  observacao?: string | null
  /** Só despesa: o que vence a partir de hoje vira lançamento sozinho. Ausente = desligada. */
  confirmarSozinha?: boolean
}

export const recorrencias = {
  listar: () => api.get<Recorrencia[]>('/api/recorrencias'),
  pendentes: () => api.get<Pendente[]>('/api/recorrencias/pendentes'),
  criar: (d: DadosRecorrencia) => api.post<Recorrencia>('/api/recorrencias', d),
  atualizar: (id: number, d: DadosRecorrencia) => api.put<Recorrencia>(`/api/recorrencias/${id}`, d),
  remover: (id: number) => api.delete(`/api/recorrencias/${id}`),
  confirmar: (id: number, competencia: string, valor?: number, data?: string) =>
    api.post<Lancamento>(`/api/recorrencias/${id}/ocorrencias/${competencia}/confirmacao`,
      valor !== undefined || data ? { valor, data } : undefined),
  pular: (id: number, competencia: string) => api.post<Recorrencia>(`/api/recorrencias/${id}/ocorrencias/${competencia}/pulo`),
  desfazerPulo: (id: number, competencia: string) =>
    api.delete<Recorrencia>(`/api/recorrencias/${id}/ocorrencias/${competencia}/pulo`),
}

export const saldos = {
  listar: (ano: number) => api.get<Saldo[]>(`/api/saldos?ano=${ano}`),
  definir: (competencia: string, valor: number, observacao?: string) =>
    api.put<Saldo>(`/api/saldos/${competencia}`, { valor, observacao: observacao || null }),
  remover: (competencia: string) => api.delete(`/api/saldos/${competencia}`),
}

export const investimentos = {
  listar: () => api.get<Investimento[]>('/api/investimentos'),
  carteira: (ano: number) => api.get<Carteira>(`/api/investimentos/carteira?ano=${ano}`),
  criar: (nome: string, tipo: TipoInvestimento) => api.post<Investimento>('/api/investimentos', { nome, tipo }),
  atualizar: (id: number, nome: string, tipo: TipoInvestimento, ativo: boolean) =>
    api.put<Investimento>(`/api/investimentos/${id}`, { nome, tipo, ativo }),
  remover: (id: number) => api.delete(`/api/investimentos/${id}`),
  movimentos: (id: number) => api.get<Movimento[]>(`/api/investimentos/${id}/movimentos`),
  registrar: (id: number, competencia: string, d: { aporte: number | null; rentabilidade: number | null; saldoInformado: number | null }) =>
    api.put<Movimento>(`/api/investimentos/${id}/movimentos/${competencia}`, d),
  removerMovimento: (id: number, competencia: string) => api.delete(`/api/investimentos/${id}/movimentos/${competencia}`),
}

export const visao = {
  anoAtual: () => api.get<VisaoAnual>('/api/visao-anual'),
  ano: (ano: number) => api.get<VisaoAnual>(`/api/visao-anual/${ano}`),
  impacto: (valor: number, periodicidade: Periodicidade, ano?: number) =>
    api.post<Impacto>('/api/impacto', { valor, periodicidade, ano }),
}
