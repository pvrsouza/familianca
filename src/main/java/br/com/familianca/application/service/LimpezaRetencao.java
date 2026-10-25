package br.com.familianca.application.service;

import br.com.familianca.application.port.out.ConviteRepository;
import br.com.familianca.application.port.out.RegistroAcessoRepository;
import br.com.familianca.application.port.out.Relogio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Apaga, uma vez por dia, o que passou do prazo de guarda (LGPD Art. 15 e 16; prazos em {@code .lgpd/}):
 * registro de acesso com mais de 6 meses (o mínimo do Marco Civil, Art. 15) e convite expirado há mais de 30 dias
 * (o e-mail é de alguém que pode nunca ter entrado). Com mais de uma instância, cada uma apaga o mesmo, sem problema.
 */
@Component
public class LimpezaRetencao {

    private static final Logger log = LoggerFactory.getLogger(LimpezaRetencao.class);

    private final RegistroAcessoRepository registrosAcesso;
    private final ConviteRepository convites;
    private final Relogio relogio;

    public LimpezaRetencao(RegistroAcessoRepository registrosAcesso, ConviteRepository convites, Relogio relogio) {
        this.registrosAcesso = registrosAcesso;
        this.convites = convites;
        this.relogio = relogio;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
    public void executar() {
        OffsetDateTime agora = relogio.agora();
        int acessos = registrosAcesso.apagarAnterioresA(agora.minusMonths(6));
        int convitesApagados = convites.apagarExpiradosAntesDe(agora.minusDays(30));
        log.info("auditoria evento=RETENCAO_APLICADA registrosAcesso={} convites={}", acessos, convitesApagados);
    }
}
