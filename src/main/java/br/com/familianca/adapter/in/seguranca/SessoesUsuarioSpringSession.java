package br.com.familianca.adapter.in.seguranca;

import br.com.familianca.application.port.out.SessoesUsuario;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

/** Apaga do banco (spring_session) as sessões do usuário, pelo índice de principal do Spring Session JDBC. */
@Component
public class SessoesUsuarioSpringSession implements SessoesUsuario {

    private final FindByIndexNameSessionRepository<? extends Session> sessoes;

    public SessoesUsuarioSpringSession(FindByIndexNameSessionRepository<? extends Session> sessoes) {
        this.sessoes = sessoes;
    }

    @Override
    public void encerrarTodas(String email) {
        sessoes.findByPrincipalName(email).keySet().forEach(sessoes::deleteById);
    }

    @Override
    public void encerrarOutras(String email) {
        String atual = RequestContextHolder.getRequestAttributes() != null
                ? RequestContextHolder.getRequestAttributes().getSessionId() : null;
        sessoes.findByPrincipalName(email).keySet().stream()
                .filter(id -> !id.equals(atual))
                .forEach(sessoes::deleteById);
    }
}
