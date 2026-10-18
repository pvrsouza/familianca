package br.com.familianca.adapter.in.seguranca;

import br.com.familianca.application.port.in.PrepararAcesso;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Cria o ADMIN da plataforma (ADMIN_EMAIL/ADMIN_PASSWORD), se informado e se não há nenhum usuário. */
@Component
public class PrimeiroAcessoNoStartup implements ApplicationRunner {

    private final PrepararAcesso preparar;
    private final SegurancaProperties.AdminInicial admin;

    public PrimeiroAcessoNoStartup(PrepararAcesso preparar, SegurancaProperties propriedades) {
        this.preparar = preparar;
        this.admin = propriedades.adminInicial();
    }

    @Override
    public void run(ApplicationArguments args) {
        preparar.prepararPrimeiroAcesso(admin.nome(), admin.email(), admin.senha());
    }
}
