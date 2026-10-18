package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovimentoInvestimentoJpaRepository
        extends JpaRepository<MovimentoInvestimentoEntity, MovimentoInvestimentoEntity.Chave> {

    List<MovimentoInvestimentoEntity> findByInvestimentoId(Long investimentoId);

    @Query("""
            select m from MovimentoInvestimentoEntity m
            where m.competencia <= :ate
              and m.investimentoId in (select i.id from InvestimentoEntity i where i.familiaId = :familiaId)""")
    List<MovimentoInvestimentoEntity> listarAte(@Param("familiaId") Long familiaId, @Param("ate") String ate);
}
