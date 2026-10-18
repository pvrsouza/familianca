package br.com.familianca.adapter.in.seguranca;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteRequisicoesApiTest {

    private final Filter filtro = new LimiteRequisicoesApi(new SegurancaProperties.LimiteApi(3, Duration.ofMinutes(1))).filtro();

    @Test
    void acimaDoLimiteResponde429PorIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(chamar("/api/lancamentos", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse bloqueada = chamar("/api/lancamentos", "10.0.0.1");
        assertThat(bloqueada.getStatus()).isEqualTo(429);
        assertThat(bloqueada.getHeader("Retry-After")).isEqualTo("60");
        assertThat(bloqueada.getContentAsString()).contains("Muitas requisições");

        assertThat(chamar("/api/lancamentos", "10.0.0.2").getStatus()).as("outro IP").isEqualTo(200);
        assertThat(chamar("/actuator/health", "10.0.0.1").getStatus()).as("fora da /api").isEqualTo(200);
    }

    private MockHttpServletResponse chamar(String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
