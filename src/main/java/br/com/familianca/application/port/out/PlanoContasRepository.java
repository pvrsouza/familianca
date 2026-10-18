package br.com.familianca.application.port.out;

import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.service.CatalogoCategorias.ModeloGrupo;

import java.util.List;
import java.util.Optional;

/** Grupos e categorias da família do usuário logado (exceto {@link #criarModelo}, usado no cadastro). */
public interface PlanoContasRepository {

    List<Grupo> listarGrupos();

    List<Categoria> listarCategorias();

    Optional<Grupo> buscarGrupo(Long id);

    Optional<Categoria> buscarCategoria(Long id);

    Grupo salvarGrupo(Grupo grupo);

    Categoria salvarCategoria(Categoria categoria);

    void removerGrupo(Long id);

    void removerCategoria(Long id);

    boolean grupoTemCategorias(Long grupoId);

    /** Tem lançamento ou recorrência. */
    boolean categoriaEmUso(Long categoriaId);

    /** Nome igual (sem diferenciar maiúsculas) no mesmo tipo, fora o grupo ignorado (nulo = nenhum). */
    boolean existeGrupo(TipoMovimento tipo, String nome, Long ignorarId);

    boolean existeCategoria(Long grupoId, String nome, Long ignorarId);

    /** Cria os grupos e categorias do modelo na família informada, depois dos que já existem. */
    void criarModelo(Long familiaId, List<ModeloGrupo> modelo);
}
