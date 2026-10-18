package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface MfaCodigoRecuperacaoJpaRepository extends JpaRepository<MfaCodigoRecuperacaoEntity, Long> {

    void deleteByUsuarioId(Long usuarioId);

    /** Update condicional: duas requisições com o mesmo código, só uma marca (retorna 1). */
    @Modifying
    @Query("""
            update MfaCodigoRecuperacaoEntity c set c.usadoEm = :agora
            where c.usuarioId = :usuarioId and c.codigoHash = :hash and c.usadoEm is null""")
    int marcarUsado(@Param("usuarioId") Long usuarioId, @Param("hash") String hash, @Param("agora") OffsetDateTime agora);
}
