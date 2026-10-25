package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.RegistroAcessoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
@Transactional
public class RegistroAcessoPersistenceAdapter implements RegistroAcessoRepository {

    private final RegistroAcessoJpaRepository jpa;

    public RegistroAcessoPersistenceAdapter(RegistroAcessoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void registrar(Long usuarioId, String ip, Integer porta, OffsetDateTime momento) {
        RegistroAcessoEntity e = new RegistroAcessoEntity();
        e.usuarioId = usuarioId;
        e.ip = ip;
        e.porta = porta;
        e.momento = momento;
        jpa.save(e);
    }

    @Override
    public int apagarAnterioresA(OffsetDateTime limite) {
        return jpa.apagarAnterioresA(limite);
    }
}
