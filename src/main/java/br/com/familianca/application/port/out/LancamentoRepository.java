package br.com.familianca.application.port.out;

import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.vo.Competencia;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lançamentos da família do usuário logado. */
public interface LancamentoRepository {

    /** Com data entre de e ate (inclusive). */
    List<Lancamento> listar(LocalDate de, LocalDate ate);

    Optional<Lancamento> buscar(Long id);

    Lancamento salvar(Lancamento lancamento);

    List<Lancamento> salvarTodos(List<Lancamento> lancamentos);

    void remover(Long id);

    List<Lancamento> listarParcelamento(UUID parcelamentoId);

    void removerParcelamento(UUID parcelamentoId);

    /** Ocorrências confirmadas de recorrências com competência no ano (a data pode ser de outro ano). */
    List<Lancamento> listarOcorrenciasConfirmadas(int ano);

    /** Ocorrências confirmadas com competência entre de e ate (inclusive). */
    List<Lancamento> listarOcorrenciasConfirmadas(Competencia de, Competencia ate);

    boolean existeOcorrencia(Long recorrenciaId, Competencia competencia);

    boolean existeDaRecorrencia(Long recorrenciaId);

    /** Descrições distintas já lançadas na categoria, as mais usadas primeiro. */
    List<String> descricoesMaisUsadas(Long categoriaId, int limite);

    /** Lançamento vindo de um extrato: guarda de que lote veio e a chave que impede importar de novo. */
    Lancamento salvarImportado(Lancamento lancamento, Long loteId, Long localSaldoId, String chave);

    /** Lançamentos com data entre de e ate (inclusive) que não vieram de importação (digitados à mão). */
    List<Lancamento> listarDigitados(LocalDate de, LocalDate ate);

    /**
     * Categoria de cada lançamento cuja descrição (sem acento, minúscula) está na lista, o mais recente primeiro.
     */
    List<CategoriaPorDescricao> categoriasPorDescricao(Collection<String> descricoes);

    /** Descrições da categoria que contêm o termo ou se parecem com ele; termo já sem acento e minúsculo. */
    List<DescricaoUsada> buscarDescricoes(Long categoriaId, String termo, int limite);
}
