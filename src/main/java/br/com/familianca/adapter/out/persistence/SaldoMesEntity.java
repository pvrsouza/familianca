package br.com.familianca.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "saldo_mes")
@IdClass(SaldoMesEntity.Chave.class)
public class SaldoMesEntity {

    @Id
    Long familiaId;
    @Id
    String competencia;
    BigDecimal valor;
    String observacao;

    public record Chave(Long familiaId, String competencia) implements Serializable {

        public Chave() {
            this(null, null);
        }
    }
}
