package br.com.familianca;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.Query;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

@AnalyzeClasses(packages = "br.com.familianca", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    @ArchTest
    static final ArchRule dominioIsolado = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "org.hibernate..",
                    "..application..", "..adapter..");

    @ArchTest
    static final ArchRule aplicacaoNaoConheceAdapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule adapterEntradaNaoConheceAdapterSaida = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..");

    /**
     * Contra SQL injection: todo acesso ao banco passa pelo Spring Data (consultas derivadas ou JPQL com parâmetros
     * nomeados). Nada de JDBC direto, SQL nativo ou consulta montada em String no código da aplicação.
     */
    @ArchTest
    static final ArchRule semJdbcDireto = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "org.springframework.jdbc..")
            .because("SQL só pelo Spring Data, com parâmetros");

    @ArchTest
    static final ArchRule semConsultaMontadaNoEntityManager = noClasses()
            .should().callMethod(EntityManager.class, "createNativeQuery", String.class)
            .orShould().callMethod(EntityManager.class, "createQuery", String.class)
            .orShould().callMethod(EntityManager.class, "createQuery", String.class, Class.class)
            .because("consultas ficam em @Query (JPQL) com parâmetros nomeados");

    @ArchTest
    static final ArchRule semQueryNativa = noMethods()
            .should().beAnnotatedWith(new DescribedPredicate<JavaAnnotation<?>>("@Query(nativeQuery = true)") {
                @Override
                public boolean test(JavaAnnotation<?> a) {
                    return a.getRawType().isEquivalentTo(Query.class)
                            && Boolean.TRUE.equals(a.get("nativeQuery").orElse(false));
                }
            });
}
