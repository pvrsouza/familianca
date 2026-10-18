package br.com.familianca.domain.service;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.vo.Dinheiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Divide uma compra em parcelas mensais iguais; os centavos que sobram da divisão vão para a primeira. */
public final class GeradorParcelas {

    public static final int MAXIMO_PARCELAS = 60;

    public record ParcelaGerada(int numero, Dinheiro valor, LocalDate data) {
    }

    public List<ParcelaGerada> gerar(Dinheiro valorTotal, int parcelas, LocalDate primeiraData) {
        if (parcelas < 2 || parcelas > MAXIMO_PARCELAS) {
            throw new RegraNegocioException("O número de parcelas deve estar entre 2 e " + MAXIMO_PARCELAS);
        }
        if (!valorTotal.ehPositivo()) {
            throw new RegraNegocioException("O valor deve ser maior que zero");
        }
        // Para baixo: a primeira (que absorve a sobra) nunca fica menor que as outras nem negativa.
        Dinheiro valorParcela = Dinheiro.of(valorTotal.valor().divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.DOWN));
        if (!valorParcela.ehPositivo()) {
            throw new RegraNegocioException("O valor é pequeno demais para " + parcelas + " parcelas");
        }
        Dinheiro primeira = valorTotal.subtrair(valorParcela.multiplicar(parcelas - 1));
        List<ParcelaGerada> geradas = new ArrayList<>(parcelas);
        for (int i = 0; i < parcelas; i++) {
            // A partir da data original: 31/01 → 28/02 → 31/03 (não "encolhe" para 28 nos meses seguintes).
            geradas.add(new ParcelaGerada(i + 1, i == 0 ? primeira : valorParcela, primeiraData.plusMonths(i)));
        }
        return geradas;
    }
}
