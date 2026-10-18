import { onBeforeUnmount, ref } from 'vue'

/** true enquanto a media query casar (ex.: '(max-width: 760px)'). */
export function useMidia(consulta: string) {
  const mq = window.matchMedia(consulta)
  const casa = ref(mq.matches)
  const ouvir = (e: MediaQueryListEvent) => (casa.value = e.matches)
  mq.addEventListener('change', ouvir)
  onBeforeUnmount(() => mq.removeEventListener('change', ouvir))
  return casa
}
