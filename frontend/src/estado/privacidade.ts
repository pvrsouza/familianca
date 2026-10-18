import { ref } from 'vue'

// Esconder os valores (o "olhinho" da barra): conveniência de cada aparelho, guardada só no navegador.
const CHAVE = 'familianca.valoresOcultos'

function ler(): boolean {
  try {
    return localStorage.getItem(CHAVE) === '1'
  } catch {
    return false
  }
}

const ocultos = ref(ler())

export const privacidade = {
  ocultos,

  alternar(): void {
    ocultos.value = !ocultos.value
    try {
      localStorage.setItem(CHAVE, ocultos.value ? '1' : '0')
    } catch {
      // Sem armazenamento (aba anônima, bloqueio): vale só até recarregar.
    }
  },
}
