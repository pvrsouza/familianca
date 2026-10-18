package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.DadosFamiliaRepository;
import br.com.familianca.application.port.out.UsuarioAtual;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Só a família do usuário logado: o id vem da sessão, nunca da requisição. */
@Component
@Transactional
public class DadosFamiliaPersistenceAdapter implements DadosFamiliaRepository {

    private final DadosFamiliaJpaRepository jpa;
    private final UsuarioAtual usuario;

    public DadosFamiliaPersistenceAdapter(DadosFamiliaJpaRepository jpa, UsuarioAtual usuario) {
        this.jpa = jpa;
        this.usuario = usuario;
    }

    @Override
    public Apagados apagarTudo() {
        Long familiaId = usuario.familiaId();
        int lancamentos = jpa.apagarLancamentos(familiaId);
        int recorrencias = jpa.apagarRecorrencias(familiaId);
        int saldos = jpa.apagarSaldos(familiaId);
        int locaisSaldo = jpa.apagarLocaisSaldo(familiaId);
        jpa.apagarMovimentosInvestimento(familiaId);
        int investimentos = jpa.apagarInvestimentos(familiaId);
        int categorias = jpa.apagarCategorias(familiaId);
        int grupos = jpa.apagarGrupos(familiaId);
        return new Apagados(lancamentos, recorrencias, saldos, locaisSaldo, investimentos, grupos, categorias);
    }
}
