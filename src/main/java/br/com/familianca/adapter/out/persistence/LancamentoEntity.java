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
import java.util.UUID;

@Entity
@Table(name = "lancamento")
public class LancamentoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(updatable = false)
    Long familiaId;
    Long categoriaId;
    Long membroId;
    String descricao;
    BigDecimal valor;
    LocalDate data;
    Long recorrenciaId;
    String competenciaRecorrencia;
    UUID parcelamentoId;
    Short parcelaNumero;
    Short parcelaTotal;
    String observacao;
    String nfCompetencia;
    LocalDate nfEmissao;
    @Column(insertable = false, updatable = false)
    OffsetDateTime criadoEm;
}
