package br.com.familianca.application.port.in;

import br.com.familianca.domain.model.MovimentoDoMes;
import br.com.familianca.domain.model.SaldoMes;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;

import java.time.LocalDate;
import java.util.List;

/** Saldo em conta no início de cada mês. Consultar: qualquer membro. Alterar: só DONO. */
public interface GerenciarSaldos {

    List<SaldoMes> listar(int ano);

    /**
     * Cria ou substitui o saldo da competência; sem dataReferencia, o valor é o do dia 1º. Com porLocal (o saldo de
     * cada conta, cofrinho...), o total é a soma deles e valor é ignorado.
     */
    SaldoMes definir(Competencia competencia, Dinheiro valor, List<SaldoMes.PorLocal> porLocal, String observacao,
                     LocalDate dataReferencia);

    /** O que foi lançado do dia 1º até a data: mostra a conta do saldo de um dia para o do dia 1º. */
    MovimentoDoMes movimentoAte(LocalDate data);

    void remover(Competencia competencia);
}
