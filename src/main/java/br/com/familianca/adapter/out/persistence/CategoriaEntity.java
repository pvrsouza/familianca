package br.com.familianca.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "categoria")
public class CategoriaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(updatable = false)
    Long familiaId;
    Long grupoId;
    String nome;
    int ordem;
    boolean ativa;
    boolean tributavel;
    BigDecimal aliquota;
    boolean recorrente;
    String estimativa;
}
