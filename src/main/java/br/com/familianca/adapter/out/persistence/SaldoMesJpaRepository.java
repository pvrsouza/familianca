package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SaldoMesJpaRepository extends JpaRepository<SaldoMesEntity, SaldoMesEntity.Chave> {

    List<SaldoMesEntity> findByFamiliaIdAndCompetenciaBetween(Long familiaId, String de, String ate);

    long countByFamiliaId(Long familiaId);

    Optional<SaldoMesEntity> findFirstByFamiliaIdOrderByCompetenciaAsc(Long familiaId);
}
