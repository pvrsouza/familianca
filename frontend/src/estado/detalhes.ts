import { ref } from 'vue'

// Categorias abertas em itens na tabela do ano (ex.: "Telefone e celular" mostrando cada linha). Por padrão ficam
// agrupadas; quem abre uma, ela continua aberta. Como os centavos, é conveniência de cada aparelho, guardada só no
// navegador.
const CHAVE = 'familianca.categorias-abertas'

function ler(): Set<number> {
  try {
    return new Set(JSON.parse(localStorage.getItem(CHAVE) ?? '[]') as number[])
  } catch {
    return new Set()
  }
}

const abertas = ref<Set<number>>(ler())

function gravar(): void {
  try {
    localStorage.setItem(CHAVE, JSON.stringify([...abertas.value]))
  } catch {
    // Sem armazenamento (aba anônima, bloqueio): vale só até recarregar.
  }
}

export const detalhes = {
  abertas,

  alternar(id: number): void {
    const s = new Set(abertas.value)
    if (s.has(id)) s.delete(id)
    else s.add(id)
    abertas.value = s
    gravar()
  },

  definir(ids: number[], abrir: boolean): void {
    const s = new Set(abertas.value)
    ids.forEach((id) => (abrir ? s.add(id) : s.delete(id)))
    abertas.value = s
    gravar()
  },
}
