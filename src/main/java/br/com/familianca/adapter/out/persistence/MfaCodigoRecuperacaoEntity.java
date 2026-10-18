package br.com.familianca.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mfa_codigo_recuperacao")
public class MfaCodigoRecuperacaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long usuarioId;
    String codigoHash;
    OffsetDateTime usadoEm;
}
