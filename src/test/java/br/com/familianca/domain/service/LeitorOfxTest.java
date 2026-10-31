package br.com.familianca.domain.service;

import br.com.familianca.domain.exception.ArquivoInvalidoException;
import br.com.familianca.domain.service.LeitorOfx.Extrato;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeitorOfxTest {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    /** Estilo do Banco Inter: OFX 1.x em SGML (tags sem fechamento), Windows-1252. */
    private static final String INTER = """
            OFXHEADER:100
            DATA:OFXSGML
            VERSION:102
            SECURITY:NONE
            ENCODING:USASCII
            CHARSET:1252
            COMPRESSION:NONE
            OLDFILEUID:NONE
            NEWFILEUID:NONE

            <OFX>
            <SIGNONMSGSRSV1><SONRS><STATUS><CODE>0<SEVERITY>INFO</STATUS><DTSERVER>20261031120000[-3:BRT]
            <LANGUAGE>POR<FI><ORG>Banco Inter S.A.<FID>77</FI></SONRS></SIGNONMSGSRSV1>
            <BANKMSGSRSV1><STMTTRNRS><TRNUID>1<STATUS><CODE>0<SEVERITY>INFO</STATUS>
            <STMTRS><CURDEF>BRL<BANKACCTFROM><BANKID>077<ACCTID>12341234-5<ACCTTYPE>CHECKING</BANKACCTFROM>
            <BANKTRANLIST><DTSTART>20261001000000[-3:BRT]<DTEND>20261031000000[-3:BRT]
            <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261028120000[-3:BRT]<TRNAMT>-45.90<FITID>2026102800001
            <MEMO>Compra no débito - PADARIA AÇÚCAR   &amp; CIA</STMTTRN>
            <STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20261005120000[-3:BRT]<TRNAMT>9000.00<FITID>2026100500002
            <NAME>PIX RECEBIDO<MEMO>Pix recebido - JOÃO DA SILVA</STMTTRN>
            </BANKTRANLIST><LEDGERBAL><BALAMT>100.00<DTASOF>20261031</LEDGERBAL></STMTRS></STMTTRNRS></BANKMSGSRSV1>
            </OFX>
            """;

    private static final String CARTAO_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <?OFX OFXHEADER="200" VERSION="211" SECURITY="NONE" OLDFILEUID="NONE" NEWFILEUID="NONE"?>
            <OFX><CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS><CURDEF>BRL</CURDEF>
            <CCACCTFROM><ACCTID>5555444433332222</ACCTID></CCACCTFROM>
            <BANKTRANLIST><DTSTART>20260901</DTSTART><DTEND>20260930</DTEND>
            <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260915</DTPOSTED><TRNAMT>-1.234,56</TRNAMT>
            <MEMO>Açaí & Café — loja</MEMO></STMTTRN>
            <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260915</DTPOSTED><TRNAMT>-10.00</TRNAMT>
            <MEMO>Estacionamento</MEMO></STMTTRN>
            <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260915</DTPOSTED><TRNAMT>-10.00</TRNAMT>
            <MEMO>Estacionamento</MEMO></STMTTRN>
            </BANKTRANLIST></CCSTMTRS></CCSTMTTRNRS></CREDITCARDMSGSRSV1></OFX>
            """;

    @Test
    void leOfx1EmSgmlComWindows1252() {
        Extrato e = LeitorOfx.ler(INTER.getBytes(WINDOWS_1252));

        assertThat(e.banco()).isEqualTo("Banco Inter S.A.");
        assertThat(e.conta()).isEqualTo("12341234-5");
        assertThat(e.periodoDe()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(e.periodoAte()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(e.transacoes()).hasSize(2);
        assertThat(e.transacoes().get(0).descricao()).isEqualTo("Compra no débito - PADARIA AÇÚCAR & CIA");
        assertThat(e.transacoes().get(0).valor()).isEqualByComparingTo("-45.90");
        assertThat(e.transacoes().get(0).data()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(e.transacoes().get(0).chave()).isEqualTo("2026102800001");
        // NAME que o MEMO já contém não se repete.
        assertThat(e.transacoes().get(1).descricao()).isEqualTo("Pix recebido - JOÃO DA SILVA");
        assertThat(e.transacoes().get(1).valor()).isEqualByComparingTo(new BigDecimal("9000"));
    }

    @Test
    void leOfx2EmXmlUtf8DeCartaoSemFitid() {
        Extrato e = LeitorOfx.ler(CARTAO_XML.getBytes(StandardCharsets.UTF_8));

        assertThat(e.banco()).isNull();
        assertThat(e.transacoes()).hasSize(3);
        assertThat(e.transacoes().get(0).descricao()).isEqualTo("Açaí & Café — loja");
        assertThat(e.transacoes().get(0).valor()).isEqualByComparingTo("-1234.56");
        // Sem FITID: hash + ocorrência dentro do arquivo; linhas iguais ficam com chaves diferentes.
        assertThat(e.transacoes().get(0).chave()).startsWith("H:").endsWith("#1");
        assertThat(e.transacoes().get(1).chave()).endsWith("#1");
        assertThat(e.transacoes().get(2).chave()).endsWith("#2")
                .startsWith(e.transacoes().get(1).chave().substring(0, 34));
        // Estável: ler de novo dá as mesmas chaves.
        assertThat(LeitorOfx.ler(CARTAO_XML.getBytes(StandardCharsets.UTF_8)).transacoes().get(0).chave())
                .isEqualTo(e.transacoes().get(0).chave());
    }

    @Test
    void fitidRepetidoNoMesmoArquivoGanhaSufixo() {
        String ofx = "<OFX><BANKTRANLIST><STMTTRN><DTPOSTED>20261001<TRNAMT>-1<FITID>X<MEMO>a</STMTTRN>"
                + "<STMTTRN><DTPOSTED>20261001<TRNAMT>-1<FITID>X<MEMO>a</STMTTRN></BANKTRANLIST></OFX>";

        Extrato e = LeitorOfx.ler(ofx.getBytes(StandardCharsets.UTF_8));

        assertThat(e.transacoes()).extracting(LeitorOfx.Transacao::chave).containsExactly("X", "X#2");
    }

    @Test
    void doctypeEEntidadeExternaNaoSaoResolvidos() {
        String ofx = """
                <?xml version="1.0"?>
                <!DOCTYPE OFX [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <OFX><BANKTRANLIST><STMTTRN><DTPOSTED>20261001</DTPOSTED><TRNAMT>-5</TRNAMT><FITID>1</FITID>
                <MEMO>&xxe;</MEMO></STMTTRN></BANKTRANLIST></OFX>""";

        Extrato e = LeitorOfx.ler(ofx.getBytes(StandardCharsets.UTF_8));

        assertThat(e.transacoes().get(0).descricao()).isEqualTo("&xxe;");
    }

    @Test
    void rejeitaArquivoQueNaoEOfx() {
        assertThatThrownBy(() -> LeitorOfx.ler(new byte[0])).isInstanceOf(ArquivoInvalidoException.class);
        assertThatThrownBy(() -> LeitorOfx.ler("a;b;c\n1;2;3".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("Formato não suportado");
        assertThatThrownBy(() -> LeitorOfx.ler(new byte[]{'<', 'O', 'F', 'X', '>', 0, 1, 2}))
                .isInstanceOf(ArquivoInvalidoException.class);
        assertThatThrownBy(() -> LeitorOfx.ler("<OFX><BANKTRANLIST></BANKTRANLIST></OFX>".getBytes()))
                .isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("lançamentos");
    }

    @Test
    void rejeitaValorDataEQuantidadeInvalidos() {
        assertThatThrownBy(() -> LeitorOfx.ler(
                "<OFX><STMTTRN><DTPOSTED>20261001<TRNAMT>abc</STMTTRN></OFX>".getBytes()))
                .isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("valor inválido");
        assertThatThrownBy(() -> LeitorOfx.ler(
                "<OFX><STMTTRN><DTPOSTED>19991001<TRNAMT>-1</STMTTRN></OFX>".getBytes()))
                .isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("data");
        assertThatThrownBy(() -> LeitorOfx.ler(
                "<OFX><STMTTRN><DTPOSTED>20261001<TRNAMT>-99999999999</STMTTRN></OFX>".getBytes()))
                .isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("grande demais");

        StringBuilder muitos = new StringBuilder("<OFX><BANKTRANLIST>");
        for (int i = 0; i <= LeitorOfx.MAXIMO_LINHAS; i++) {
            muitos.append("<STMTTRN><DTPOSTED>20261001<TRNAMT>-1<FITID>").append(i).append("</STMTTRN>");
        }
        assertThatThrownBy(() -> LeitorOfx.ler(muitos.toString().getBytes()))
                .isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("mais de 5000");
    }

    @Test
    void descricaoLongaEhTruncadaEEspacosNormalizados() {
        String ofx = "<OFX><STMTTRN><DTPOSTED>20261001<TRNAMT>-1<FITID>1<MEMO>" + "ab  ".repeat(100) + "</STMTTRN></OFX>";

        String d = LeitorOfx.ler(ofx.getBytes()).transacoes().get(0).descricao();

        assertThat(d).hasSizeLessThanOrEqualTo(200).doesNotContain("  ");
    }
}
