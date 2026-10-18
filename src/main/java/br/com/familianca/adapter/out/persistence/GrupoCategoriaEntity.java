package br.com.familianca.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "grupo_categoria")
public class GrupoCategoriaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(updatable = false)
    Long familiaId;
    String tipo;
    String natureza;
    String nome;
    boolean empresa;
    int ordem;
    boolean ativo;
}
