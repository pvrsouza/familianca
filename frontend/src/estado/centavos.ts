import { ref } from 'vue'

// Centavos nas tabelas do ano: por padrão os valores aparecem arredondados (1.235); quem prefere o valor exato liga
// aqui. Como o olhinho, é conveniência de cada aparelho, guardada só no navegador.
const CHAVE = 'familianca.centavos'

function ler(): boolean {
  try {
    return localStorage.getItem(CHAVE) === '1'
  } catch {
    return false
  }
}

const ligados = ref(ler())

export const centavos = {
  ligados,

  alternar(): void {
    ligados.value = !ligados.value
    try {
      localStorage.setItem(CHAVE, ligados.value ? '1' : '0')
    } catch {
      // Sem armazenamento (aba anônima, bloqueio): vale só até recarregar.
    }
  },
}
