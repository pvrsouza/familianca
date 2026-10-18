package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.PlanoContasRepository;
import br.com.familianca.application.port.out.UsuarioAtual;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Estimativa;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Natureza;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.service.CatalogoCategorias.ModeloCategoria;
import br.com.familianca.domain.service.CatalogoCategorias.ModeloGrupo;
import br.com.familianca.domain.vo.Percentual;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Grupos e categorias da família do usuário logado; de outra família = não encontrado. */
@Component
@Transactional
public class PlanoContasPersistenceAdapter implements PlanoContasRepository {

    /** Nenhum id real é negativo: "não ignorar nenhum" nas consultas de nome repetido. */
    private static final long NENHUM = -1L;

    private final GrupoCategoriaJpaRepository grupos;
    private final CategoriaJpaRepository categorias;
    private final LancamentoJpaRepository lancamentos;
    private final RecorrenciaJpaRepository recorrencias;
    private final UsuarioAtual usuario;

    public PlanoContasPersistenceAdapter(GrupoCategoriaJpaRepository grupos, CategoriaJpaRepository categorias,
                                         LancamentoJpaRepository lancamentos, RecorrenciaJpaRepository recorrencias,
                                         UsuarioAtual usuario) {
        this.grupos = grupos;
        this.categorias = categorias;
        this.lancamentos = lancamentos;
        this.recorrencias = recorrencias;
        this.usuario = usuario;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Grupo> listarGrupos() {
        return grupos.findByFamiliaId(usuario.familiaId()).stream()
                .map(PlanoContasPersistenceAdapter::paraDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Categoria> listarCategorias() {
        return categorias.findByFamiliaId(usuario.familiaId()).stream()
                .map(PlanoContasPersistenceAdapter::paraDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Grupo> buscarGrupo(Long id) {
        return grupos.findByIdAndFamiliaId(id, usuario.familiaId()).map(PlanoContasPersistenceAdapter::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Categoria> buscarCategoria(Long id) {
        return categorias.findByIdAndFamiliaId(id, usuario.familiaId())
                .map(PlanoContasPersistenceAdapter::paraDominio);
    }

    @Override
    public Grupo salvarGrupo(Grupo g) {
        GrupoCategoriaEntity e = g.id() != null ? grupoDaFamilia(g.id()) : new GrupoCategoriaEntity();
        if (g.id() == null) {
            e.familiaId = usuario.familiaId();
        }
        preencher(e, g);
        return paraDominio(grupos.save(e));
    }

    @Override
    public Categoria salvarCategoria(Categoria c) {
        grupoDaFamilia(c.grupoId());
        CategoriaEntity e;
        if (c.id() != null) {
            e = categorias.findByIdAndFamiliaId(c.id(), usuario.familiaId())
                    .orElseThrow(() -> new NaoEncontradoException("Categoria não encontrada: " + c.id()));
        } else {
            e = new CategoriaEntity();
            e.familiaId = usuario.familiaId();
        }
        preencher(e, c);
        return paraDominio(categorias.save(e));
    }

    @Override
    public void removerGrupo(Long id) {
        grupos.findByIdAndFamiliaId(id, usuario.familiaId()).ifPresent(grupos::delete);
    }

    @Override
    public void removerCategoria(Long id) {
        categorias.findByIdAndFamiliaId(id, usuario.familiaId()).ifPresent(categorias::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean grupoTemCategorias(Long grupoId) {
        return categorias.existsByGrupoId(grupoId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean categoriaEmUso(Long categoriaId) {
        return lancamentos.existsByCategoriaId(categoriaId) || recorrencias.existsByCategoriaId(categoriaId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeGrupo(TipoMovimento tipo, String nome, Long ignorarId) {
        return grupos.existeNome(usuario.familiaId(), tipo.name(), nome.trim(), ignorarId != null ? ignorarId : NENHUM);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeCategoria(Long grupoId, String nome, Long ignorarId) {
        return categorias.existeNome(grupoId, nome.trim(), ignorarId != null ? ignorarId : NENHUM);
    }

    @Override
    public void criarModelo(Long familiaId, List<ModeloGrupo> modelo) {
        List<GrupoCategoriaEntity> existentes = grupos.findByFamiliaId(familiaId);
        for (ModeloGrupo mg : modelo) {
            int ordem = existentes.stream().filter(g -> g.tipo.equals(mg.tipo().name()))
                    .mapToInt(g -> g.ordem).max().orElse(0) + 1;
            GrupoCategoriaEntity g = new GrupoCategoriaEntity();
            g.familiaId = familiaId;
            preencher(g, new Grupo(null, mg.tipo(), mg.natureza(), mg.nome(), mg.empresa(), ordem, true));
            g = grupos.save(g);
            existentes.add(g);
            int ordemCategoria = 0;
            for (ModeloCategoria mc : mg.categorias()) {
                CategoriaEntity c = new CategoriaEntity();
                c.familiaId = familiaId;
                preencher(c, new Categoria(null, g.id, mc.nome(), ++ordemCategoria, true, mc.tributavel(),
                        mc.aliquota(), mc.recorrente()));
                categorias.save(c);
            }
        }
    }

    private GrupoCategoriaEntity grupoDaFamilia(Long id) {
        return grupos.findByIdAndFamiliaId(id, usuario.familiaId())
                .orElseThrow(() -> new NaoEncontradoException("Grupo", id));
    }

    private static void preencher(GrupoCategoriaEntity e, Grupo g) {
        e.tipo = g.tipo().name();
        e.natureza = g.natureza().name();
        e.nome = g.nome();
        e.empresa = g.empresa();
        e.ordem = g.ordem();
        e.ativo = g.ativo();
    }

    private static void preencher(CategoriaEntity e, Categoria c) {
        e.grupoId = c.grupoId();
        e.nome = c.nome();
        e.ordem = c.ordem();
        e.ativa = c.ativa();
        e.tributavel = c.tributavel();
        e.aliquota = c.aliquota() != null ? c.aliquota().valor() : null;
        e.recorrente = c.recorrente();
        e.estimativa = c.estimativa() != null ? c.estimativa().name() : null;
    }

    private static Grupo paraDominio(GrupoCategoriaEntity e) {
        return new Grupo(e.id, TipoMovimento.valueOf(e.tipo), Natureza.valueOf(e.natureza), e.nome, e.empresa,
                e.ordem, e.ativo);
    }

    private static Categoria paraDominio(CategoriaEntity e) {
        return new Categoria(e.id, e.grupoId, e.nome, e.ordem, e.ativa, e.tributavel,
                e.aliquota != null ? Percentual.of(e.aliquota) : null, e.recorrente,
                e.estimativa != null ? Estimativa.valueOf(e.estimativa) : null);
    }
}
