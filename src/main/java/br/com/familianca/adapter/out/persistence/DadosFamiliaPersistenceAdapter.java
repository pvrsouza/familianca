package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.DadosFamiliaRepository;
import br.com.familianca.application.port.out.UsuarioAtual;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

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

    @Override
    @Transactional(readOnly = true)
    public Optional<Integer> primeiroAno() {
        Long familiaId = usuario.familiaId();
        LocalDate lancamento = jpa.primeiraDataLancamento(familiaId);
        return Stream.of(lancamento != null ? String.valueOf(lancamento.getYear()) : null,
                        jpa.primeiroInicioRecorrencia(familiaId), jpa.primeiraCompetenciaSaldo(familiaId),
                        jpa.primeiraCompetenciaInvestimento(familiaId))
                .filter(Objects::nonNull)
                .map(t -> Integer.parseInt(t.substring(0, 4)))
                .min(Integer::compare);
    }
}
