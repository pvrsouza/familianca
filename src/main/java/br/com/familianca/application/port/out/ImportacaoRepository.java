package br.com.familianca.application.port.out;

import br.com.familianca.domain.model.LoteImportacao;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Lotes de importação e chaves das linhas ignoradas, da família do usuário logado. */
public interface ImportacaoRepository {

    /** Das chaves informadas, as que já existem no lugar (em lançamento importado ou em linha ignorada). */
    Set<String> chavesExistentes(Long localSaldoId, Collection<String> chaves);

    LoteImportacao criarLote(Long localSaldoId, LocalDate periodoDe, LocalDate periodoAte, Long membroId);

    void atualizarContagens(Long loteId, int importadas, int ignoradas, int confirmadas);

    void registrarIgnorada(Long loteId, Long localSaldoId, String chave);

    List<LoteImportacao> listarLotes();

    Optional<LoteImportacao> buscarLote(Long id);

    /**
     * Apaga os lançamentos do lote que não foram editados depois, as chaves das ignoradas e o lote. Os editados
     * ficam (e continuam com a chave).
     */
    Desfeito desfazer(Long loteId);

    record Desfeito(int removidos, int mantidosPorEdicao) {
    }
}
