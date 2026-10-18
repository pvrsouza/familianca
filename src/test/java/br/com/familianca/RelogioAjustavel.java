package br.com.familianca;

import br.com.familianca.application.port.out.Relogio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** "Agora" é o meio-dia de "hoje" em Brasília. */
public class RelogioAjustavel implements Relogio {

    private LocalDate hoje = LocalDate.of(2026, 10, 17);

    @Override
    public LocalDate hoje() {
        return hoje;
    }

    @Override
    public OffsetDateTime agora() {
        return hoje.atTime(12, 0).atOffset(ZoneOffset.ofHours(-3));
    }

    public void definir(LocalDate data) {
        this.hoje = data;
    }
}
