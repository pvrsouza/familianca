package br.com.familianca.application.port.out;

import br.com.familianca.domain.model.Membro;

import java.util.List;
import java.util.Optional;

/** Sem filtro implícito: o login e o cadastro usam antes de existir sessão. */
public interface MembroRepository {

    Membro salvar(Membro membro);

    Optional<Membro> buscarPorUsuario(Long usuarioId);

    /** Membro da família informada; de outra família = vazio. */
    Optional<Membro> buscar(Long familiaId, Long id);

    /** Ativos e inativos, por nome. */
    List<Membro> listar(Long familiaId);

    long contarAtivos(Long familiaId);

    long contarDonosAtivos(Long familiaId);
}
