<script setup lang="ts">
import { computed } from 'vue'
import type { VisaoAnual } from '../../api/tipos'
import { MESES_CURTOS, pct, reais, reaisCurtos } from '../../util/formato'

// O peso de cada pessoa no orçamento: o mesmo quadro para todos, sem apontar o dedo.
const props = defineProps<{ visao: VisaoAnual }>()

const pessoas = computed(() =>
  [...props.visao.membros].sort((a, b) => b.despesas.ano - a.despesas.ano),
)
const maiorDespesa = computed(() => Math.max(1, ...pessoas.value.map((p) => p.despesas.ano)))
const vazio = computed(() => pessoas.value.every((p) => !p.despesas.ano && !p.receitas.ano))
</script>

<template>
  <div class="pessoas">
    <p class="intro">
      Quanto cada um traz e quanto pesa nas despesas da família no ano. Os valores são de quem cada lançamento
      pertence.
    </p>

    <p v-if="vazio" class="texto-2">Ainda não há lançamentos no ano para mostrar por pessoa.</p>

    <ul v-else class="lista painel">
      <li v-for="p in pessoas" :key="p.membroId ?? 'calc'" class="pessoa">
        <div class="quem">
          <span class="avatar" aria-hidden="true">{{ p.membroId ? p.nome.charAt(0) : '%' }}</span>
          <span class="nome">{{ p.nome }}</span>
        </div>
        <div class="peso">
          <div class="barra" role="img"
               :aria-label="`${pct(p.participacaoDespesas)} das despesas da família`">
            <span :style="{ width: (p.despesas.ano / maiorDespesa) * 100 + '%' }" />
          </div>
          <span class="num rotulo">{{ pct(p.participacaoDespesas) }} das despesas</span>
        </div>
        <dl class="numeros">
          <div><dt>Despesas no ano</dt><dd class="num">{{ reais(p.despesas.ano) }}</dd></div>
          <div><dt>Receitas no ano</dt><dd class="num receita">{{ reais(p.receitas.ano) }}</dd></div>
          <div><dt>Parte da renda</dt><dd class="num">{{ pct(p.participacaoReceitas) }}</dd></div>
        </dl>
      </li>
    </ul>

    <section v-if="!vazio" class="secao mes-a-mes">
      <div class="secao-cabecalho">
        <h2>Despesas de cada um, mês a mês</h2>
      </div>
      <div class="tabela-rolagem">
        <table class="tabela">
          <thead>
            <tr>
              <th scope="col">Valores em R$</th>
              <th v-for="m in MESES_CURTOS" :key="m" scope="col">{{ m }}</th>
              <th scope="col">Ano</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in pessoas" :key="p.membroId ?? 'calc'">
              <th scope="row">{{ p.nome }}</th>
              <td v-for="(v, i) in p.despesas.valores" :key="i" class="num">{{ reaisCurtos(v) }}</td>
              <td class="num forte">{{ reaisCurtos(p.despesas.ano) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.intro {
  max-width: 64ch;
  color: var(--texto-2);
  margin-bottom: var(--e-4);
}

.pessoa {
  display: grid !important;
  grid-template-columns: minmax(10rem, 1.2fr) minmax(12rem, 2fr) minmax(18rem, 2.4fr);
  gap: var(--e-6);
  padding: var(--e-4) var(--e-5) !important;
}

.quem {
  display: flex;
  align-items: center;
  gap: var(--e-3);
  min-width: 0;
}

.avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: 50%;
  background: var(--petroleo-tinta);
  color: var(--petroleo-escuro);
  font-weight: 700;
  text-transform: uppercase;
}

.nome {
  font-weight: 600;
  overflow-wrap: anywhere;
}

.peso {
  display: grid;
  gap: var(--e-1);
  align-content: center;
}

.barra {
  height: 0.625rem;
  border-radius: 999px;
  background: var(--areia-funda);
  overflow: hidden;
}

.barra span {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--petroleo);
  transition: width 420ms var(--ease);
}

.rotulo {
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.numeros {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--e-4);
  margin: 0;
}

.numeros dt {
  font-size: var(--t-xs);
  color: var(--texto-2);
}

.numeros dd {
  margin: 0;
  font-weight: 600;
}

.mes-a-mes {
  margin-top: var(--e-10);
}

.tabela th:first-child {
  min-width: 10rem;
  font-weight: 500;
}

.tabela thead th {
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

td.forte {
  font-weight: 700;
}

@media (max-width: 900px) {
  .pessoa {
    grid-template-columns: 1fr;
    gap: var(--e-3);
  }

  .numeros {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
