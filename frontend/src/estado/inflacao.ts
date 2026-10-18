import { ref } from 'vue'

// Linhas de inflação na tabela do ano (da família e de cada grupo de despesa), como na planilha. Vêm ligadas; quem
// quiser a tabela mais enxuta desliga. Como os centavos, é conveniência de cada aparelho, guardada só no navegador.
const CHAVE = 'familianca.inflacao'

function ler(): boolean {
  try {
    return localStorage.getItem(CHAVE) !== '0'
  } catch {
    return true
  }
}

const ligada = ref(ler())

export const inflacao = {
  ligada,

  alternar(): void {
    ligada.value = !ligada.value
    try {
      localStorage.setItem(CHAVE, ligada.value ? '1' : '0')
    } catch {
      // Sem armazenamento (aba anônima, bloqueio): vale só até recarregar.
    }
  },
}
