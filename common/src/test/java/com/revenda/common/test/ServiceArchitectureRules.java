package com.revenda.common.test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.revenda.common.crypto.BlindIndexer;
import com.revenda.common.crypto.FieldCipher;
import com.revenda.common.time.SqlTime;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

/**
 * As regras de arquitetura de todo serviço. Cada serviço as roda num teste com
 * {@code @AnalyzeClasses(packagesOf = <Serviço>Application.class, importOptions = DoNotIncludeTests.class)} e
 * {@code @ArchTest static final ArchTests rules = ArchTests.in(ServiceArchitectureRules.class)}. As regras de uma
 * camada ainda vazia passam até a camada nascer.
 */
public final class ServiceArchitectureRules {

    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String INFRASTRUCTURE = "..infrastructure..";
    private static final String ADAPTERS = "..infrastructure.adapters..";
    private static final String INBOUND_ADAPTERS = "..infrastructure.adapters.in..";
    private static final String OUTBOUND_ADAPTERS = "..infrastructure.adapters.out..";
    private static final String PERSISTENCE_ADAPTERS = "..infrastructure.adapters.out.persistence..";
    private static final String CONFIG = "..infrastructure.config..";
    private static final String BASE_PACKAGE = "com.revenda.";
    private static final String COMMON = "common";

    @ArchTest
    public static final ArchRule domainIsPlainJava = classes()
            .that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "lombok..", DOMAIN, "com.revenda.common.money..", "com.revenda.common.error")
            .because("o domínio é Java puro: só o próprio domínio e as partes puras do common (dinheiro e erro)")
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule layersPointInward = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("domínio").definedBy(DOMAIN)
            .layer("aplicação").definedBy(APPLICATION)
            .layer("infraestrutura").definedBy(INFRASTRUCTURE)
            .whereLayer("infraestrutura").mayNotBeAccessedByAnyLayer()
            .whereLayer("aplicação").mayOnlyBeAccessedByLayers("infraestrutura")
            .whereLayer("domínio").mayOnlyBeAccessedByLayers("aplicação", "infraestrutura")
            .because("as dependências apontam para dentro: infraestrutura → aplicação → domínio")
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule adaptersTalkToPorts = noClasses()
            .that().resideInAPackage(ADAPTERS)
            .should().dependOnClassesThat().resideInAPackage(APPLICATION)
            .because("o adapter chama a porta do domínio (UseCase, Port), nunca a implementação do caso de uso")
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule inboundAdaptersDoNotReachOutbound = noClasses()
            .that().resideInAPackage(INBOUND_ADAPTERS)
            .should().dependOnClassesThat().resideInAPackage(OUTBOUND_ADAPTERS)
            .because("a entrada passa pelo caso de uso; só ele usa as portas de saída")
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule inboundPortsAreUseCases = classes()
            .that().resideInAPackage("..domain.ports.in..").and().areInterfaces().and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("UseCase")
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule outboundPortsAreNamedPort = classes()
            .that().resideInAPackage("..domain.ports.out..").and().areInterfaces().and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Port")
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule servicesDoNotReachEachOther = classes()
            .that().resideInAPackage("com.revenda..")
            .should(onlyReachTheirOwnServiceAndCommon())
            .because("cada serviço fala com o outro pela API ou pela fila, nunca pelo código");

    @ArchTest
    public static final ArchRule jdbcOnlyInThePersistenceAdapter = noClasses()
            .that().resideOutsideOfPackages(PERSISTENCE_ADAPTERS, CONFIG)
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..")
            .orShould().dependOnClassesThat().belongToAnyOf(SqlTime.class)
            .because("SQL e JdbcClient moram só no adapter de persistência");

    @ArchTest
    public static final ArchRule cipherOnlyInThePersistenceAdapter = noClasses()
            .that().resideOutsideOfPackages(PERSISTENCE_ADAPTERS, CONFIG)
            .should().dependOnClassesThat().belongToAnyOf(FieldCipher.class, BlindIndexer.class)
            .because("o dado pessoal é cifrado só no adapter de persistência; o resto vê o valor em claro");

    @ArchTest
    public static final ArchRule clockIsInjected = noClasses()
            .should().callMethod(Instant.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(LocalDate.class, "now")
            .orShould().callMethod(LocalTime.class, "now")
            .orShould().callMethod(OffsetDateTime.class, "now")
            .orShould().callMethod(ZonedDateTime.class, "now")
            .orShould().callMethod(Clock.class, "systemUTC")
            .orShould().callMethod(Clock.class, "systemDefaultZone")
            .because("a hora vem do Clock injetado, que o teste consegue mover");

    @ArchTest
    public static final ArchRule accessRulesLiveInSecurityRoutes = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.security.access.prepost..", "jakarta.annotation.security..")
            .orShould().dependOnClassesThat().haveFullyQualifiedName(
                    "org.springframework.security.access.annotation.Secured")
            .orShould().dependOnClassesThat().haveFullyQualifiedName(
                    "org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity")
            .orShould().dependOnClassesThat().haveFullyQualifiedName(
                    "org.springframework.security.web.SecurityFilterChain")
            .because("quem pode chamar cada rota fica no bean SecurityRoutes, nunca em anotação nem em outra cadeia de filtros");

    @ArchTest
    public static final ArchRule noStandardStreams = noClasses()
            .should(GeneralCodingRules.ACCESS_STANDARD_STREAMS)
            .because("System.out e printStackTrace escapam do mascarador do log");

    @ArchTest
    public static final ArchRule noJavaUtilLogging = noClasses()
            .should(GeneralCodingRules.USE_JAVA_UTIL_LOGGING)
            .because("o log sai pelo SLF4J, que passa pelo mascarador");

    @ArchTest
    public static final ArchRule noFieldInjection = noFields()
            .should(GeneralCodingRules.BE_ANNOTATED_WITH_AN_INJECTION_ANNOTATION)
            .because("a dependência entra pelo construtor (@RequiredArgsConstructor)")
            .allowEmptyShould(true);

    private ServiceArchitectureRules() {
    }

    private static ArchCondition<JavaClass> onlyReachTheirOwnServiceAndCommon() {
        return new ArchCondition<>("só depender do próprio serviço e do common") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String own = serviceOf(origin.getPackageName());
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    String target = serviceOf(dependency.getTargetClass().getPackageName());
                    if (target != null && !target.equals(own) && !target.equals(COMMON)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static String serviceOf(String packageName) {
        if (!packageName.startsWith(BASE_PACKAGE)) {
            return null;
        }
        String rest = packageName.substring(BASE_PACKAGE.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
