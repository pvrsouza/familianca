import type { Carteira } from '../api/tipos'

const EPS = 0.004

export interface MovimentoCarteira {
  aplicados: number
  resgatados: number
  /** Saldo que já existia nos investimentos abertos no ano (não é aporte nem rendimento). */
  abertos: number
  /** Competência (AAAA-MM) da abertura quando houve uma só; senão nulo. */
  mesAbertura: string | null
  investimentosAbertos: number
}

/**
 * O que entrou e saiu da carteira no ano, investimento a investimento: a soma do mês na carteira esconde o aporte de
 * um que se anula com o resgate de outro. Aporte positivo é aplicação; negativo, resgate.
 */
export function movimentoDoAno(c: Carteira): MovimentoCarteira {
  let aplicados = 0
  let resgatados = 0
  let abertos = 0
  const meses: string[] = []
  for (const p of c.investimentos) {
    for (const v of p.aportes) {
      if (v > EPS) aplicados += v
      else if (v < -EPS) resgatados -= v
    }
    if (p.abertura && p.abertura.competencia.startsWith(`${c.ano}-`)) {
      abertos += p.abertura.valor
      meses.push(p.abertura.competencia)
    }
  }
  return {
    aplicados,
    resgatados,
    abertos,
    mesAbertura: meses.length === 1 ? meses[0] : null,
    investimentosAbertos: meses.length,
  }
}
