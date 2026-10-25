package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface RegistroAcessoJpaRepository extends JpaRepository<RegistroAcessoEntity, Long> {

    @Modifying
    @Query("delete from RegistroAcessoEntity r where r.momento < :limite")
    int apagarAnterioresA(@Param("limite") OffsetDateTime limite);
}
