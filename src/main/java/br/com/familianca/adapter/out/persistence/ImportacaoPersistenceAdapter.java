package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.ImportacaoRepository;
import br.com.familianca.application.port.out.UsuarioAtual;
import br.com.familianca.domain.model.LoteImportacao;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Só a família do usuário logado. */
@Component
@Transactional
public class ImportacaoPersistenceAdapter implements ImportacaoRepository {

    private final ImportacaoLoteJpaRepository lotes;
    private final ImportacaoIgnoradaJpaRepository ignoradas;
    private final LancamentoJpaRepository lancamentos;
    private final UsuarioAtual usuario;

    public ImportacaoPersistenceAdapter(ImportacaoLoteJpaRepository lotes, ImportacaoIgnoradaJpaRepository ignoradas,
                                        LancamentoJpaRepository lancamentos, UsuarioAtual usuario) {
        this.lotes = lotes;
        this.ignoradas = ignoradas;
        this.lancamentos = lancamentos;
        this.usuario = usuario;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> chavesExistentes(Long localSaldoId, Collection<String> chaves) {
        Set<String> existentes = new HashSet<>();
        if (chaves.isEmpty()) {
            return existentes;
        }
        existentes.addAll(lancamentos.chavesImportadas(usuario.familiaId(), localSaldoId, chaves));
        existentes.addAll(ignoradas.chavesIgnoradas(usuario.familiaId(), localSaldoId, chaves));
        return existentes;
    }

    @Override
    public LoteImportacao criarLote(Long localSaldoId, LocalDate periodoDe, LocalDate periodoAte, Long membroId) {
        ImportacaoLoteEntity e = new ImportacaoLoteEntity();
        e.familiaId = usuario.familiaId();
        e.localSaldoId = localSaldoId;
        e.membroId = membroId;
        e.periodoDe = periodoDe;
        e.periodoAte = periodoAte;
        return paraDominio(lotes.saveAndFlush(e));
    }

    @Override
    public void atualizarContagens(Long loteId, int importadas, int ignoradas, int confirmadas) {
        ImportacaoLoteEntity e = lotes.findByIdAndFamiliaId(loteId, usuario.familiaId()).orElseThrow();
        e.importadas = importadas;
        e.ignoradas = ignoradas;
        e.confirmadas = confirmadas;
    }

    @Override
    public void registrarIgnorada(Long loteId, Long localSaldoId, String chave) {
        ImportacaoIgnoradaEntity e = new ImportacaoIgnoradaEntity();
        e.familiaId = usuario.familiaId();
        e.localSaldoId = localSaldoId;
        e.loteId = loteId;
        e.chave = chave;
        ignoradas.save(e);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoteImportacao> listarLotes() {
        return lotes.findByFamiliaIdOrderByCriadoEmDescIdDesc(usuario.familiaId()).stream()
                .map(ImportacaoPersistenceAdapter::paraDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LoteImportacao> buscarLote(Long id) {
        return lotes.findByIdAndFamiliaId(id, usuario.familiaId()).map(ImportacaoPersistenceAdapter::paraDominio);
    }

    @Override
    public Desfeito desfazer(Long loteId) {
        int mantidos = lancamentos.countByImportacaoLoteIdAndEditadoAposImportacaoTrue(loteId);
        int removidos = lancamentos.apagarNaoEditadosDoLote(usuario.familiaId(), loteId);
        // As chaves das ignoradas saem com o lote (on delete cascade) e os editados ficam sem lote (set null).
        lotes.apagar(loteId, usuario.familiaId());
        return new Desfeito(removidos, mantidos);
    }

    private static LoteImportacao paraDominio(ImportacaoLoteEntity e) {
        return new LoteImportacao(e.id, e.localSaldoId, e.periodoDe, e.periodoAte, e.importadas, e.ignoradas,
                e.confirmadas, e.membroId, e.criadoEm);
    }
}
