package br.com.familianca.application.port.in;

import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.NotaFiscal;
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

    /**
     * Descrições já usadas na categoria, para sugerir ao lançar e evitar nomes diferentes para a mesma coisa. Sem
     * termo: as 3 mais comuns. Com termo: as que contêm parte dele ou se parecem com ele (sem ligar para acento,
     * maiúscula ou erro de digitação), nos lançamentos e recorrências. Sem repetir variações da mesma descrição.
     */
    List<String> descricoesUsadas(Long categoriaId, String termo);

    /**
     * termo: parte da descrição, observação, categoria, grupo ou pessoa, sem ligar para acento e maiúscula; nulo =
     * todos. origem: de onde veio o lançamento; nulo = todos.
     */
    record Filtro(LocalDate de, LocalDate ate, Long categoriaId, Long grupoId, Long membroId, TipoMovimento tipo,
                  String termo, Origem origem) {

        public Filtro(LocalDate de, LocalDate ate, Long categoriaId, Long grupoId, Long membroId, TipoMovimento tipo) {
            this(de, ate, categoriaId, grupoId, membroId, tipo, null, null);
        }
    }

    /** AVULSO: lançado uma vez; RECORRENTE: confirmação de algo que se repete; PARCELADO: parcela de uma compra. */
    enum Origem { AVULSO, RECORRENTE, PARCELADO }

    /** membroId nulo = o membro logado. notaFiscal: só receita tributável; nulo = não informada. */
    record DadosLancamento(Long categoriaId, Long membroId, String descricao, Dinheiro valor, LocalDate data,
                           String observacao, NotaFiscal notaFiscal) {

        public DadosLancamento(Long categoriaId, Long membroId, String descricao, Dinheiro valor, LocalDate data,
                               String observacao) {
            this(categoriaId, membroId, descricao, valor, data, observacao, null);
        }
    }

    /** @param vencimento da ocorrência que o lançamento confirmou (despesa com vencimento); nulo = não tem */
    record LancamentoDetalhado(Lancamento lancamento, Categoria categoria, Grupo grupo, Membro membro,
                               LocalDate vencimento) {

        public LancamentoDetalhado(Lancamento lancamento, Categoria categoria, Grupo grupo, Membro membro) {
            this(lancamento, categoria, grupo, membro, null);
        }
    }
}
