package br.com.familianca.application.port.out;

import br.com.familianca.domain.model.LocalSaldo;

import java.util.List;
import java.util.Optional;

/** Lugares onde está o dinheiro da família do usuário logado. */
public interface LocalSaldoRepository {

    List<LocalSaldo> listar();

    Optional<LocalSaldo> buscar(Long id);

    LocalSaldo salvar(LocalSaldo local);

    void remover(Long id);

    boolean existeNome(String nome, Long ignorarId);

    /** Se já entrou em algum saldo de mês. */
    boolean emUso(Long id);
}
