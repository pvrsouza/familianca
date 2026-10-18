package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SaldoMesJpaRepository extends JpaRepository<SaldoMesEntity, SaldoMesEntity.Chave> {

    List<SaldoMesEntity> findByFamiliaIdAndCompetenciaBetween(Long familiaId, String de, String ate);
}
