import { computed, reactive } from 'vue'
import { categorias as apiCategorias } from '../api/servicos'
import type { Grupo, Tipo } from '../api/tipos'

// Plano de contas em memória para os formulários; recarrega quando as categorias mudam.
const estado = reactive({ grupos: [] as Grupo[], carregado: false, carregando: null as Promise<void> | null })

export const catalogo = {
  estado,
  grupos: computed(() => estado.grupos),

  carregar(forcar = false): Promise<void> {
    if (estado.carregado && !forcar) return Promise.resolve()
    if (!estado.carregando || forcar) {
      estado.carregando = apiCategorias
        .listar()
        .then((g) => {
          estado.grupos = g
          estado.carregado = true
        })
        .finally(() => {
          estado.carregando = null
        })
    }
    return estado.carregando
  },

  /** Grupos ativos do tipo, só com categorias que aceitam lançamento (ativas e não calculadas). */
  lancaveis(tipo: Tipo): Grupo[] {
    return estado.grupos
      .filter((g) => g.tipo === tipo && g.ativo)
      .map((g) => ({ ...g, categorias: g.categorias.filter((c) => c.ativa && !c.calculada) }))
      .filter((g) => g.categorias.length > 0)
  },

  tipoDaCategoria(id: number): Tipo | null {
    return estado.grupos.find((g) => g.categorias.some((c) => c.id === id))?.tipo ?? null
  },

  limpar(): void {
    estado.grupos = []
    estado.carregado = false
  },
}
