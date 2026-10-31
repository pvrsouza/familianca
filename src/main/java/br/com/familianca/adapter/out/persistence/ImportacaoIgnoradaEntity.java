package br.com.familianca.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Só a chave da linha do extrato que a pessoa ignorou: nenhuma descrição nem valor. */
@Entity
@Table(name = "importacao_ignorada")
public class ImportacaoIgnoradaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long familiaId;
    Long localSaldoId;
    Long loteId;
    String chave;
}
