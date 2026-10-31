package br.com.familianca.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "importacao_lote")
public class ImportacaoLoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(updatable = false)
    Long familiaId;
    @Column(updatable = false)
    Long localSaldoId;
    @Column(updatable = false)
    Long membroId;
    LocalDate periodoDe;
    LocalDate periodoAte;
    int importadas;
    int ignoradas;
    int confirmadas;
    @Column(insertable = false, updatable = false)
    OffsetDateTime criadoEm;
}
