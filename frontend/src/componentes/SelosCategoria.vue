<script setup lang="ts">
import { computed } from 'vue'
import type { Estimativa, Tipo } from '../api/tipos'
import { ESTIMATIVAS, pct } from '../util/formato'
import { avisar } from '../estado/avisos'

// Selos de uma letra com o jeito da categoria: R (repete), T (tributável), C (calculada), E (estimativa própria).
// Passar o mouse mostra a explicação completa; no celular, tocar no selo mostra a mesma explicação num aviso.
const props = defineProps<{
  categoria: {
    recorrente?: boolean
    tributavel?: boolean
    calculada?: boolean
    aliquota?: number | null
    estimativa?: Estimativa | null
  }
  tipo?: Tipo | null
}>()

interface Selo {
  letra: string
  nome: string
  texto: string
  classe: string
}

const selos = computed<Selo[]>(() => {
  const c = props.categoria
  const lista: Selo[] = []
  if (c.recorrente) {
    lista.push({
      letra: 'R',
      nome: 'Recorrente',
      classe: 'r',
      texto: `Recorrente: ${props.tipo === 'RECEITA' ? 'receita' : 'conta'} que costuma se repetir todo mês (como aluguel, `
        + 'telefone ou salário). Ao lançar nesta categoria, "Repetir no ano" já vem marcado e os próximos meses '
        + 'aparecem como previstos na visão anual até serem confirmados. Dá para desmarcar e lançar uma vez só.',
    })
  }
  if (c.calculada) {
    lista.push({
      letra: 'C',
      nome: 'Calculada',
      classe: 'c',
      texto: `Calculada por alíquota: todo mês vale ${pct(c.aliquota, 2)} das receitas tributáveis do mês (como `
        + 'DAS ou ISS). O valor é calculado sozinho e a categoria não recebe lançamentos.',
    })
  }
  if (c.tributavel) {
    lista.push({
      letra: 'T',
      nome: 'Tributável',
      classe: 't',
      texto: 'Tributável: receita que entra na base de cálculo dos impostos da empresa (como nota fiscal emitida). '
        + 'As categorias calculadas por alíquota usam a soma destas receitas em cada mês.',
    })
  }
  // Regra própria de previsão: na despesa avulsa, a estimativa; na que se repete, as contas dela sem regra própria.
  if (c.estimativa && props.tipo !== 'RECEITA' && !c.calculada) {
    const regra = ESTIMATIVAS[c.estimativa]
    const doQue = c.recorrente ? 'as contas desta categoria (sem regra própria) são previstas'
      : 'os próximos meses desta categoria são estimados'
    lista.push({
      letra: 'E',
      nome: 'Previsão própria',
      classe: 'e',
      texto: c.estimativa === 'NENHUMA'
        ? (c.recorrente
          ? 'Previsão própria: as contas desta categoria valem o valor cadastrado, mesmo que a família estime as outras.'
          : 'Previsão própria: os próximos meses desta categoria não são estimados, mesmo que a família estime '
            + 'as outras. Só conta o que for lançado.')
        : `Previsão própria (${regra.rotulo.toLowerCase()}): ${doQue} ${regra.como}, em vez da regra da família.`,
    })
  }
  return lista
})
</script>

<template>
  <span v-if="selos.length" class="selos-categoria">
    <span v-for="s in selos" :key="s.letra" class="selo-letra" :class="s.classe" role="img" :aria-label="s.texto"
          :title="s.texto" @click.stop.prevent="avisar(s.texto)">{{ s.letra }}</span>
  </span>
</template>

<style scoped>
.selos-categoria {
  display: inline-flex;
  gap: 3px;
  vertical-align: middle;
}

.selo-letra {
  display: inline-grid;
  place-items: center;
  width: 1.125rem;
  height: 1.125rem;
  border-radius: 4px;
  font-size: 0.6875rem;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0;
  text-transform: none;
  cursor: help;
  user-select: none;
}

.r {
  background: var(--petroleo-tinta-forte);
  color: var(--petroleo-escuro);
}

.c {
  background: var(--coral-claro);
  color: var(--coral-borda);
  outline: 1px solid var(--coral-borda);
  outline-offset: -1px;
}

.t {
  background: var(--verde-claro);
  color: var(--verde);
}

.e {
  background: var(--areia-funda);
  color: var(--cinza);
  outline: 1px dashed var(--pedra-forte);
  outline-offset: -1px;
}
</style>
