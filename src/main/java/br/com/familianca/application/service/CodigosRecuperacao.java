package br.com.familianca.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Códigos de recuperação do segundo fator: 10 códigos de 10 caracteres (50 bits cada, no formato xxxxx-xxxxx, sem
 * letras que se confundem). No banco, só o SHA-256: com essa entropia e o limite de tentativas, não precisa de BCrypt.
 */
final class CodigosRecuperacao {

    static final int QUANTIDADE = 10;
    private static final String ALFABETO = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private CodigosRecuperacao() {
    }

    static List<String> gerar() {
        return IntStream.range(0, QUANTIDADE).mapToObj(i -> bloco() + "-" + bloco()).toList();
    }

    /** Ignora maiúsculas, espaços e o hífen: "ABCDE FGHJK" = "abcde-fghjk". */
    static String hash(String codigo) {
        String normalizado = codigo.toLowerCase(Locale.ROOT).replaceAll("[\\s-]", "");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalizado.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String bloco() {
        StringBuilder bloco = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            bloco.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        return bloco.toString();
    }
}
