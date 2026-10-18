import { reactive } from 'vue'

// Avisos rápidos depois de uma ação ("Despesa lançada."). Somem sozinhos.
export const avisos = reactive({ lista: [] as { id: number; texto: string }[] })
let proximo = 1

export function avisar(texto: string) {
  const id = proximo++
  avisos.lista.push({ id, texto })
  setTimeout(() => {
    const i = avisos.lista.findIndex((a) => a.id === id)
    if (i >= 0) avisos.lista.splice(i, 1)
  }, 4200)
}
