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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "recorrencia")
public class RecorrenciaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(updatable = false)
    Long familiaId;
    Long categoriaId;
    Long membroId;
    String descricao;
    BigDecimal valor;
    short dia;
    String frequencia;
    String inicio;
    String fim;
    String observacao;
    LocalDate confirmarSozinhaDesde;
    /** Competência (AAAA-MM) da última mudança de valor; nulo = desde sempre. */
    String valorDesde;
    /** Competências puladas (AAAA-MM). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recorrencia_excecao", joinColumns = @JoinColumn(name = "recorrencia_id"))
    @Column(name = "competencia")
    Set<String> puladas = new HashSet<>();
    boolean semVencimento;
    String formaPagamento;
    /** Regra própria de estimativa; nulo = segue a da categoria. */
    String estimativa;
    /** Vencimento ajustado por competência (AAAA-MM). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recorrencia_vencimento", joinColumns = @JoinColumn(name = "recorrencia_id"))
    @MapKeyColumn(name = "competencia")
    @Column(name = "vencimento")
    Map<String, LocalDate> vencimentos = new HashMap<>();
    /** Valor previsto ajustado por competência (AAAA-MM). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recorrencia_valor", joinColumns = @JoinColumn(name = "recorrencia_id"))
    @MapKeyColumn(name = "competencia")
    @Column(name = "valor")
    Map<String, BigDecimal> valores = new HashMap<>();
}
