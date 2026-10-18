package br.com.familianca.application.port.out;

import br.com.familianca.domain.model.Recorrencia;

import java.util.List;
import java.util.Optional;

/** Recorrências (com as competências puladas) da família do usuário logado. */
public interface RecorrenciaRepository {

    List<Recorrencia> listar();

    Optional<Recorrencia> buscar(Long id);

    Recorrencia salvar(Recorrencia recorrencia);

    void remover(Long id);

    /** Descrições das recorrências da categoria que contêm o termo ou se parecem com ele (sem acento, minúsculo). */
    List<DescricaoUsada> buscarDescricoes(Long categoriaId, String termo, int limite);
}
