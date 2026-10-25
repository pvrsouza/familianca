package br.com.familianca.application.service;

import br.com.familianca.application.port.in.RegistrarAcesso;
import br.com.familianca.application.port.out.RegistroAcessoRepository;
import br.com.familianca.application.port.out.Relogio;
import org.springframework.stereotype.Component;

@Component
public class RegistroAcessoService implements RegistrarAcesso {

    private final RegistroAcessoRepository registros;
    private final Relogio relogio;

    public RegistroAcessoService(RegistroAcessoRepository registros, Relogio relogio) {
        this.registros = registros;
        this.relogio = relogio;
    }

    @Override
    public void registrar(Long usuarioId, String ip, Integer porta) {
        registros.registrar(usuarioId, ip, porta, relogio.agora());
    }
}
