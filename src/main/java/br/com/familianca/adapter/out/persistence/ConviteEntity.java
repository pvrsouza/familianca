package br.com.familianca.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "convite")
public class ConviteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long familiaId;
    String email;
    String papel;
    String tokenHash;
    String status;
    OffsetDateTime expiraEm;
    Long convidadoPor;
    @Column(insertable = false, updatable = false)
    OffsetDateTime criadoEm;
}
