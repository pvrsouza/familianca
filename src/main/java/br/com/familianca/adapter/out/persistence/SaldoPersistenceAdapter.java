package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.SaldoRepository;
import br.com.familianca.application.port.out.UsuarioAtual;
import br.com.familianca.domain.model.SaldoMes;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Component
@Transactional
public class SaldoPersistenceAdapter implements SaldoRepository {

    private final SaldoMesJpaRepository jpa;
    private final UsuarioAtual usuario;

    public SaldoPersistenceAdapter(SaldoMesJpaRepository jpa, UsuarioAtual usuario) {
        this.jpa = jpa;
        this.usuario = usuario;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaldoMes> listar(int ano) {
        return jpa.findByFamiliaIdAndCompetenciaBetween(usuario.familiaId(), ano + "-01", ano + "-12").stream()
                .map(e -> new SaldoMes(Competencia.parse(e.competencia), Dinheiro.of(e.valor), e.observacao,
                        e.dataReferencia, e.porLocal.entrySet().stream()
                        .map(p -> new SaldoMes.PorLocal(p.getKey(), Dinheiro.of(p.getValue())))
                        .sorted(Comparator.comparing(SaldoMes.PorLocal::localId)).toList()))
                .toList();
    }

    @Override
    public SaldoMes salvar(SaldoMes saldo) {
        SaldoMesEntity e = new SaldoMesEntity();
        e.familiaId = usuario.familiaId();
        e.competencia = saldo.competencia().toString();
        e.valor = saldo.valor().valor();
        e.observacao = saldo.observacao();
        e.dataReferencia = saldo.dataReferencia();
        saldo.porLocal().forEach(p -> e.porLocal.put(p.localId(), p.valor().valor()));
        jpa.save(e);
        return saldo;
    }

    @Override
    public void remover(Competencia competencia) {
        jpa.deleteById(new SaldoMesEntity.Chave(usuario.familiaId(), competencia.toString()));
    }
}
