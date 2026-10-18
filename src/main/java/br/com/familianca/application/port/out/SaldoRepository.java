package br.com.familianca.application.port.out;

import br.com.familianca.domain.model.SaldoMes;
import br.com.familianca.domain.vo.Competencia;

import java.util.List;
import java.util.Optional;

/** Saldos informados da família do usuário logado. */
public interface SaldoRepository {

    List<SaldoMes> listar(int ano);

    SaldoMes salvar(SaldoMes saldo);

    void remover(Competencia competencia);

    /** Quantos meses têm saldo informado, em qualquer ano. */
    long contar();

    /** O mês do primeiro saldo informado (o saldo inicial), em qualquer ano. */
    Optional<Competencia> primeira();
}
