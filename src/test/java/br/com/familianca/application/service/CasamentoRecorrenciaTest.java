package br.com.familianca.application.service;

import br.com.familianca.IntegracaoBase;
import br.com.familianca.application.service.CasamentoRecorrencia.LinhaCasavel;
import br.com.familianca.application.service.CasamentoRecorrencia.OcorrenciaCasada;
import br.com.familianca.domain.exception.AcessoNegadoException;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Hoje é 17/10/2026 (ver {@link IntegracaoBase}). */
class CasamentoRecorrenciaTest extends IntegracaoBase {

    private static final Competencia OUTUBRO = Competencia.of(2026, 10);

    @Autowired
    private CasamentoRecorrencia casamento;

    /** O MockMvc limpa o contexto de segurança ao fim de cada requisição: restaura o DONO se ninguém estiver logado. */
    private void garantirUsuario() {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            var principal = principal(dono);
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        }
    }

    private Map<Integer, OcorrenciaCasada> casar(List<LinhaCasavel> linhas) {
        garantirUsuario();
        return casamento.casar(linhas);
    }

    private Lancamento confirmar(Long id, Competencia competencia, LocalDate data, Dinheiro valor) {
        garantirUsuario();
        return casamento.confirmar(id, competencia, data, valor);
    }

    private Long aluguel() throws Exception {
        return recorrencia("Habitação", "Aluguel ou financiamento", "Aluguel", "2000", 5);
    }

    private Long recorrencia(String grupo, String categoria, String descricao, String valor, int dia)
            throws Exception {
        return postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "%s", "valor": %s, "dia": %d, "frequencia": "MENSAL",
                 "inicio": "2026-10"}""".formatted(categoria(grupo, categoria), descricao, valor, dia));
    }

    private static LinhaCasavel linha(int indice, String data, String valor, TipoMovimento tipo) {
        return new LinhaCasavel(indice, LocalDate.parse(data), Dinheiro.of(valor), tipo, "EXTRATO");
    }

    @Test
    void casaOAluguelDentroDaToleranciaEDaJanela() throws Exception {
        Long id = aluguel();
        Map<Integer, OcorrenciaCasada> r = casar(List.of(
                linha(0, "2026-10-06", "2040.00", TipoMovimento.DESPESA)));
        assertThat(r).containsOnlyKeys(0);
        assertThat(r.get(0).recorrenciaId()).isEqualTo(id);
        assertThat(r.get(0).competencia()).isEqualTo(OUTUBRO);
        assertThat(r.get(0).vencimento()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(r.get(0).valor()).isEqualTo(Dinheiro.of("2000"));
    }

    @Test
    void naoCasaValorMuitoDiferenteDataForaDaJanelaNemDirecaoErrada() throws Exception {
        aluguel();
        assertThat(casar(List.of(
                linha(0, "2026-10-05", "3000.00", TipoMovimento.DESPESA),
                linha(1, "2026-09-20", "2000.00", TipoMovimento.DESPESA),
                linha(2, "2026-10-05", "2000.00", TipoMovimento.RECEITA)))).isEmpty();
    }

    @Test
    void naoCasaOcorrenciaJaConfirmadaOuPulada() throws Exception {
        Long confirmada = aluguel();
        Long pulada = recorrencia("Habitação", "Condomínio", "Condomínio", "800", 5);
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-10/confirmacao", confirmada)).andExpect(status().isCreated());
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-10/pulo", pulada)).andExpect(status().isOk());
        assertThat(casar(List.of(
                linha(0, "2026-10-05", "2000.00", TipoMovimento.DESPESA),
                linha(1, "2026-10-05", "800.00", TipoMovimento.DESPESA)))).isEmpty();
    }

    @Test
    void umaOcorrenciaParaDuasLinhasIguaisCasaSoUma() throws Exception {
        aluguel();
        Map<Integer, OcorrenciaCasada> r = casar(List.of(
                linha(0, "2026-10-05", "2000.00", TipoMovimento.DESPESA),
                linha(1, "2026-10-05", "2000.00", TipoMovimento.DESPESA)));
        assertThat(r).containsOnlyKeys(0);
    }

    @Test
    void linhaComDuasOcorrenciasIgualmenteProximasNaoCasa() throws Exception {
        recorrencia("Habitação", "Aluguel ou financiamento", "Aluguel A", "1000", 5);
        recorrencia("Habitação", "Condomínio", "Aluguel B", "1000", 5);
        assertThat(casar(List.of(linha(0, "2026-10-05", "1000.00", TipoMovimento.DESPESA)))).isEmpty();
    }

    @Test
    void escolheAMaisProximaEUmaOcorrenciaNaoServeADuas() throws Exception {
        Long a = recorrencia("Habitação", "Aluguel ou financiamento", "Aluguel A", "1000", 5);
        Long b = recorrencia("Habitação", "Condomínio", "Condomínio B", "1050", 5);
        Map<Integer, OcorrenciaCasada> r = casar(List.of(
                linha(0, "2026-10-05", "1040.00", TipoMovimento.DESPESA),
                linha(1, "2026-10-05", "1000.00", TipoMovimento.DESPESA)));
        assertThat(r.get(0).recorrenciaId()).isEqualTo(b);
        assertThat(r.get(1).recorrenciaId()).isEqualTo(a);
    }

    @Test
    void casaRecorrenciaDeReceitaNoMesDaCompetencia() throws Exception {
        Long salario = recorrencia("Receitas fixas", "Salário", "Salário", "5000", 5);
        Map<Integer, OcorrenciaCasada> r = casar(List.of(
                linha(0, "2026-10-07", "5000.00", TipoMovimento.RECEITA),
                linha(1, "2026-10-07", "5000.00", TipoMovimento.DESPESA)));
        assertThat(r).containsOnlyKeys(0);
        assertThat(r.get(0).recorrenciaId()).isEqualTo(salario);
        assertThat(r.get(0).vencimento()).isNull();
    }

    @Test
    void confirmarUsaValorEDataDoExtratoEFechaAOcorrencia() throws Exception {
        Long id = aluguel();
        Lancamento l = confirmar(id, OUTUBRO, LocalDate.of(2026, 10, 6), Dinheiro.of("2040.00"));
        assertThat(l.id()).isNotNull();
        assertThat(l.valor()).isEqualTo(Dinheiro.of("2040.00"));
        assertThat(l.data()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(l.recorrenciaId()).isEqualTo(id);
        assertThat(casar(List.of(linha(0, "2026-10-06", "2040.00", TipoMovimento.DESPESA)))).isEmpty();
    }

    @Test
    void membroNaoVeNemConfirmaOcorrenciaDeOutroMembro() throws Exception {
        Long id = aluguel(); // do dono
        Membro membro = adicionarMembro(dono, "membro@teste.com", PapelFamilia.MEMBRO);
        var principal = principal(membro);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));

        assertThat(casar(List.of(linha(0, "2026-10-05", "2000.00", TipoMovimento.DESPESA)))).isEmpty();
        assertThatThrownBy(() -> confirmar(id, OUTUBRO, LocalDate.of(2026, 10, 5), Dinheiro.of("2000")))
                .isInstanceOf(AcessoNegadoException.class);
    }
}
