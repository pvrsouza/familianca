package br.com.familianca.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

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
    LocalDate dataReferencia;
    /** Saldo de outro dia guardado só para conferir; os dois juntos ou nenhum. */
    LocalDate conferenciaDia;
    BigDecimal conferenciaValor;

    /** Saldo de cada lugar (conta, cofrinho...) no mês; vazio = só o total. */
    @ElementCollection
    @CollectionTable(name = "saldo_mes_local", joinColumns = {
            @JoinColumn(name = "familia_id", referencedColumnName = "familiaId"),
            @JoinColumn(name = "competencia", referencedColumnName = "competencia")})
    @MapKeyColumn(name = "local_id")
    @Column(name = "valor")
    Map<Long, BigDecimal> porLocal = new HashMap<>();

    public record Chave(Long familiaId, String competencia) implements Serializable {

        public Chave() {
            this(null, null);
        }
    }
}
