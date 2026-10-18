package br.com.familianca.domain.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Código de 6 dígitos que muda a cada 30 s (TOTP, RFC 6238, HMAC-SHA1): o padrão que Google Authenticator,
 * Microsoft Authenticator, Authy, 1Password etc. leem pelo QR code. O segredo trafega em Base32 (RFC 4648).
 */
public final class Totp {

    public static final int PASSO_SEGUNDOS = 30;
    public static final int DIGITOS = 6;
    /** Aceita o passo anterior e o seguinte: relógio do celular um pouco adiantado ou atrasado. */
    private static final int TOLERANCIA_PASSOS = 1;
    private static final int BYTES_SEGREDO = 20;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Totp() {
    }

    /** 160 bits aleatórios (tamanho recomendado pela RFC 4226), em Base32 sem padding. */
    public static String novoSegredo() {
        byte[] bytes = new byte[BYTES_SEGREDO];
        ALEATORIO.nextBytes(bytes);
        return base32(bytes);
    }

    public static long passo(long epochSegundos) {
        return Math.floorDiv(epochSegundos, PASSO_SEGUNDOS);
    }

    public static String codigo(String segredoBase32, long passo) {
        byte[] mensagem = new byte[8];
        for (int i = 7, p = 0; i >= 0; i--, p++) {
            mensagem[i] = (byte) (passo >>> (8 * p));
        }
        byte[] hash = hmacSha1(deBase32(segredoBase32), mensagem);
        int deslocamento = hash[hash.length - 1] & 0x0f;
        int binario = ((hash[deslocamento] & 0x7f) << 24) | ((hash[deslocamento + 1] & 0xff) << 16)
                | ((hash[deslocamento + 2] & 0xff) << 8) | (hash[deslocamento + 3] & 0xff);
        return String.format("%0" + DIGITOS + "d", binario % (int) Math.pow(10, DIGITOS));
    }

    /**
     * Passo do código, se ele vale agora (dentro da tolerância) e é posterior a {@code ultimoPassoUsado}: o mesmo
     * código não serve duas vezes.
     */
    public static OptionalLong verificar(String segredoBase32, String codigo, long epochSegundos, Long ultimoPassoUsado) {
        if (codigo == null || !codigo.matches("\\d{" + DIGITOS + "}")) {
            return OptionalLong.empty();
        }
        long atual = passo(epochSegundos);
        for (long p = atual - TOLERANCIA_PASSOS; p <= atual + TOLERANCIA_PASSOS; p++) {
            boolean naoUsado = ultimoPassoUsado == null || p > ultimoPassoUsado;
            // Comparação em tempo constante: não revela quantos dígitos acertou.
            if (naoUsado && MessageDigest.isEqual(codigo(segredoBase32, p).getBytes(), codigo.getBytes())) {
                return OptionalLong.of(p);
            }
        }
        return OptionalLong.empty();
    }

    static String base32(byte[] bytes) {
        StringBuilder texto = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                texto.append(BASE32.charAt((buffer >>> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            texto.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return texto.toString();
    }

    static byte[] deBase32(String texto) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : texto.toUpperCase(Locale.ROOT).replace("=", "").replace(" ", "").toCharArray()) {
            int valor = BASE32.indexOf(c);
            if (valor < 0) {
                throw new IllegalArgumentException("Segredo fora do Base32");
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                bytes.write((buffer >>> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] hmacSha1(byte[] chave, byte[] mensagem) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(chave, "HmacSHA1"));
            return mac.doFinal(mensagem);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
