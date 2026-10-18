package br.com.familianca.application.service;

import br.com.familianca.application.port.in.GerenciarLocaisSaldo;
import br.com.familianca.application.port.out.LocalSaldoRepository;
import br.com.familianca.domain.exception.ConflitoException;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.model.LocalSaldo;
import br.com.familianca.domain.model.TipoLocalSaldo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional
@PreAuthorize("hasRole('DONO')")
public class LocalSaldoService implements GerenciarLocaisSaldo {

    private final LocalSaldoRepository locais;
    private final AcessoFamilia acesso;

    public LocalSaldoService(LocalSaldoRepository locais, AcessoFamilia acesso) {
        this.locais = locais;
        this.acesso = acesso;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<LocalSaldo> listar() {
        return locais.listar().stream()
                .sorted(Comparator.comparing(LocalSaldo::nome, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Override
    public LocalSaldo criar(String nome, TipoLocalSaldo tipo, Long membroId) {
        LocalSaldo novo = new LocalSaldo(null, nome, tipo, membroDaFamilia(membroId), true);
        exigirNomeLivre(novo.nome(), null);
        return locais.salvar(novo);
    }

    @Override
    public LocalSaldo atualizar(Long id, String nome, TipoLocalSaldo tipo, Long membroId, boolean ativo) {
        exigir(id);
        LocalSaldo editado = new LocalSaldo(id, nome, tipo, membroDaFamilia(membroId), ativo);
        exigirNomeLivre(editado.nome(), id);
        return locais.salvar(editado);
    }

    @Override
    public void remover(Long id) {
        exigir(id);
        if (locais.emUso(id)) {
            throw new ConflitoException("Este lugar já entrou no saldo de algum mês: desative em vez de excluir");
        }
        locais.remover(id);
    }

    /** Dono do lugar: alguém da família (mesmo quem já saiu, para manter o histórico), ou ninguém. */
    private Long membroDaFamilia(Long membroId) {
        if (membroId != null && !acesso.membrosPorId().containsKey(membroId)) {
            throw new NaoEncontradoException("Membro", membroId);
        }
        return membroId;
    }

    private void exigir(Long id) {
        locais.buscar(id).orElseThrow(() -> new NaoEncontradoException("Lugar do saldo", id));
    }

    private void exigirNomeLivre(String nome, Long ignorarId) {
        if (locais.existeNome(nome, ignorarId)) {
            throw new ConflitoException("Já existe " + nome);
        }
    }
}
