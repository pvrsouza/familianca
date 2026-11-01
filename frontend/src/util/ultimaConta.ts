// A última conta usada ao lançar fica no navegador de cada pessoa (nada vai para o servidor).
function chave(membroId: number | null | undefined): string {
  return `familianca.ultimaConta.${membroId ?? 0}`
}

export function ultimaConta(membroId: number | null | undefined): number | null {
  try {
    return Number(localStorage.getItem(chave(membroId))) || null
  } catch {
    return null // navegador sem armazenamento: o campo só começa vazio
  }
}

export function guardarUltimaConta(membroId: number | null | undefined, contaId: number | null): void {
  try {
    if (contaId) localStorage.setItem(chave(membroId), String(contaId))
  } catch {
    // sem armazenamento: não lembra, e tudo segue
  }
}
