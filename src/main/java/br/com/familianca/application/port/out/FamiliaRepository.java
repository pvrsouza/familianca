package br.com.familianca.application.port.out;

import br.com.familianca.domain.model.Familia;

import java.util.List;
import java.util.Optional;

/** Sem filtro implícito: quem chama passa a família (a do usuário logado, ou qualquer uma no caso do ADMIN). */
public interface FamiliaRepository {

    Familia salvar(Familia familia);

    Optional<Familia> buscarPorId(Long id);

    List<Familia> listar();

    /** Trava a família até o fim da transação (as escritas automáticas da mesma família não correm juntas). */
    void travar(Long id);
}
