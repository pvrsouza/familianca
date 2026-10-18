package br.com.familianca.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "familia")
public class FamiliaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String nome;
    String plano;
    String statusAssinatura;
    LocalDate assinaturaValidaAte;
    BigDecimal metaAportePercentual;
    String estimativa = "MEDIA";
    @Column(insertable = false, updatable = false)
    OffsetDateTime criadoEm;
}
