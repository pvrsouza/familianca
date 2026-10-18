package br.com.familianca.application.port.out;

import br.com.familianca.domain.model.Convite;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ConviteRepository {

    Convite salvar(Convite convite);

    Optional<Convite> buscarPorTokenHash(String tokenHash);

    Optional<Convite> buscar(Long familiaId, Long id);

    /** Pendentes ainda válidos. */
    List<Convite> listarPendentes(Long familiaId, OffsetDateTime agora);

    /** Pendente (válido ou expirado) para o e-mail, que impede outro convite igual. */
    Optional<Convite> buscarPendente(Long familiaId, String email);
}
