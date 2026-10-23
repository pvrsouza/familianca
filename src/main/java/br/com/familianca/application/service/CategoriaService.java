package br.com.familianca.application.service;

import br.com.familianca.application.port.in.GerenciarCategorias;
import br.com.familianca.application.port.out.PlanoContasRepository;
import br.com.familianca.application.port.out.UsuarioAtual;
import br.com.familianca.domain.exception.ConflitoException;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Estimativa;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Recurso;
import br.com.familianca.domain.service.CatalogoCategorias;
import br.com.familianca.domain.service.CatalogoCategorias.ModeloGrupo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static br.com.familianca.domain.Validacoes.obrigatorio;

@Service
@Transactional
@PreAuthorize("hasRole('DONO')")
public class CategoriaService implements GerenciarCategorias {

    private final PlanoContasRepository planoContas;
    private final AcessoFamilia acesso;
    private final UsuarioAtual usuarioAtual;

    public CategoriaService(PlanoContasRepository planoContas, AcessoFamilia acesso, UsuarioAtual usuarioAtual) {
        this.planoContas = planoContas;
        this.acesso = acesso;
        this.usuarioAtual = usuarioAtual;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public PlanoContas listar() {
        return new PlanoContas(planoContas.listarGrupos(), planoContas.listarCategorias());
    }

    @Override
    public Grupo criarGrupo(DadosGrupo dados) {
        if (dados.empresa()) {
            acesso.exigir(Recurso.DESPESAS_PJ);
        }
        int ordem = dados.ordem() != null ? dados.ordem() : proximaOrdem(planoContas.listarGrupos().stream()
                .filter(g -> g.tipo() == dados.tipo()).map(Grupo::ordem).toList());
        Grupo grupo = new Grupo(null, dados.tipo(), dados.natureza(), dados.nome(), dados.empresa(), ordem, true);
        if (planoContas.existeGrupo(grupo.tipo(), grupo.nome(), null)) {
            throw new ConflitoException("Já existe o grupo " + grupo.nome());
        }
        return planoContas.salvarGrupo(grupo);
    }

    @Override
    public Grupo atualizarGrupo(Long id, DadosGrupo dados, boolean ativo) {
        Grupo atual = exigirGrupo(id);
        if (dados.tipo() != null && dados.tipo() != atual.tipo()) {
            throw new RegraNegocioException("O tipo do grupo não muda: crie outro grupo");
        }
        if (dados.empresa() && !atual.empresa()) {
            acesso.exigir(Recurso.DESPESAS_PJ);
        }
        Grupo grupo = new Grupo(id, atual.tipo(), dados.natureza(), dados.nome(), dados.empresa(),
                dados.ordem() != null ? dados.ordem() : atual.ordem(), ativo);
        if (planoContas.existeGrupo(grupo.tipo(), grupo.nome(), id)) {
            throw new ConflitoException("Já existe o grupo " + grupo.nome());
        }
        return planoContas.salvarGrupo(grupo);
    }

    @Override
    public void removerGrupo(Long id) {
        exigirGrupo(id);
        if (planoContas.grupoTemCategorias(id)) {
            throw new ConflitoException("O grupo tem categorias: exclua ou mova as categorias, ou desative o grupo");
        }
        planoContas.removerGrupo(id);
    }

    @Override
    public Categoria criarCategoria(DadosCategoria dados) {
        Grupo grupo = exigirGrupo(obrigatorio(dados.grupoId(), "grupo"));
        int ordem = dados.ordem() != null ? dados.ordem() : proximaOrdem(planoContas.listarCategorias().stream()
                .filter(c -> c.grupoId().equals(grupo.id())).map(Categoria::ordem).toList());
        Categoria categoria = new Categoria(null, grupo.id(), dados.nome(), ordem, true, dados.tributavel(),
                dados.aliquota(), Boolean.TRUE.equals(dados.recorrente()), dados.estimativa());
        categoria.validarNo(grupo);
        if (categoria.tributavel() || categoria.calculada()) {
            acesso.exigir(Recurso.DESPESAS_PJ);
        }
        if (planoContas.existeCategoria(grupo.id(), categoria.nome(), null)) {
            throw new ConflitoException("Já existe a categoria " + categoria.nome() + " em " + grupo.nome());
        }
        return planoContas.salvarCategoria(categoria);
    }

    @Override
    public Categoria definirEstimativa(Long id, Estimativa estimativa) {
        Categoria c = exigirCategoria(id);
        Categoria nova = new Categoria(c.id(), c.grupoId(), c.nome(), c.ordem(), c.ativa(), c.tributavel(), c.aliquota(),
                c.recorrente(), estimativa);
        nova.validarNo(exigirGrupo(c.grupoId()));
        return planoContas.salvarCategoria(nova);
    }

    @Override
    public Categoria atualizarCategoria(Long id, DadosCategoria dados, boolean ativa) {
        Categoria atual = exigirCategoria(id);
        Grupo grupoAtual = exigirGrupo(atual.grupoId());
        Grupo grupo = dados.grupoId() != null ? exigirGrupo(dados.grupoId()) : grupoAtual;
        if (grupo.tipo() != grupoAtual.tipo()) {
            throw new RegraNegocioException("A categoria só pode mudar para um grupo do mesmo tipo");
        }
        Categoria categoria = new Categoria(id, grupo.id(), dados.nome(),
                dados.ordem() != null ? dados.ordem() : atual.ordem(), ativa, dados.tributavel(), dados.aliquota(),
                dados.recorrente() != null ? dados.recorrente() : atual.recorrente(),
                dados.estimativaInformada() ? dados.estimativa() : atual.estimativa());
        categoria.validarNo(grupo);
        boolean passaAUsarPj = (categoria.tributavel() && !atual.tributavel())
                || (categoria.calculada() && !Objects.equals(categoria.aliquota(), atual.aliquota()));
        if (passaAUsarPj) {
            acesso.exigir(Recurso.DESPESAS_PJ);
        }
        if (categoria.calculada() && !atual.calculada() && planoContas.categoriaEmUso(id)) {
            throw new RegraNegocioException("Categoria com lançamentos ou recorrências não pode passar a ser calculada");
        }
        if (planoContas.existeCategoria(grupo.id(), categoria.nome(), id)) {
            throw new ConflitoException("Já existe a categoria " + categoria.nome() + " em " + grupo.nome());
        }
        return planoContas.salvarCategoria(categoria);
    }

    @Override
    public void removerCategoria(Long id) {
        exigirCategoria(id);
        if (planoContas.categoriaEmUso(id)) {
            throw new ConflitoException("A categoria tem lançamentos ou recorrências: desative em vez de excluir");
        }
        planoContas.removerCategoria(id);
    }

    @Override
    public PlanoContas aplicarModeloPj() {
        acesso.exigir(Recurso.DESPESAS_PJ);
        List<ModeloGrupo> modelo = CatalogoCategorias.pj();
        for (ModeloGrupo g : modelo) {
            if (planoContas.existeGrupo(g.tipo(), g.nome(), null)) {
                throw new ConflitoException("O modelo PJ já foi aplicado (existe o grupo " + g.nome() + ")");
            }
        }
        planoContas.criarModelo(usuarioAtual.familiaId(), modelo);
        return listar();
    }

    private Grupo exigirGrupo(Long id) {
        return planoContas.buscarGrupo(id).orElseThrow(() -> new NaoEncontradoException("Grupo", id));
    }

    private Categoria exigirCategoria(Long id) {
        return planoContas.buscarCategoria(id)
                .orElseThrow(() -> new NaoEncontradoException("Categoria não encontrada: " + id));
    }

    private static int proximaOrdem(List<Integer> ordens) {
        return ordens.stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
    }
}
