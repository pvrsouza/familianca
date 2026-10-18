package br.com.familianca.application.port.out;

import br.com.familianca.domain.model.SaldoMes;
import br.com.familianca.domain.vo.Competencia;

import java.util.List;

/** Saldos informados da família do usuário logado. */
public interface SaldoRepository {

    List<SaldoMes> listar(int ano);

    SaldoMes salvar(SaldoMes saldo);

    void remover(Competencia competencia);
}
