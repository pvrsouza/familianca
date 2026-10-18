package br.com.familianca.application.port.in;

import br.com.familianca.domain.model.LocalSaldo;
import br.com.familianca.domain.model.TipoLocalSaldo;

import java.util.List;

/** Onde está o dinheiro da família (contas, poupança, cofrinho...). Consultar: qualquer membro. Alterar: só DONO. */
public interface GerenciarLocaisSaldo {

    List<LocalSaldo> listar();

    LocalSaldo criar(String nome, TipoLocalSaldo tipo, Long membroId);

    LocalSaldo atualizar(Long id, String nome, TipoLocalSaldo tipo, Long membroId, boolean ativo);

    /** Só se nunca entrou num saldo; se já entrou, desativar. */
    void remover(Long id);
}
