import { computed, reactive } from 'vue'
import { auth, familia as apiFamilia } from '../api/servicos'
import type { Familia, Recurso, UsuarioLogado } from '../api/tipos'
import { ErroApi } from '../api/http'

// Estado da sessão: quem está logado e a família dele. Uma família por pessoa (por enquanto).
const estado = reactive({
  usuario: null as UsuarioLogado | null,
  familia: null as Familia | null,
  carregado: false,
  /** Muda a cada lançamento/recorrência salvo, para as telas abertas recarregarem. */
  versaoDados: 0,
})

export const sessao = {
  estado,
  logado: computed(() => estado.usuario !== null),
  dono: computed(() => estado.usuario?.papelFamilia === 'DONO'),
  recursos: computed(() => new Set<Recurso>(estado.familia?.planoEmVigor.recursos ?? [])),
  /** Até onde as setas de ano e mês voltam: o ano mais antigo com algum dado da família. */
  primeiroAno: computed(() => estado.familia?.primeiroAno ?? new Date().getFullYear()),

  /** Lê a sessão do servidor (cookie). Sem sessão: usuario fica nulo. */
  async carregar(): Promise<void> {
    try {
      estado.usuario = await auth.eu()
      await this.recarregarFamilia()
    } catch (e) {
      if (!(e instanceof ErroApi) || e.status !== 401) throw e
      estado.usuario = null
      estado.familia = null
    } finally {
      estado.carregado = true
    }
  },

  async recarregarFamilia(): Promise<void> {
    estado.familia = await apiFamilia.consultar()
  },

  async entrar(email: string, senha: string, codigoMfa?: string): Promise<void> {
    estado.usuario = await auth.entrar(email, senha, codigoMfa)
    await this.recarregarFamilia()
  },

  async sair(): Promise<void> {
    try {
      await auth.sair()
    } finally {
      this.limpar()
    }
  },

  limpar(): void {
    estado.usuario = null
    estado.familia = null
  },

  avisarMudanca(): void {
    estado.versaoDados++
    // Um lançamento antigo pode levar o primeiro ano para trás.
    this.recarregarFamilia().catch(() => {})
  },

  tem(recurso: Recurso): boolean {
    return this.recursos.value.has(recurso)
  },
}
