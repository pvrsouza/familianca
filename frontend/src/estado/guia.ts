import { ref } from 'vue'

// Passos do guia "Como começar" que a família pulou (cadastrar o que se repete, lançar um gasto, trazer a família).
// O saldo inicial não se pula. Como os centavos, é conveniência de cada aparelho, guardada só no navegador, por
// família: pular aqui não esconde o passo de outra pessoa da família.
const PREFIXO = 'familianca.guia.pulados.'

const pulados = ref<Set<string>>(new Set())
let familiaCarregada: number | null = null

function chave(familiaId: number): string {
  return PREFIXO + familiaId
}

function carregar(familiaId: number): void {
  if (familiaCarregada === familiaId) return
  familiaCarregada = familiaId
  try {
    pulados.value = new Set(JSON.parse(localStorage.getItem(chave(familiaId)) ?? '[]') as string[])
  } catch {
    pulados.value = new Set()
  }
}

function gravar(familiaId: number): void {
  try {
    localStorage.setItem(chave(familiaId), JSON.stringify([...pulados.value]))
  } catch {
    // Sem armazenamento (aba anônima, bloqueio): vale só até recarregar.
  }
}

export const guia = {
  pulado(familiaId: number, passo: string): boolean {
    carregar(familiaId)
    return pulados.value.has(passo)
  },

  pular(familiaId: number, passo: string, sim: boolean): void {
    carregar(familiaId)
    const novo = new Set(pulados.value)
    if (sim) novo.add(passo)
    else novo.delete(passo)
    pulados.value = novo
    gravar(familiaId)
  },
}
