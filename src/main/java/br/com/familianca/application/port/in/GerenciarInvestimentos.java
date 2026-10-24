package br.com.familianca.application.port.in;

import br.com.familianca.domain.model.CarteiraAnual;
import br.com.familianca.domain.model.Investimento;
import br.com.familianca.domain.model.MovimentoInvestimento;
import br.com.familianca.domain.model.TipoInvestimento;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;

import java.util.List;

/** Carteira da família (recurso INVESTIMENTOS). Consultar: qualquer membro. Alterar: só DONO. */
public interface GerenciarInvestimentos {

    List<Investimento> listar();

    Investimento criar(String nome, TipoInvestimento tipo);

    Investimento atualizar(Long id, String nome, TipoInvestimento tipo, boolean ativo);

    /** Só sem movimentos; com eles, desative. */
    void remover(Long id);

    List<MovimentoInvestimento> listarMovimentos(Long investimentoId);

    /** Cria ou substitui o movimento da competência. */
    MovimentoInvestimento registrarMovimento(MovimentoInvestimento movimento);

    void removerMovimento(Long investimentoId, Competencia competencia);

    /**
     * Resgatou tudo no mês: registra o saldo zero e o resgate do saldo do mês anterior (volta para a conta, no fluxo de
     * caixa) e encerra o investimento. Dali em diante ele fica zerado. Sem nenhum registro, só encerra.
     */
    Investimento encerrar(Long id, Competencia mes);

    CarteiraAnual carteira(int ano);

    /**
     * Saldos do mês, um por investimento ativo que já existia nele, para atualizar todos de uma vez. mes nulo = o mês
     * de referência (o anterior ao atual: no começo de novembro, o extrato em mãos é o de outubro).
     */
    SaldosDoMes saldosDoMes(Competencia mes);

    /** Grava o saldo do extrato (e o aporte) de cada investimento no mês, numa vez só. Só até o mês atual. */
    SaldosDoMes atualizarSaldos(Competencia mes, List<SaldoInformado> saldos);

    /** @param aporte nulo = não colocou nem tirou */
    record SaldoInformado(Long investimentoId, Dinheiro saldo, Dinheiro aporte) {
    }

    /** @param atualizado tem o saldo do extrato informado no mês (mesmo que igual ao anterior) */
    record LinhaSaldo(Investimento investimento, Dinheiro saldoAnterior, Dinheiro aporte, Dinheiro saldoInformado,
                      boolean atualizado) {
    }

    record SaldosDoMes(Competencia competencia, Competencia referencia, List<LinhaSaldo> linhas) {

        public long atualizados() {
            return linhas.stream().filter(LinhaSaldo::atualizado).count();
        }
    }
}
