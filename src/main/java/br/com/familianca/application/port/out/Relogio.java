package br.com.familianca.application.port.out;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Fonte da data e hora atuais (fuso America/Sao_Paulo). Permite fixar "hoje" nos testes. */
public interface Relogio {

    LocalDate hoje();

    OffsetDateTime agora();
}
