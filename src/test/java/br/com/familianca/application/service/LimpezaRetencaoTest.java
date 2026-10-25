package br.com.familianca.application.service;

import br.com.familianca.IntegracaoBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LimpezaRetencaoTest extends IntegracaoBase {

    @Autowired
    private LimpezaRetencao limpeza;

    @Test
    void apagaRegistroDeAcessoComMaisDe6MesesEConviteExpiradoHaMaisDe30Dias() {
        OffsetDateTime agora = relogio.agora();
        acesso(agora.minusMonths(6).minusDays(1));
        acesso(agora.minusMonths(6).plusDays(1));
        convite("velho@teste.com", "ACEITO", agora.minusDays(31));
        convite("cancelado@teste.com", "CANCELADO", agora.minusDays(29));
        convite("pendente@teste.com", "PENDENTE", agora.plusDays(7));

        limpeza.executar();

        assertThat(jdbc.queryForObject("select count(*) from registro_acesso", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("select email from convite order by email", String.class))
                .containsExactly("cancelado@teste.com", "pendente@teste.com");
    }

    private void acesso(OffsetDateTime momento) {
        jdbc.update("insert into registro_acesso (usuario_id, ip, momento) values (?, '10.0.0.1', ?)",
                dono.usuarioId(), momento);
    }

    private void convite(String email, String status, OffsetDateTime expiraEm) {
        jdbc.update("""
                insert into convite (familia_id, email, papel, token_hash, status, expira_em, convidado_por)
                values (?, ?, 'MEMBRO', md5(?), ?, ?, ?)""", dono.familiaId(), email, email, status, expiraEm,
                dono.id());
    }
}
