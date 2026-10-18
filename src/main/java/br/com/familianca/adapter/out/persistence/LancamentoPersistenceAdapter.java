package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.DescricaoUsada;
import br.com.familianca.application.port.out.LancamentoRepository;
import br.com.familianca.application.port.out.UsuarioAtual;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.Parcela;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lançamentos da família do usuário logado; de outra família = não encontrado. */
@Component
@Transactional
public class LancamentoPersistenceAdapter implements LancamentoRepository {

    private final LancamentoJpaRepository jpa;
    private final UsuarioAtual usuario;

    public LancamentoPersistenceAdapter(LancamentoJpaRepository jpa, UsuarioAtual usuario) {
        this.jpa = jpa;
        this.usuario = usuario;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Lancamento> listar(LocalDate de, LocalDate ate) {
        return jpa.findByFamiliaIdAndDataBetween(usuario.familiaId(), de, ate).stream()
                .map(LancamentoPersistenceAdapter::paraDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Lancamento> buscar(Long id) {
        return jpa.findByIdAndFamiliaId(id, usuario.familiaId()).map(LancamentoPersistenceAdapter::paraDominio);
    }

    @Override
    public Lancamento salvar(Lancamento l) {
        return paraDominio(jpa.save(paraEntidade(l)));
    }

    @Override
    public List<Lancamento> salvarTodos(List<Lancamento> lancamentos) {
        return jpa.saveAll(lancamentos.stream().map(this::paraEntidade).toList()).stream()
                .map(LancamentoPersistenceAdapter::paraDominio).toList();
    }

    @Override
    public void remover(Long id) {
        jpa.findByIdAndFamiliaId(id, usuario.familiaId()).ifPresent(jpa::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Lancamento> listarParcelamento(UUID parcelamentoId) {
        return jpa.findByFamiliaIdAndParcelamentoId(usuario.familiaId(), parcelamentoId).stream()
                .map(LancamentoPersistenceAdapter::paraDominio).toList();
    }

    @Override
    public void removerParcelamento(UUID parcelamentoId) {
        jpa.deleteAll(jpa.findByFamiliaIdAndParcelamentoId(usuario.familiaId(), parcelamentoId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Lancamento> listarOcorrenciasConfirmadas(int ano) {
        return listarOcorrenciasConfirmadas(Competencia.of(ano, 1), Competencia.of(ano, 12));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Lancamento> listarOcorrenciasConfirmadas(Competencia de, Competencia ate) {
        return jpa.findByFamiliaIdAndCompetenciaRecorrenciaBetween(usuario.familiaId(), de.toString(), ate.toString())
                .stream().map(LancamentoPersistenceAdapter::paraDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeOcorrencia(Long recorrenciaId, Competencia competencia) {
        return jpa.existsByRecorrenciaIdAndCompetenciaRecorrencia(recorrenciaId, competencia.toString());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeDaRecorrencia(Long recorrenciaId) {
        return jpa.existsByRecorrenciaId(recorrenciaId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> descricoesMaisUsadas(Long categoriaId, int limite) {
        return jpa.descricoesMaisUsadas(usuario.familiaId(), categoriaId, PageRequest.of(0, limite));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DescricaoUsada> buscarDescricoes(Long categoriaId, String termo, int limite) {
        return jpa.buscarDescricoes(usuario.familiaId(), categoriaId, termo, FuncoesBusca.contem(termo),
                FuncoesBusca.minimo(termo), PageRequest.of(0, limite));
    }

    private LancamentoEntity paraEntidade(Lancamento l) {
        LancamentoEntity e;
        if (l.id() != null) {
            e = jpa.findByIdAndFamiliaId(l.id(), usuario.familiaId())
                    .orElseThrow(() -> new NaoEncontradoException("Lançamento", l.id()));
        } else {
            e = new LancamentoEntity();
            e.familiaId = usuario.familiaId();
        }
        e.categoriaId = l.categoriaId();
        e.membroId = l.membroId();
        e.descricao = l.descricao();
        e.valor = l.valor().valor();
        e.data = l.data();
        e.recorrenciaId = l.recorrenciaId();
        e.competenciaRecorrencia = l.competenciaRecorrencia() != null ? l.competenciaRecorrencia().toString() : null;
        e.parcelamentoId = l.parcela() != null ? l.parcela().parcelamentoId() : null;
        e.parcelaNumero = l.parcela() != null ? (short) l.parcela().numero() : null;
        e.parcelaTotal = l.parcela() != null ? (short) l.parcela().total() : null;
        e.observacao = l.observacao();
        return e;
    }

    private static Lancamento paraDominio(LancamentoEntity e) {
        return new Lancamento(e.id, e.categoriaId, e.membroId, e.descricao, Dinheiro.of(e.valor), e.data,
                e.recorrenciaId, e.competenciaRecorrencia != null ? Competencia.parse(e.competenciaRecorrencia) : null,
                e.parcelamentoId != null ? new Parcela(e.parcelamentoId, e.parcelaNumero, e.parcelaTotal) : null,
                e.observacao);
    }
}
