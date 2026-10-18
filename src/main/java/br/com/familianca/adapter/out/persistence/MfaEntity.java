package br.com.familianca.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mfa")
public class MfaEntity {

    @Id
    Long usuarioId;
    String segredoCifrado;
    boolean ativo;
    Long ultimoPasso;
    @Column(insertable = false, updatable = false)
    OffsetDateTime criadoEm;
}
