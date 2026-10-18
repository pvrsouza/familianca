package br.com.familianca.adapter.out.persistence;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.BasicTypeRegistry;
import org.hibernate.type.StandardBasicTypes;

/**
 * Funções do Postgres usadas no JPQL da busca de descrições (extensões da V10), com tipo de retorno declarado:
 * {@code sem_acento(texto)} = unaccent e {@code semelhanca(termo, texto)} = word_similarity do pg_trgm (0 a 1, quanto
 * o termo aparece dentro do texto, tolerando erro de digitação). Registradas em
 * META-INF/services/org.hibernate.boot.model.FunctionContributor.
 */
public class FuncoesBusca implements FunctionContributor {

    /** Abaixo disso a semelhança por trigramas é ruído ("me" parece com tudo): só vale o "contém". */
    private static final int TAMANHO_MINIMO_SEMELHANCA = 3;

    /** word_similarity a partir da qual "mercdo" ainda acha "Mercado do mês", sem trazer descrições sem relação. */
    private static final double SEMELHANCA_MINIMA = 0.4;

    @Override
    public void contributeFunctions(FunctionContributions funcoes) {
        BasicTypeRegistry tipos = funcoes.getTypeConfiguration().getBasicTypeRegistry();
        funcoes.getFunctionRegistry().registerPattern("sem_acento", "unaccent(?1)",
                tipos.resolve(StandardBasicTypes.STRING));
        funcoes.getFunctionRegistry().registerPattern("semelhanca", "word_similarity(?1, ?2)",
                tipos.resolve(StandardBasicTypes.DOUBLE));
    }

    /** "%termo%" para o like, com %, _ e a barra invertida tratados como texto (escape '\\'). */
    static String contem(String termo) {
        return "%" + termo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** Semelhança mínima para o termo; termo curto demais nunca alcança (1 &gt; máximo da função). */
    static double minimo(String termo) {
        return termo.length() < TAMANHO_MINIMO_SEMELHANCA ? 2 : SEMELHANCA_MINIMA;
    }
}
