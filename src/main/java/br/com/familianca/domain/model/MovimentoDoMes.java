package br.com.familianca.domain.model;

import br.com.familianca.domain.vo.Dinheiro;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** O que entrou (receitas) e saiu (despesas) pelos lançamentos do dia 1º do mês até uma data, inclusive. */
public record MovimentoDoMes(LocalDate ate, Dinheiro recebido, Dinheiro pago) {

    /** Soma os lançamentos do período; lançamento de categoria fora do mapa (de outra família) não entra. */
    public static MovimentoDoMes ate(LocalDate data, List<Lancamento> lancamentos,
                                     Map<Long, TipoMovimento> tipoPorCategoria) {
        LocalDate inicio = data.withDayOfMonth(1);
        Dinheiro recebido = Dinheiro.ZERO;
        Dinheiro pago = Dinheiro.ZERO;
        for (Lancamento l : lancamentos) {
            TipoMovimento tipo = tipoPorCategoria.get(l.categoriaId());
            if (tipo == null || l.data().isBefore(inicio) || l.data().isAfter(data)) {
                continue;
            }
            if (tipo == TipoMovimento.RECEITA) {
                recebido = recebido.somar(l.valor());
            } else {
                pago = pago.somar(l.valor());
            }
        }
        return new MovimentoDoMes(data, recebido, pago);
    }
}
