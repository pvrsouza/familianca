package br.com.familianca.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "membro")
public class MembroEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(updatable = false)
    Long familiaId;
    @Column(updatable = false)
    Long usuarioId;
    String papel;
    boolean ativo;
    @Column(insertable = false, updatable = false)
    OffsetDateTime entrouEm;
}
