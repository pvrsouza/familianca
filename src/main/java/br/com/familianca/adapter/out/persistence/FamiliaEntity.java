package br.com.familianca.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

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
    String estimativaAporte = "MEDIA_3M";
    /** Meta de aporte definida à mão por competência (AAAA-MM). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "familia_meta_aporte", joinColumns = @JoinColumn(name = "familia_id"))
    @MapKeyColumn(name = "competencia")
    @Column(name = "percentual")
    Map<String, BigDecimal> metasAporte = new HashMap<>();
    @Column(insertable = false, updatable = false)
    OffsetDateTime criadoEm;
}
