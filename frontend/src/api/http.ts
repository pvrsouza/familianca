// Cliente da API: mesma origem, cookie de sessão HttpOnly e CSRF pelo header X-XSRF-TOKEN (valor do cookie
// XSRF-TOKEN, legível pelo JavaScript). Os erros chegam como ProblemDetail (RFC 9457).

export interface Problema {
  status: number
  title?: string
  detail?: string
  plano?: string
  recurso?: string
  mfaObrigatorio?: boolean
}

export class ErroApi extends Error {
  readonly status: number
  readonly problema: Problema
  /** Segundos até poder tentar de novo (429). */
  readonly tentarEm?: number

  constructor(problema: Problema, tentarEm?: number) {
    super(problema.detail || problema.title || 'Algo deu errado. Tente de novo.')
    this.status = problema.status
    this.problema = problema
    this.tentarEm = tentarEm
  }
}

/** Quem trata a sessão expirada (o router redireciona para o login). */
let aoPerderSessao: (() => void) | null = null
export function quandoPerderSessao(fn: () => void) {
  aoPerderSessao = fn
}

const METODOS_SEGUROS = new Set(['GET', 'HEAD', 'OPTIONS'])

function lerCookie(nome: string): string | null {
  const par = document.cookie.split('; ').find((c) => c.startsWith(nome + '='))
  return par ? decodeURIComponent(par.slice(nome.length + 1)) : null
}

let csrfPronto: Promise<void> | null = null

/** Garante o cookie XSRF-TOKEN antes da primeira requisição que altera dados. */
export function prepararCsrf(forcar = false): Promise<void> {
  if (!forcar && lerCookie('XSRF-TOKEN')) return Promise.resolve()
  if (!csrfPronto || forcar) {
    csrfPronto = fetch('/api/auth/csrf', { credentials: 'same-origin' })
      .then(() => undefined)
      .finally(() => {
        csrfPronto = null
      })
  }
  return csrfPronto
}

interface Opcoes {
  corpo?: unknown
  /** Rotas públicas (login, cadastro, convite): 401 não significa sessão perdida. */
  publica?: boolean
  sinal?: AbortSignal
}

export async function requisitar<T>(metodo: string, caminho: string, opcoes: Opcoes = {}): Promise<T> {
  const cabecalhos: Record<string, string> = { Accept: 'application/json' }
  if (!METODOS_SEGUROS.has(metodo)) {
    await prepararCsrf()
    const token = lerCookie('XSRF-TOKEN')
    if (token) cabecalhos['X-XSRF-TOKEN'] = token
  }
  if (opcoes.corpo !== undefined) cabecalhos['Content-Type'] = 'application/json'

  let resposta: Response
  try {
    resposta = await fetch(caminho, {
      method: metodo,
      headers: cabecalhos,
      body: opcoes.corpo !== undefined ? JSON.stringify(opcoes.corpo) : undefined,
      credentials: 'same-origin',
      signal: opcoes.sinal,
    })
  } catch (e) {
    if ((e as Error).name === 'AbortError') throw e
    throw new ErroApi({ status: 0, detail: 'Sem conexão com o Familiança. Confira a internet e tente de novo.' })
  }

  if (resposta.status === 204) return undefined as T
  const texto = await resposta.text()
  const dados = texto ? safeJson(texto) : undefined

  if (!resposta.ok) {
    const problema: Problema = { status: resposta.status, ...(typeof dados === 'object' && dados ? dados : {}) }
    if (resposta.status === 401 && !opcoes.publica && !problema.mfaObrigatorio) aoPerderSessao?.()
    if (resposta.status === 403 && problema.detail?.toLowerCase().includes('csrf')) {
      // Token velho (ex.: depois do login): renova para a próxima tentativa.
      await prepararCsrf(true)
    }
    const espera = Number(resposta.headers.get('Retry-After')) || undefined
    throw new ErroApi(problema, espera)
  }
  return dados as T
}

function safeJson(texto: string): unknown {
  try {
    return JSON.parse(texto)
  } catch {
    return undefined
  }
}

export const api = {
  get: <T>(caminho: string, opcoes?: Opcoes) => requisitar<T>('GET', caminho, opcoes),
  post: <T>(caminho: string, corpo?: unknown, opcoes?: Opcoes) => requisitar<T>('POST', caminho, { ...opcoes, corpo }),
  put: <T>(caminho: string, corpo?: unknown, opcoes?: Opcoes) => requisitar<T>('PUT', caminho, { ...opcoes, corpo }),
  delete: <T = void>(caminho: string, opcoes?: Opcoes) => requisitar<T>('DELETE', caminho, opcoes),
}

/** Mensagem para mostrar à pessoa, a partir de qualquer erro. */
export function mensagemDeErro(e: unknown): string {
  if (e instanceof ErroApi) {
    if (e.status === 429) {
      const minutos = e.tentarEm ? Math.max(1, Math.ceil(e.tentarEm / 60)) : null
      return minutos
        ? `Muitas tentativas seguidas. Tente de novo em ${minutos} ${minutos === 1 ? 'minuto' : 'minutos'}.`
        : 'Muitas tentativas seguidas. Espere um pouco e tente de novo.'
    }
    if (e.status >= 500) return 'Algo deu errado do nosso lado. Tente de novo em instantes.'
    return e.message
  }
  return 'Algo deu errado. Tente de novo.'
}
