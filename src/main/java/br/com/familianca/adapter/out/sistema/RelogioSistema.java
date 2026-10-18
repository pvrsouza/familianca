package br.com.familianca.adapter.out.sistema;

import br.com.familianca.application.port.out.Relogio;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
public class RelogioSistema implements Relogio {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Override
    public LocalDate hoje() {
        return LocalDate.now(FUSO);
    }

    @Override
    public OffsetDateTime agora() {
        return OffsetDateTime.now(FUSO);
    }
}
