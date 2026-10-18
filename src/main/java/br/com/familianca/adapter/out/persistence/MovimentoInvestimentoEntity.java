package br.com.familianca.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "movimento_investimento")
@IdClass(MovimentoInvestimentoEntity.Chave.class)
public class MovimentoInvestimentoEntity {

    @Id
    Long investimentoId;
    @Id
    String competencia;
    BigDecimal aporte;
    BigDecimal rentabilidade;
    BigDecimal saldoInformado;

    public record Chave(Long investimentoId, String competencia) implements Serializable {

        public Chave() {
            this(null, null);
        }
    }
}
