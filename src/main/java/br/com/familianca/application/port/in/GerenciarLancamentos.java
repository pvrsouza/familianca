package br.com.familianca.application.port.in;

import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.vo.Dinheiro;

import java.time.LocalDate;
import java.util.List;

/**
 * Receitas e despesas da família. Todos veem tudo; MEMBRO lança, edita e exclui só os próprios (membroId dele),
 * DONO mexe em qualquer um.
 */
public interface GerenciarLancamentos {

    /** Mais recentes primeiro. de/ate nulos = ano atual; demais filtros nulos = todos. */
    List<LancamentoDetalhado> listar(Filtro filtro);

    LancamentoDetalhado buscar(Long id);

    LancamentoDetalhado lancar(DadosLancamento dados);

    /** Compra parcelada: dados.valor é o total, dividido em parcelas mensais a partir de dados.data. */
    List<LancamentoDetalhado> lancarParcelado(DadosLancamento dados, int parcelas);

    /** Só a parcela editada muda; recorrência e parcelamento de origem se mantêm. */
    LancamentoDetalhado atualizar(Long id, DadosLancamento dados);

    /** todasAsParcelas: exclui o parcelamento inteiro a que o lançamento pertence. */
    void remover(Long id, boolean todasAsParcelas);

    int anoAtual();

    record Filtro(LocalDate de, LocalDate ate, Long categoriaId, Long grupoId, Long membroId, TipoMovimento tipo) {
    }

    /** membroId nulo = o membro logado. */
    record DadosLancamento(Long categoriaId, Long membroId, String descricao, Dinheiro valor, LocalDate data,
                           String observacao) {
    }

    record LancamentoDetalhado(Lancamento lancamento, Categoria categoria, Grupo grupo, Membro membro) {
    }
}
