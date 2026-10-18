package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.InvestimentoRepository;
import br.com.familianca.application.port.out.UsuarioAtual;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.model.Investimento;
import br.com.familianca.domain.model.MovimentoInvestimento;
import br.com.familianca.domain.model.TipoInvestimento;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.Percentual;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Investimentos da família do usuário logado; movimentos seguem o investimento. */
@Component
@Transactional
public class InvestimentoPersistenceAdapter implements InvestimentoRepository {

    private static final long NENHUM = -1L;

    private final InvestimentoJpaRepository investimentos;
    private final MovimentoInvestimentoJpaRepository movimentos;
    private final UsuarioAtual usuario;

    public InvestimentoPersistenceAdapter(InvestimentoJpaRepository investimentos,
                                          MovimentoInvestimentoJpaRepository movimentos, UsuarioAtual usuario) {
        this.investimentos = investimentos;
        this.movimentos = movimentos;
        this.usuario = usuario;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Investimento> listar() {
        return investimentos.findByFamiliaId(usuario.familiaId()).stream()
                .map(InvestimentoPersistenceAdapter::paraDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Investimento> buscar(Long id) {
        return investimentos.findByIdAndFamiliaId(id, usuario.familiaId())
                .map(InvestimentoPersistenceAdapter::paraDominio);
    }

    @Override
    public Investimento salvar(Investimento i) {
        InvestimentoEntity e;
        if (i.id() != null) {
            e = daFamilia(i.id());
        } else {
            e = new InvestimentoEntity();
            e.familiaId = usuario.familiaId();
        }
        e.nome = i.nome();
        e.tipo = i.tipo().name();
        e.ativo = i.ativo();
        return paraDominio(investimentos.save(e));
    }

    @Override
    public void remover(Long id) {
        investimentos.findByIdAndFamiliaId(id, usuario.familiaId()).ifPresent(investimentos::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeNome(String nome, Long ignorarId) {
        return investimentos.existeNome(usuario.familiaId(), nome.trim(), ignorarId != null ? ignorarId : NENHUM);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovimentoInvestimento> listarMovimentosAte(int ano) {
        return movimentos.listarAte(usuario.familiaId(), ano + "-12").stream()
                .map(InvestimentoPersistenceAdapter::paraDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovimentoInvestimento> listarMovimentos(Long investimentoId) {
        daFamilia(investimentoId);
        return movimentos.findByInvestimentoId(investimentoId).stream()
                .map(InvestimentoPersistenceAdapter::paraDominio).toList();
    }

    @Override
    public MovimentoInvestimento salvarMovimento(MovimentoInvestimento m) {
        daFamilia(m.investimentoId());
        MovimentoInvestimentoEntity e = new MovimentoInvestimentoEntity();
        e.investimentoId = m.investimentoId();
        e.competencia = m.competencia().toString();
        e.aporte = m.aporte().valor();
        e.rentabilidade = m.rentabilidade() != null ? m.rentabilidade().valor() : null;
        e.saldoInformado = m.saldoInformado() != null ? m.saldoInformado().valor() : null;
        return paraDominio(movimentos.save(e));
    }

    @Override
    public void removerMovimento(Long investimentoId, Competencia competencia) {
        daFamilia(investimentoId);
        movimentos.deleteById(new MovimentoInvestimentoEntity.Chave(investimentoId, competencia.toString()));
    }

    private InvestimentoEntity daFamilia(Long id) {
        return investimentos.findByIdAndFamiliaId(id, usuario.familiaId())
                .orElseThrow(() -> new NaoEncontradoException("Investimento", id));
    }

    private static Investimento paraDominio(InvestimentoEntity e) {
        return new Investimento(e.id, e.nome, TipoInvestimento.valueOf(e.tipo), e.ativo);
    }

    private static MovimentoInvestimento paraDominio(MovimentoInvestimentoEntity e) {
        return new MovimentoInvestimento(e.investimentoId, Competencia.parse(e.competencia), Dinheiro.of(e.aporte),
                e.rentabilidade != null ? Percentual.of(e.rentabilidade) : null,
                e.saldoInformado != null ? Dinheiro.of(e.saldoInformado) : null);
    }
}
