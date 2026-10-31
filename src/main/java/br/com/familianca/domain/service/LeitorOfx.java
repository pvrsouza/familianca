package br.com.familianca.domain.service;

import br.com.familianca.domain.Validacoes;
import br.com.familianca.domain.exception.ArquivoInvalidoException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê extrato OFX 1.x (SGML, tags sem fechamento) e 2.x (XML) e OFC. Não usa parser de XML: só procura as tags com
 * uma expressão regular linear, então não há DTD, entidade externa nem XXE. Aceita UTF-8 ou Windows-1252.
 */
public final class LeitorOfx {

    public static final int MAXIMO_LINHAS = 5_000;
    public static final int ANO_MIN = 2000;
    public static final int ANO_MAX = 2100;
    public static final int DESCRICAO_MAXIMO = 200;
    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("9999999999.99");
    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z][A-Za-z0-9._]{0,30})>([^<]*)");
    private static final Set<String> FIM_DA_LISTA = Set.of("BANKTRANLIST", "STMTRS", "CCSTMTRS", "STMTTRNRS",
            "CCSTMTTRNRS", "ACCTSTMT");

    private LeitorOfx() {
    }

    /** Valor com sinal: negativo = débito. A chave identifica a linha para não importar duas vezes. */
    public record Transacao(LocalDate data, BigDecimal valor, String descricao, String chave) {
    }

    public record Extrato(String banco, String conta, LocalDate periodoDe, LocalDate periodoAte,
                          List<Transacao> transacoes) {
    }

    public static Extrato ler(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ArquivoInvalidoException("O arquivo está vazio");
        }
        String texto = decodificar(bytes);
        int inicio = inicioDoDocumento(texto);
        Matcher m = TAG.matcher(texto);
        m.region(inicio, texto.length());

        Map<String, String> geral = new HashMap<>();
        Map<String, String> atual = null;
        List<Map<String, String>> brutas = new ArrayList<>();
        while (m.find()) {
            boolean fecha = !m.group(1).isEmpty();
            String tag = m.group(2).toUpperCase(Locale.ROOT);
            if (tag.equals("STMTTRN") || (fecha && FIM_DA_LISTA.contains(tag))) {
                if (atual != null) {
                    brutas.add(atual);
                    atual = null;
                }
                if (tag.equals("STMTTRN") && !fecha) {
                    if (brutas.size() >= MAXIMO_LINHAS) {
                        throw new ArquivoInvalidoException("O extrato tem mais de " + MAXIMO_LINHAS
                                + " lançamentos: envie um período menor");
                    }
                    atual = new HashMap<>();
                }
            } else if (!fecha) {
                String valor = desescapar(m.group(3).strip());
                (atual != null ? atual : geral).putIfAbsent(tag, valor);
            }
        }
        if (atual != null) {
            brutas.add(atual);
        }
        if (brutas.isEmpty()) {
            throw new ArquivoInvalidoException("Não encontrei lançamentos neste arquivo");
        }

        Map<String, Integer> ocorrencias = new HashMap<>();
        List<Transacao> transacoes = new ArrayList<>();
        for (Map<String, String> b : brutas) {
            BigDecimal valor = valor(b.get("TRNAMT"));
            if (valor.signum() == 0) {
                continue;
            }
            LocalDate data = data(b.get("DTPOSTED"));
            String descricao = descricao(b);
            transacoes.add(new Transacao(data, valor, descricao,
                    chave(b.get("FITID"), data, valor, descricao, ocorrencias)));
        }
        if (transacoes.isEmpty()) {
            throw new ArquivoInvalidoException("Não encontrei lançamentos com valor neste arquivo");
        }
        LocalDate de = dataOuNula(geral.get("DTSTART"));
        LocalDate ate = dataOuNula(geral.get("DTEND"));
        if (de == null || ate == null || ate.isBefore(de)) {
            de = transacoes.stream().map(Transacao::data).min(LocalDate::compareTo).orElseThrow();
            ate = transacoes.stream().map(Transacao::data).max(LocalDate::compareTo).orElseThrow();
        }
        String banco = geral.getOrDefault("ORG", geral.get("BANKID"));
        return new Extrato(vazioParaNulo(banco), vazioParaNulo(geral.get("ACCTID")), de, ate, transacoes);
    }

    /** Sem acento, minúsculo e com espaços únicos: "Mércado  X" e "mercado x" são a mesma descrição. */
    public static String normalizar(String texto) {
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String decodificar(byte[] b) {
        boolean utf16 = b.length >= 2 && ((b[0] == (byte) 0xFF && b[1] == (byte) 0xFE)
                || (b[0] == (byte) 0xFE && b[1] == (byte) 0xFF));
        boolean binario = false;
        for (byte x : b) {
            if (x == 0) {
                binario = true;
                break;
            }
        }
        if (utf16 || binario) {
            throw new ArquivoInvalidoException("Formato não suportado: envie um arquivo OFX ou OFC");
        }
        int ini = b.length >= 3 && b[0] == (byte) 0xEF && b[1] == (byte) 0xBB && b[2] == (byte) 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b, ini, b.length - ini)).toString();
        } catch (CharacterCodingException e) {
            return new String(b, ini, b.length - ini, Charset.forName("windows-1252"));
        }
    }

    /** Posição de <OFX> ou <OFC> (o cabeçalho do 1.x e o <?xml?> do 2.x vêm antes). */
    private static int inicioDoDocumento(String texto) {
        String inicio = texto.substring(0, Math.min(texto.length(), 8192)).toLowerCase(Locale.ROOT);
        int ofx = inicio.indexOf("<ofx>");
        int ofc = inicio.indexOf("<ofc>");
        int pos = ofx >= 0 && (ofc < 0 || ofx < ofc) ? ofx : ofc;
        if (pos < 0) {
            throw new ArquivoInvalidoException("Formato não suportado: envie um arquivo OFX ou OFC");
        }
        return pos;
    }

    private static String descricao(Map<String, String> b) {
        String nome = b.getOrDefault("NAME", "");
        String memo = b.getOrDefault("MEMO", "");
        String bruta = memo.isBlank() ? nome : nome.isBlank() || normalizar(memo).contains(normalizar(nome)) ? memo
                : nome + " - " + memo;
        String limpa = Validacoes.limpar(bruta).replaceAll("\\s+", " ").strip();
        if (limpa.isEmpty()) {
            limpa = b.getOrDefault("TRNTYPE", "").isBlank() ? "Sem descrição" : b.get("TRNTYPE");
        }
        return limpa.length() > DESCRICAO_MAXIMO ? limpa.substring(0, DESCRICAO_MAXIMO).strip() : limpa;
    }

    /** FITID quando existe (repetido no mesmo arquivo ganha #2, #3...); senão H:hash#ocorrência. */
    private static String chave(String fitid, LocalDate data, BigDecimal valor, String descricao,
                                Map<String, Integer> ocorrencias) {
        String f = fitid == null ? "" : Validacoes.limpar(fitid).strip();
        boolean temFitid = !f.isEmpty() && f.length() <= 200;
        String base = temFitid ? f : "H:" + hash(data + "|" + valor.toPlainString() + "|" + normalizar(descricao));
        int n = ocorrencias.merge(base, 1, Integer::sum);
        return !temFitid || n > 1 ? base + "#" + n : base;
    }

    private static String hash(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BigDecimal valor(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ArquivoInvalidoException("Há lançamento sem valor no arquivo");
        }
        String t = texto.replace(" ", "");
        int virgula = t.lastIndexOf(',');
        int ponto = t.lastIndexOf('.');
        t = virgula > ponto ? t.replace(".", "").replace(',', '.') : t.replace(",", "");
        BigDecimal v;
        try {
            v = new BigDecimal(t).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException | ArithmeticException e) {
            throw new ArquivoInvalidoException("Há lançamento com valor inválido no arquivo");
        }
        if (v.abs().compareTo(VALOR_MAXIMO) > 0) {
            throw new ArquivoInvalidoException("Há lançamento com valor grande demais no arquivo");
        }
        return v;
    }

    private static LocalDate data(String texto) {
        LocalDate d = dataOuNula(texto);
        if (d == null) {
            throw new ArquivoInvalidoException("Há lançamento sem data válida no arquivo");
        }
        return d;
    }

    /** AAAAMMDD no começo (o resto é hora e fuso); fora de 2000 a 2100 vale como inválida. */
    private static LocalDate dataOuNula(String texto) {
        if (texto == null || texto.length() < 8 || !texto.substring(0, 8).chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            LocalDate d = LocalDate.of(Integer.parseInt(texto.substring(0, 4)),
                    Integer.parseInt(texto.substring(4, 6)), Integer.parseInt(texto.substring(6, 8)));
            return d.getYear() < ANO_MIN || d.getYear() > ANO_MAX ? null : d;
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static String desescapar(String t) {
        return t.indexOf('&') < 0 ? t : t.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }

    private static String vazioParaNulo(String t) {
        return t == null || t.isBlank() ? null : Validacoes.limpar(t).strip();
    }
}
