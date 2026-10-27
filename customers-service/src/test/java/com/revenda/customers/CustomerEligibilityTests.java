package com.revenda.customers;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.test.ApiDriver;
import com.revenda.common.test.MovableClock;
import com.revenda.common.test.SyntheticData;
import com.revenda.common.test.TestTokens;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * RN-05 na elegibilidade que o {@code sales} pede antes da compra: cadastro ativo e 18 anos ou mais na data civil de
 * São Paulo. Cada teste lê a data no relógio, que só anda para a frente, para não depender da ordem dos testes.
 */
@ExtendWith(OutputCaptureExtension.class)
class CustomerEligibilityTests extends CustomersApiTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Instant WITH_NANOSECONDS = Instant.parse("2026-10-27T15:00:00.123456789Z");

    @Autowired
    private MovableClock clock;

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockWithNanoseconds {

        @Bean
        MovableClock clockWithNanoseconds() {
            return new MovableClock(WITH_NANOSECONDS);
        }
    }

    @Test
    void theDayBeforeTheEighteenthBirthdayIsUnderageAndTheDayItselfAndTheNextAreEligible() {
        LocalDate eighteenYearsAgo = today().minusYears(18);
        Account turnsTomorrow = registeredBornOn(eighteenYearsAgo.plusDays(1));
        Account turnsToday = registeredBornOn(eighteenYearsAgo);
        Account turnedYesterday = registeredBornOn(eighteenYearsAgo.minusDays(1));

        assertThat(eligibilityOf(turnsTomorrow)).isEqualTo(answer(turnsTomorrow, false, "UNDERAGE"));
        assertThat(eligibilityOf(turnsToday)).isEqualTo(answer(turnsToday, true, null));
        assertThat(eligibilityOf(turnedYesterday)).isEqualTo(answer(turnedYesterday, true, null));
    }

    @Test
    void theNextDayTheMinorComesOfAge() {
        Account account = registeredBornOn(today().minusYears(18).plusDays(1));
        assertThat(eligibilityOf(account).get("reason").asString()).isEqualTo("UNDERAGE");

        clock.advance(Duration.ofDays(1));

        assertThat(eligibilityOf(account)).isEqualTo(answer(account, true, null));
    }

    @Test
    void theDateIsTheCivilDateInSaoPauloNotInUtc() {
        advanceToNextUtc(LocalTime.of(1, 30));
        LocalDate utcToday = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        assertThat(today()).isEqualTo(utcToday.minusDays(1));
        Account account = registeredBornOn(utcToday.minusYears(18));

        ResponseEntity<JsonNode> evening = sales().get(eligibilityPathOf(account.subject()));
        clock.advance(Duration.ofHours(2));
        ResponseEntity<JsonNode> midnight = sales().get(eligibilityPathOf(account.subject()));

        assertThat(evening.getBody()).isEqualTo(answer(account, false, "UNDERAGE"));
        assertThat(midnight.getBody()).isEqualTo(answer(account, true, null));
    }

    @Test
    void anAccountWithoutAProfileIsNotEligibleAndHasNoCustomerId() {
        ResponseEntity<JsonNode> response = sales().get(eligibilityPathOf(UUID.randomUUID().toString()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().propertyNames()).containsExactlyInAnyOrder("customerId", "eligible", "reason");
        assertThat(response.getBody().get("customerId").isNull()).isTrue();
        assertThat(response.getBody().get("eligible").asBoolean()).isFalse();
        assertThat(response.getBody().get("reason").asString()).isEqualTo("PROFILE_MISSING");
    }

    @Test
    void theAnswerHasNothingPersonalAndTheCheckIsNotAudited(CapturedOutput output) {
        Account account = newAccount();
        Map<String, Object> body = customerBody(Map.of("birthDate", today().minusYears(30).toString()));
        String cpf = (String) body.get("cpf");
        register(account, body);
        int auditBefore = auditRows();

        ResponseEntity<JsonNode> response = sales().get(eligibilityPathOf(account.subject()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().propertyNames()).containsExactlyInAnyOrder("customerId", "eligible", "reason");
        assertThat(response.getBody().toString()).doesNotContain(cpf, account.email(), "+5511999990000",
                "Maria Exemplo", "Praça da Sé");
        assertThat(auditRows()).isEqualTo(auditBefore);
        assertThat(output).doesNotContain(cpf, SyntheticData.formatCpf(cpf), account.email(), "+5511999990000");
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), SAO_PAULO);
    }

    private void advanceToNextUtc(LocalTime time) {
        Instant now = clock.instant();
        Instant next = LocalDate.ofInstant(now, ZoneOffset.UTC).atTime(time).toInstant(ZoneOffset.UTC);
        if (!next.isAfter(now)) {
            next = next.plus(Duration.ofDays(1));
        }
        clock.advance(Duration.between(now, next));
    }

    private Account registeredBornOn(LocalDate birthDate) {
        Account account = newAccount();
        register(account, customerBody(Map.of("birthDate", birthDate.toString())));
        return account;
    }

    private JsonNode eligibilityOf(Account account) {
        ResponseEntity<JsonNode> response = sales().get(eligibilityPathOf(account.subject()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private JsonNode answer(Account account, boolean eligible, String reason) {
        UUID id = jdbc.sql("SELECT id FROM customer WHERE identity_subject = :subject")
                .param("subject", account.subject()).query(UUID.class).single();
        Map<String, Object> expected = new HashMap<>();
        expected.put("customerId", id.toString());
        expected.put("eligible", eligible);
        expected.put("reason", reason);
        return JsonMapper.shared().valueToTree(expected);
    }

    private ApiDriver sales() {
        return api().asService(TestTokens.SALES_CLIENT, "customers.read-payer");
    }

    private static String eligibilityPathOf(String subject) {
        return "/internal/customers/by-subject/" + subject + "/eligibility";
    }
}
