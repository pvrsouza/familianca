package br.com.familianca.adapter.in.web;

import br.com.familianca.IntegracaoBase;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImportacaoApiTest extends IntegracaoBase {

    private Long local;

    @BeforeEach
    void criarLocal() throws Exception {
        local = postarERetornarId("/api/locais-saldo", "{\"nome\": \"Inter corrente\", \"tipo\": \"CONTA_CORRENTE\"}");
    }

    /** Linha: fitid|data (AAAAMMDD)|valor|memo. */
    private static byte[] ofx(String... linhas) {
        StringBuilder sb = new StringBuilder("""
                OFXHEADER:100
                DATA:OFXSGML
                VERSION:102
                CHARSET:1252

                <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKACCTFROM><BANKID>077<ACCTID>12341234
                </BANKACCTFROM><BANKTRANLIST><DTSTART>20261001<DTEND>20261031
                """);
        for (String l : linhas) {
            String[] p = l.split("\\|");
            sb.append("<STMTTRN><TRNTYPE>OTHER<DTPOSTED>").append(p[1]).append("120000[-3:BRT]<TRNAMT>").append(p[2])
                    .append("<FITID>").append(p[0]).append("<MEMO>").append(p[3]).append("</STMTTRN>\n");
        }
        sb.append("</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>");
        return sb.toString().getBytes(Charset.forName("windows-1252"));
    }

    private ResultActions analisar(MockMvc como, Long localId, String nome, byte[] conteudo) throws Exception {
        return como.perform(multipart("/api/importacoes/analisar")
                .file(new MockMultipartFile("arquivo", nome, "application/octet-stream", conteudo))
                .param("localSaldoId", String.valueOf(localId)));
    }

    private static String linha(String chave, String acao, String data, String descricao, String valor, String tipo,
                                Long categoriaId) {
        return """
                {"chave": "%s", "acao": "%s", "data": "%s", "descricao": "%s", "valor": %s, "tipo": "%s",
                 "categoriaId": %s}""".formatted(chave, acao, data, descricao, valor, tipo, categoriaId);
    }

    private ResultActions gravar(MockMvc como, Long localId, String... linhas) throws Exception {
        return como.perform(post("/api/importacoes").contentType(APPLICATION_JSON).content("""
                {"localSaldoId": %d, "periodoDe": "2026-10-01", "periodoAte": "2026-10-31", "linhas": [%s]}"""
                .formatted(localId, String.join(",", linhas))));
    }

    private int contar(String tabela) {
        return jdbc.queryForObject("select count(*) from " + tabela, Integer.class);
    }

    @Test
    void analisaImportaEReenviarOMesmoArquivoNaoDuplica() throws Exception {
        byte[] arquivo = ofx("A1|20261028|-45.90|Compra PADARIA AÇÚCAR", "A2|20261005|9000.00|Pix recebido SALÁRIO");

        analisar(mvc, local, "extrato.OFX", arquivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contaOfx").value("****1234"))
                .andExpect(jsonPath("$.periodoDe").value("2026-10-01"))
                .andExpect(jsonPath("$.resumo.total").value(2))
                .andExpect(jsonPath("$.resumo.novas").value(2))
                .andExpect(jsonPath("$.linhas[0].chave").value("A1"))
                .andExpect(jsonPath("$.linhas[0].tipo").value("DESPESA"))
                .andExpect(jsonPath("$.linhas[0].valor").value(45.90))
                .andExpect(jsonPath("$.linhas[0].descricao").value("Compra PADARIA AÇÚCAR"))
                .andExpect(jsonPath("$.linhas[0].status").value("NOVA"))
                .andExpect(jsonPath("$.linhas[0].acaoSugerida").value("IMPORTAR"))
                .andExpect(jsonPath("$.linhas[0].ocorrenciaPendente").value(nullValue()))
                .andExpect(jsonPath("$.linhas[1].tipo").value("RECEITA"));

        Long mercado = categoria("Habitação", "Supermercado");
        Long salario = categoria("Receitas fixas", "Salário");
        gravar(mvc, local,
                linha("A1", "IMPORTAR", "2026-10-28", "Compra PADARIA AÇÚCAR", "45.90", "DESPESA", mercado),
                linha("A2", "IMPORTAR", "2026-10-05", "Pix recebido SALÁRIO", "9000.00", "RECEITA", salario))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(2))
                .andExpect(jsonPath("$.jaExistiam").value(0));
        assertThat(contar("lancamento")).isEqualTo(2);

        // Mesmo arquivo de novo: tudo JA_IMPORTADA e sugerido para ignorar.
        analisar(mvc, local, "extrato.ofx", arquivo)
                .andExpect(jsonPath("$.resumo.jaImportadas").value(2))
                .andExpect(jsonPath("$.linhas[0].status").value("JA_IMPORTADA"))
                .andExpect(jsonPath("$.linhas[0].acaoSugerida").value("IGNORAR"));

        // Arquivo que se sobrepõe: só a linha nova é nova; gravar a velha de novo conta em jaExistiam.
        analisar(mvc, local, "extrato2.ofx", ofx("A2|20261005|9000.00|Pix recebido SALÁRIO",
                "A3|20261030|-12.00|Café"))
                .andExpect(jsonPath("$.resumo.novas").value(1))
                .andExpect(jsonPath("$.resumo.jaImportadas").value(1));
        gravar(mvc, local,
                linha("A2", "IMPORTAR", "2026-10-05", "Pix recebido SALÁRIO", "9000.00", "RECEITA", salario),
                linha("A3", "IMPORTAR", "2026-10-30", "Café", "12.00", "DESPESA", mercado))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(1))
                .andExpect(jsonPath("$.jaExistiam").value(1));
        assertThat(contar("lancamento")).isEqualTo(3);

        // Outro lugar do saldo: a mesma chave não conflita.
        Long outro = postarERetornarId("/api/locais-saldo", "{\"nome\": \"Outra conta\", \"tipo\": \"POUPANCA\"}");
        analisar(mvc, outro, "extrato.ofx", arquivo).andExpect(jsonPath("$.resumo.novas").value(2));
    }

    @Test
    void sugereCategoriaDaUltimaDescricaoEIgnoraPorPalavraChave() throws Exception {
        lancar("Habitação", "Supermercado", "Padaria Açúcar", "10", "2026-09-01");
        byte[] arquivo = ofx("B1|20261010|-20.00|PADARIA  acucar", "B2|20261011|-500.00|Pagamento de fatura cartão",
                "B3|20261012|-300.00|Aplicação CDB", "B4|20261013|-90.00|Resgate poupança",
                "B5|20261014|-33.00|Pagamento aplicativo de corrida", "B6|20261015|-77.00|Transferência entre contas");

        analisar(mvc, local, "x.ofx", arquivo)
                .andExpect(jsonPath("$.linhas[0].categoriaIdSugerida").value(categoria("Habitação", "Supermercado")))
                .andExpect(jsonPath("$.linhas[0].acaoSugerida").value("IMPORTAR"))
                .andExpect(jsonPath("$.linhas[1].acaoSugerida").value("IGNORAR"))
                .andExpect(jsonPath("$.linhas[2].acaoSugerida").value("IGNORAR"))
                .andExpect(jsonPath("$.linhas[3].acaoSugerida").value("IGNORAR"))
                .andExpect(jsonPath("$.linhas[4].acaoSugerida").value("IMPORTAR"))
                .andExpect(jsonPath("$.linhas[4].categoriaIdSugerida").value(nullValue()))
                .andExpect(jsonPath("$.linhas[5].acaoSugerida").value("IGNORAR"));
    }

    @Test
    void marcaPossivelDuplicataDoQueFoiDigitadoAMao() throws Exception {
        Long digitado = lancar("Habitação", "Supermercado", "Mercado", "45.90", "2026-10-27");
        lancar("Habitação", "Supermercado", "Mercado longe", "45.90", "2026-10-20");
        lancar("Habitação", "Supermercado", "Outro valor", "45.91", "2026-10-28");
        lancar("Receitas fixas", "Salário", "Receita igual", "45.90", "2026-10-28");

        analisar(mvc, local, "x.ofx", ofx("C1|20261028|-45.90|Compra mercado"))
                .andExpect(jsonPath("$.resumo.possiveisDuplicatas").value(1))
                .andExpect(jsonPath("$.linhas[0].status").value("POSSIVEL_DUPLICATA"))
                .andExpect(jsonPath("$.linhas[0].lancamentoParecido.id").value(digitado))
                .andExpect(jsonPath("$.linhas[0].lancamentoParecido.categoriaNome").value("Supermercado"))
                .andExpect(jsonPath("$.linhas[0].acaoSugerida").value("IMPORTAR"));

        // Importado vindo de extrato não conta como "digitado à mão".
        Long mercado = categoria("Habitação", "Supermercado");
        gravar(mvc, local, linha("C1", "IMPORTAR", "2026-10-28", "Compra mercado", "45.90", "DESPESA", mercado))
                .andExpect(status().isCreated());
        analisar(mvc, local, "x.ofx", ofx("C2|20261028|-45.90|Outra compra"))
                .andExpect(jsonPath("$.linhas[0].lancamentoParecido.id").value(digitado));
    }

    @Test
    void semCategoriaUsaAClassificarCriadaNaHoraEIgnoradaGuardaSoAChave() throws Exception {
        gravar(mvc, local,
                linha("D1", "IMPORTAR", "2026-10-10", "Compra sem categoria", "10.00", "DESPESA", null),
                linha("D2", "IMPORTAR", "2026-10-11", "Pix sem categoria", "20.00", "RECEITA", null),
                linha("D3", "IMPORTAR", "2026-10-12", "Outra compra", "5.00", "DESPESA", null),
                linha("D4", "IGNORAR", "2026-10-13", "Aplicação segredo", "999.00", "DESPESA", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(3))
                .andExpect(jsonPath("$.ignoradas").value(1));

        assertThat(jdbc.queryForObject("""
                select count(*) from categoria where nome = 'A classificar' and familia_id = ?""", Integer.class,
                dono.familiaId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                select count(*) from lancamento l join categoria c on c.id = l.categoria_id
                where c.nome = 'A classificar'""", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForList("select * from importacao_ignorada").get(0).values().toString())
                .doesNotContain("segredo", "999");

        analisar(mvc, local, "x.ofx", ofx("D4|20261013|-999.00|Aplicação segredo"))
                .andExpect(jsonPath("$.linhas[0].status").value("JA_IMPORTADA"));
    }

    @Test
    void tudoOuNadaERegrasDeCategoria() throws Exception {
        Long mercado = categoria("Habitação", "Supermercado");
        Long salario = categoria("Receitas fixas", "Salário");
        Long inativa = categoria("Habitação", "Gás");
        mvc.perform(put("/api/categorias/{id}", inativa).contentType(APPLICATION_JSON)
                .content("{\"grupoId\": %d, \"nome\": \"Gás\", \"ativa\": false}".formatted(grupo("Habitação"))))
                .andExpect(status().isOk());

        gravar(mvc, local,
                linha("E1", "IMPORTAR", "2026-10-10", "Ok", "10.00", "DESPESA", mercado),
                linha("E2", "IMPORTAR", "2026-10-11", "Categoria desativada", "10.00", "DESPESA", inativa))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Linha 2:")));
        gravar(mvc, local, linha("E3", "IMPORTAR", "2026-10-10", "Tipo trocado", "10.00", "RECEITA", mercado))
                .andExpect(status().isUnprocessableEntity());
        gravar(mvc, local, linha("E4", "CONFIRMAR_RECORRENCIA", "2026-10-10", "Aluguel", "10.00", "DESPESA", salario))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("recorrência")));

        assertThat(contar("lancamento")).isZero();
        assertThat(contar("importacao_lote")).isZero();
        assertThat(contar("importacao_ignorada")).isZero();
    }

    @Test
    void permissoesDeMembroEDono() throws Exception {
        Membro maria = adicionarMembro(dono, "maria@teste.com", PapelFamilia.MEMBRO);
        MockMvc comoMaria = mvcComo(maria);
        Long mercado = categoria("Habitação", "Supermercado");
        Long doDono = postarERetornarId("/api/locais-saldo",
                "{\"nome\": \"Conta do dono\", \"tipo\": \"CONTA_CORRENTE\", \"membroId\": %d}".formatted(dono.id()));
        Long daMaria = postarERetornarId("/api/locais-saldo",
                "{\"nome\": \"Conta da Maria\", \"tipo\": \"CONTA_CORRENTE\", \"membroId\": %d}".formatted(maria.id()));

        // Lugar de outra pessoa: 403 ao analisar e ao gravar; lugar conjunto (local) e o dela: pode.
        analisar(comoMaria, doDono, "x.ofx", ofx("F1|20261010|-10.00|Teste")).andExpect(status().isForbidden());
        gravar(comoMaria, doDono, linha("F1", "IMPORTAR", "2026-10-10", "Teste", "10.00", "DESPESA", mercado))
                .andExpect(status().isForbidden());
        analisar(comoMaria, 9999L, "x.ofx", ofx("F1|20261010|-10.00|Teste")).andExpect(status().isNotFound());
        analisar(comoMaria, daMaria, "x.ofx", ofx("F1|20261010|-10.00|Teste")).andExpect(status().isOk());

        // MEMBRO não lança em nome de outro; sem membroId, é dela.
        gravar(comoMaria, local, """
                {"chave": "F2", "acao": "IMPORTAR", "data": "2026-10-10", "descricao": "Em nome do dono", "valor": 5,
                 "tipo": "DESPESA", "categoriaId": %d, "membroId": %d}""".formatted(mercado, dono.id()))
                .andExpect(status().isForbidden());
        gravar(comoMaria, local, linha("F3", "IMPORTAR", "2026-10-10", "Dela", "5.00", "DESPESA", mercado))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select membro_id from lancamento", Long.class)).isEqualTo(maria.id());

        // DONO importa em nome de qualquer um e no lugar de qualquer um.
        Long loteDoDono = ((Number) com.jayway.jsonpath.JsonPath.read(gravar(mvc, daMaria,
                String.format("""
                        {"chave": "F4", "acao": "IMPORTAR", "data": "2026-10-11", "descricao": "Do dono", "valor": 7,
                         "tipo": "DESPESA", "categoriaId": %d, "membroId": %d}""", mercado, maria.id()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.loteId"))
                .longValue();

        // Listagem: todos veem; podeDesfazer só do que é dela (ou tudo, para o dono).
        comoMaria.perform(get("/api/importacoes"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(loteDoDono))
                .andExpect(jsonPath("$[0].podeDesfazer").value(false))
                .andExpect(jsonPath("$[0].localSaldoNome").value("Conta da Maria"))
                .andExpect(jsonPath("$[1].podeDesfazer").value(true))
                .andExpect(jsonPath("$[1].feitaPor").value("Usuário maria"));
        mvc.perform(get("/api/importacoes")).andExpect(jsonPath("$[1].podeDesfazer").value(true));

        comoMaria.perform(delete("/api/importacoes/{id}", loteDoDono)).andExpect(status().isForbidden());
        comoMaria.perform(delete("/api/importacoes/{id}", 9999)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/importacoes/{id}", loteDoDono)).andExpect(status().isOk())
                .andExpect(jsonPath("$.removidos").value(1));
        // O DONO desfaz a de outra pessoa.
        Long loteDaMaria = jdbc.queryForObject("select id from importacao_lote", Long.class);
        mvc.perform(delete("/api/importacoes/{id}", loteDaMaria)).andExpect(status().isOk());
        assertThat(contar("lancamento")).isZero();
    }

    @Test
    void desfazerMantemOEditadoEApagaAsChavesDasIgnoradas() throws Exception {
        Long mercado = categoria("Habitação", "Supermercado");
        Long loteId = ((Number) com.jayway.jsonpath.JsonPath.read(gravar(mvc, local,
                linha("G1", "IMPORTAR", "2026-10-10", "Fica", "10.00", "DESPESA", mercado),
                linha("G2", "IMPORTAR", "2026-10-11", "Sai", "20.00", "DESPESA", mercado),
                linha("G3", "IGNORAR", "2026-10-12", "Ignorada", "30.00", "DESPESA", null))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.loteId"))
                .longValue();
        Long fica = jdbc.queryForObject("select id from lancamento where importacao_chave = 'G1'", Long.class);

        mvc.perform(put("/api/lancamentos/{id}", fica).contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "Fica editado", "valor": 11, "data": "2026-10-10"}"""
                        .formatted(mercado)))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/importacoes/{id}", loteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removidos").value(1))
                .andExpect(jsonPath("$.mantidosPorEdicao").value(1));

        assertThat(contar("lancamento")).isEqualTo(1);
        assertThat(contar("importacao_ignorada")).isZero();
        mvc.perform(get("/api/importacoes")).andExpect(jsonPath("$", hasSize(0)));
        // Ignorada volta como NOVA; apagada (G2) também; a editada continua importada.
        analisar(mvc, local, "x.ofx", ofx("G1|20261010|-10.00|Fica", "G2|20261011|-20.00|Sai",
                "G3|20261012|-30.00|Ignorada"))
                .andExpect(jsonPath("$.linhas[0].status").value("JA_IMPORTADA"))
                .andExpect(jsonPath("$.linhas[1].status").value("NOVA"))
                .andExpect(jsonPath("$.linhas[2].status").value("NOVA"));
    }

    @Test
    void rejeitaArquivosInvalidosEGrandes() throws Exception {
        analisar(mvc, local, "extrato.csv", ofx("H1|20261010|-1.00|x")).andExpect(status().isBadRequest());
        analisar(mvc, local, "extrato.ofx", new byte[0]).andExpect(status().isBadRequest());
        analisar(mvc, local, "extrato.ofx", "a;b;c".getBytes(StandardCharsets.UTF_8)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Formato não suportado")));
        analisar(mvc, local, "extrato.ofx", "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY a SYSTEM \"file:///etc/passwd\">]><x>&a;</x>"
                .getBytes(StandardCharsets.UTF_8)).andExpect(status().isBadRequest());
        byte[] grande = new byte[2 * 1024 * 1024 + 1];
        Arrays.fill(grande, (byte) 'a');
        analisar(mvc, local, "extrato.ofx", grande).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("2 MB")));
        mvcSemUsuario.perform(multipart("/api/importacoes/analisar")
                .file(new MockMultipartFile("arquivo", "x.ofx", "text/plain", ofx("H1|20261010|-1.00|x")))
                .param("localSaldoId", local.toString()).with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void exigeSaldoInicialParaGravar() throws Exception {
        semSaldoInicial(dono);
        gravar(mvc, local, linha("I1", "IMPORTAR", "2026-10-10", "x", "10.00", "DESPESA", categoria("Habitação", "Supermercado")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void contaPendenteVemSugeridaEConfirmarGuardaAChaveEDesfazerDevolveAPendencia() throws Exception {
        Long aluguel = postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "Aluguel", "valor": 2000, "dia": 5, "frequencia": "MENSAL",
                 "inicio": "2026-10"}""".formatted(categoria("Habitação", "Aluguel ou financiamento")));
        byte[] arquivo = ofx("R1|20261006|-2040.00|Pix enviado IMOBILIARIA", "R2|20261007|-30.00|Padaria");

        analisar(mvc, local, "extrato.ofx", arquivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linhas[0].acaoSugerida").value("CONFIRMAR_RECORRENCIA"))
                .andExpect(jsonPath("$.linhas[0].ocorrenciaPendente.recorrenciaId").value(aluguel))
                .andExpect(jsonPath("$.linhas[0].ocorrenciaPendente.competencia").value("2026-10"))
                .andExpect(jsonPath("$.linhas[1].acaoSugerida").value("IMPORTAR"))
                .andExpect(jsonPath("$.linhas[1].ocorrenciaPendente").value(nullValue()));

        gravar(mvc, local, """
                {"chave": "R1", "acao": "CONFIRMAR_RECORRENCIA", "data": "2026-10-06", "descricao": "Aluguel",
                 "valor": 2040.00, "tipo": "DESPESA", "recorrenciaId": %d, "competencia": "2026-10"}""".formatted(aluguel))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.confirmadas").value(1));

        assertThat(jdbc.queryForObject(
                "select count(*) from lancamento where importacao_chave = 'R1' and recorrencia_id is not null",
                Integer.class)).isEqualTo(1);
        analisar(mvc, local, "extrato.ofx", arquivo)
                .andExpect(jsonPath("$.linhas[0].status").value("JA_IMPORTADA"))
                .andExpect(jsonPath("$.linhas[0].acaoSugerida").value("IGNORAR"));

        Long lote = jdbc.queryForObject("select id from importacao_lote", Long.class);
        mvc.perform(delete("/api/importacoes/" + lote).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removidos").value(1));
        analisar(mvc, local, "extrato.ofx", arquivo)
                .andExpect(jsonPath("$.linhas[0].acaoSugerida").value("CONFIRMAR_RECORRENCIA"));
    }
}
