package br.com.familianca.domain;

import br.com.familianca.domain.exception.RegraNegocioException;

import java.text.Normalizer;
import java.util.regex.Pattern;

/** Todo texto livre do domínio passa por aqui: obrigatório/opcional, sem espaços nas pontas e {@link #limpar limpo}. */
public final class Validacoes {

    /**
     * Caracteres de controle (menos quebra de linha e tabulação) e de controle de direção do Unicode, que invertem
     * a exibição do texto (ex.: um nome que aparece diferente para o resto da família).
     */
    private static final Pattern INVISIVEIS = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]|[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]");

    private Validacoes() {
    }

    public static String obrigatorio(String valor, String campo) {
        String limpo = limpar(valor);
        if (limpo == null || limpo.isBlank()) {
            throw new RegraNegocioException("Campo obrigatório: " + campo);
        }
        return limpo.trim();
    }

    public static <T> T obrigatorio(T valor, String campo) {
        if (valor == null) {
            throw new RegraNegocioException("Campo obrigatório: " + campo);
        }
        return valor;
    }

    public static String opcional(String valor) {
        String limpo = limpar(valor);
        return limpo == null || limpo.isBlank() ? null : limpo.trim();
    }

    /**
     * Normaliza para NFC (o mesmo nome digitado em teclados diferentes vira o mesmo texto) e remove os caracteres
     * {@link #INVISIVEIS}. Não escapa HTML: a API guarda o texto como digitado e quem exibe (front) faz o escape.
     */
    public static String limpar(String valor) {
        return valor == null ? null : INVISIVEIS.matcher(Normalizer.normalize(valor, Normalizer.Form.NFC)).replaceAll("");
    }
}
