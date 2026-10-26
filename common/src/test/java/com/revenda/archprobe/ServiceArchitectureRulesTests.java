package com.revenda.archprobe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.revenda.archprobe.application.service.ProbeCarServiceThatCiphers;
import com.revenda.archprobe.application.service.RegisterProbeCarService;
import com.revenda.archprobe.domain.model.ProbeCar;
import com.revenda.archprobe.domain.model.ProbeCarWithItsOwnClock;
import com.revenda.archprobe.domain.model.SpringManagedProbeCar;
import com.revenda.archprobe.domain.ports.in.RegisterProbeCar;
import com.revenda.archprobe.domain.ports.in.RegisterProbeCarUseCase;
import com.revenda.archprobe.domain.ports.out.ProbeCarStore;
import com.revenda.archprobe.domain.ports.out.SaveProbeCarPort;
import com.revenda.archprobe.domain.service.ProbePricingThatCallsTheApplication;
import com.revenda.archprobe.infrastructure.adapters.in.job.ConsoleProbeJob;
import com.revenda.archprobe.infrastructure.adapters.in.web.ProbeCarController;
import com.revenda.archprobe.infrastructure.adapters.in.web.ProbeCarControllerThatSkipsThePort;
import com.revenda.archprobe.infrastructure.adapters.in.web.ProbeCarControllerThatWritesTheTable;
import com.revenda.archprobe.infrastructure.adapters.in.web.ProbeCarControllerWithFieldInjection;
import com.revenda.archprobe.infrastructure.adapters.in.web.ProbeCarControllerWithPreAuthorize;
import com.revenda.archprobe.infrastructure.adapters.out.http.ProbeCarClientThatReachesAnotherService;
import com.revenda.archprobe.infrastructure.adapters.out.persistence.JdbcProbeCarAdapter;
import com.revenda.archprobe.infrastructure.config.ProbeSecurityConfiguration;
import com.revenda.common.test.ServiceArchitectureRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ServiceArchitectureRulesTests {

    private static final Map<String, ArchRule> RULES = Arrays.stream(ServiceArchitectureRules.class.getFields())
            .filter(field -> Modifier.isStatic(field.getModifiers()) && ArchRule.class.isAssignableFrom(field.getType()))
            .collect(Collectors.toMap(Field::getName, ServiceArchitectureRulesTests::read));

    private static final List<Class<?>> WELL_BUILT_SERVICE = List.of(
            ProbeCarApplication.class, ProbeCar.class, RegisterProbeCarUseCase.class, SaveProbeCarPort.class,
            RegisterProbeCarService.class, ProbeCarController.class, JdbcProbeCarAdapter.class);

    static Stream<Arguments> violations() {
        return Stream.of(
                arguments("domainIsPlainJava", SpringManagedProbeCar.class),
                arguments("layersPointInward", ProbePricingThatCallsTheApplication.class),
                arguments("adaptersTalkToPorts", ProbeCarControllerThatSkipsThePort.class),
                arguments("inboundAdaptersDoNotReachOutbound", ProbeCarControllerThatWritesTheTable.class),
                arguments("inboundPortsAreUseCases", RegisterProbeCar.class),
                arguments("outboundPortsAreNamedPort", ProbeCarStore.class),
                arguments("servicesDoNotReachEachOther", ProbeCarClientThatReachesAnotherService.class),
                arguments("jdbcOnlyInThePersistenceAdapter", ProbeCarControllerThatWritesTheTable.class),
                arguments("cipherOnlyInThePersistenceAdapter", ProbeCarServiceThatCiphers.class),
                arguments("clockIsInjected", ProbeCarWithItsOwnClock.class),
                arguments("accessRulesLiveInSecurityRoutes", ProbeCarControllerWithPreAuthorize.class),
                arguments("accessRulesLiveInSecurityRoutes", ProbeSecurityConfiguration.class),
                arguments("noStandardStreams", ConsoleProbeJob.class),
                arguments("noJavaUtilLogging", ConsoleProbeJob.class),
                arguments("noFieldInjection", ProbeCarControllerWithFieldInjection.class));
    }

    @Test
    void aWellBuiltServicePassesEveryRule() {
        JavaClasses service = importClasses(WELL_BUILT_SERVICE);

        assertThat(RULES).isNotEmpty();
        RULES.values().forEach(rule -> rule.check(service));
    }

    @ParameterizedTest(name = "{0} recusa {1}")
    @MethodSource("violations")
    void eachRuleRefusesTheClassThatBreaksIt(String rule, Class<?> offender) {
        List<Class<?>> serviceWithTheOffender = new ArrayList<>(WELL_BUILT_SERVICE);
        serviceWithTheOffender.add(offender);

        EvaluationResult result = RULES.get(rule).evaluate(importClasses(serviceWithTheOffender));

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains(offender.getName()));
    }

    @Test
    void everyRuleHasAClassThatBreaksIt() {
        assertThat(violations().map(each -> (String) each.get()[0]).collect(Collectors.toSet()))
                .isEqualTo(RULES.keySet());
    }

    @Test
    void theRulesAllowEmptyLayersWhileTheServiceIsNew() {
        JavaClasses applicationClassOnly = importClasses(List.of(ProbeCarApplication.class));

        RULES.values().forEach(rule -> rule.check(applicationClassOnly));
    }

    private static JavaClasses importClasses(List<Class<?>> classes) {
        return new ClassFileImporter().importClasses(classes);
    }

    private static ArchRule read(Field field) {
        try {
            return (ArchRule) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
