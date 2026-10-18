package br.com.familianca.application.port.in;

import br.com.familianca.domain.model.SaldoMes;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;

import java.util.List;

/** Saldo em conta no início de cada mês. Consultar: qualquer membro. Alterar: só DONO. */
public interface GerenciarSaldos {

    List<SaldoMes> listar(int ano);

    /** Cria ou substitui o saldo da competência. */
    SaldoMes definir(Competencia competencia, Dinheiro valor, String observacao);

    void remover(Competencia competencia);
}
