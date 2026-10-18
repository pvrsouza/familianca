package br.com.familianca.domain.model;

import java.time.LocalDate;

import static br.com.familianca.domain.Validacoes.obrigatorio;

/**
 * Plano contratado pela família. O plano só vale enquanto a assinatura está em teste ou ativa e dentro da validade;
 * fora disso, vale o {@link Plano#GRATUITO}. Validade nula = sem vencimento (ex.: ativada à mão pelo ADMIN).
 */
public record Assinatura(Plano plano, StatusAssinatura status, LocalDate validaAte) {

    public Assinatura {
        obrigatorio(plano, "plano");
        obrigatorio(status, "status da assinatura");
    }

    /** Família nova: plano completo em teste por alguns dias. */
    public static Assinatura teste(LocalDate hoje, int dias) {
        return new Assinatura(Plano.PREMIUM, StatusAssinatura.TESTE, hoje.plusDays(dias));
    }

    public Plano planoEmVigor(LocalDate hoje) {
        boolean vigente = status != StatusAssinatura.CANCELADA && (validaAte == null || !hoje.isAfter(validaAte));
        return vigente ? plano : Plano.GRATUITO;
    }
}
