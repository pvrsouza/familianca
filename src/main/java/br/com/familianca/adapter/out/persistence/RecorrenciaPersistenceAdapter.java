package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.DescricaoUsada;
import br.com.familianca.application.port.out.RecorrenciaRepository;
import br.com.familianca.application.port.out.UsuarioAtual;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.model.Frequencia;
import br.com.familianca.domain.model.Recorrencia;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Recorrências da família do usuário logado; de outra família = não encontrada. */
@Component
@Transactional
public class RecorrenciaPersistenceAdapter implements RecorrenciaRepository {

    private final RecorrenciaJpaRepository jpa;
    private final UsuarioAtual usuario;

    public RecorrenciaPersistenceAdapter(RecorrenciaJpaRepository jpa, UsuarioAtual usuario) {
        this.jpa = jpa;
        this.usuario = usuario;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Recorrencia> listar() {
        return jpa.findByFamiliaId(usuario.familiaId()).stream()
                .map(RecorrenciaPersistenceAdapter::paraDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Recorrencia> buscar(Long id) {
        return jpa.findByIdAndFamiliaId(id, usuario.familiaId()).map(RecorrenciaPersistenceAdapter::paraDominio);
    }

    @Override
    public Recorrencia salvar(Recorrencia r) {
        RecorrenciaEntity e;
        if (r.id() != null) {
            e = jpa.findByIdAndFamiliaId(r.id(), usuario.familiaId())
                    .orElseThrow(() -> new NaoEncontradoException("Recorrência não encontrada: " + r.id()));
        } else {
            e = new RecorrenciaEntity();
            e.familiaId = usuario.familiaId();
        }
        e.categoriaId = r.categoriaId();
        e.membroId = r.membroId();
        e.descricao = r.descricao();
        e.valor = r.valor().valor();
        e.dia = (short) r.dia();
        e.frequencia = r.frequencia().name();
        e.inicio = r.inicio().toString();
        e.fim = r.fim() != null ? r.fim().toString() : null;
        e.observacao = r.observacao();
        e.confirmarSozinhaDesde = r.confirmarSozinhaDesde();
        e.valorDesde = r.valorDesde() != null ? r.valorDesde().toString() : null;
        e.puladas.clear();
        r.puladas().forEach(c -> e.puladas.add(c.toString()));
        return paraDominio(jpa.save(e));
    }

    @Override
    public void remover(Long id) {
        jpa.findByIdAndFamiliaId(id, usuario.familiaId()).ifPresent(jpa::delete);
    }

    private static Recorrencia paraDominio(RecorrenciaEntity e) {
        return new Recorrencia(e.id, e.categoriaId, e.membroId, e.descricao, Dinheiro.of(e.valor), e.dia,
                Frequencia.valueOf(e.frequencia), Competencia.parse(e.inicio),
                e.fim != null ? Competencia.parse(e.fim) : null, e.observacao,
                e.puladas.stream().map(Competencia::parse).collect(Collectors.toSet()), e.confirmarSozinhaDesde,
                e.valorDesde != null ? Competencia.parse(e.valorDesde) : null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DescricaoUsada> buscarDescricoes(Long categoriaId, String termo, int limite) {
        return jpa.buscarDescricoes(usuario.familiaId(), categoriaId, termo, FuncoesBusca.contem(termo),
                FuncoesBusca.minimo(termo), PageRequest.of(0, limite));
    }
}
