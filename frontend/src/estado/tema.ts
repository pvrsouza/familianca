import { ref } from 'vue'

// Tema da interface: automático (segue o sistema), claro ou escuro. Como os centavos, é conveniência de cada
// aparelho, guardada só no navegador. As cores mudam pelo color-scheme do <html> (estilos/base.css); aqui só se fixa
// data-tema quando a pessoa escolhe um lado e se acerta a cor da barra do navegador.
export type Tema = 'automatico' | 'claro' | 'escuro'

const CHAVE = 'familianca.tema'
const TEMAS: readonly Tema[] = ['automatico', 'claro', 'escuro']
const COR_BARRA = { claro: '#1F5C66', escuro: '#15434A' }

function ler(): Tema {
  try {
    const valor = localStorage.getItem(CHAVE)
    return TEMAS.includes(valor as Tema) ? (valor as Tema) : 'automatico'
  } catch {
    return 'automatico'
  }
}

const escolha = ref<Tema>(ler())
const sistemaEscuro = window.matchMedia('(prefers-color-scheme: dark)')

function aplicar(): void {
  const raiz = document.documentElement
  if (escolha.value === 'automatico') delete raiz.dataset.tema
  else raiz.dataset.tema = escolha.value

  const escuro = escolha.value === 'escuro' || (escolha.value === 'automatico' && sistemaEscuro.matches)
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', escuro ? COR_BARRA.escuro : COR_BARRA.claro)
}

sistemaEscuro.addEventListener('change', aplicar)

export const tema = {
  escolha,
  aplicar,

  escolher(t: Tema): void {
    escolha.value = t
    try {
      localStorage.setItem(CHAVE, t)
    } catch {
      // Sem armazenamento (aba anônima, bloqueio): vale só até recarregar.
    }
    aplicar()
  },
}
