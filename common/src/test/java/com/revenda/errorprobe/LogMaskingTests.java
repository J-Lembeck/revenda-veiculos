package com.revenda.errorprobe;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.revenda.common.privacy.MaskingAppender;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@Slf4j
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(classes = ErrorProbeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class LogMaskingTests {

    private static final String[] RAW = {"123.456.789-09", "ana.cliente@example.test", "98888-7777", "988887777",
            "955555555", "01310-100", "eyJhbGciOiJSUzI1NiJ9"};

    @Autowired
    private TestRestTemplate rest;

    @Test
    void everyRootOutputGoesThroughTheMasker() {
        Logger root = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME);
        List<Appender<ILoggingEvent>> outputs = new ArrayList<>();
        root.iteratorForAppenders().forEachRemaining(outputs::add);

        assertThat(outputs).singleElement().isInstanceOf(MaskingAppender.class);
        assertThat(((MaskingAppender) outputs.getFirst()).iteratorForAppenders()).hasNext();
    }

    @Test
    void unexpectedErrorReachesTheLogMasked(CapturedOutput output) {
        rest.getForEntity("/probe/failure", String.class);

        assertThat(output.getAll())
                .contains("Erro não tratado em GET /probe/failure")
                .contains("java.lang.IllegalStateException: detalhe interno [cpf]")
                .doesNotContain(RAW);
    }

    @Test
    void applicationLogsAreMasked(CapturedOutput output) {
        MDC.put("contato", "ana.cliente@example.test");
        try {
            log.info("marcador-de-teste comprador {} fone {} cep {}", "123.456.789-09", "(11) 98888-7777", "01310-100");
            log.atInfo().addKeyValue("pagador", "+5511955555555").log("marcador-de-teste par chave-valor");
            log.error("marcador-de-teste falha na cobrança", new RuntimeException(
                    "token eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.YXNzaW5hdHVyYQ recusado",
                    new IllegalStateException("Chave (email)=(ana.cliente@example.test) já existe")));
        } finally {
            MDC.remove("contato");
        }

        assertThat(output.getAll())
                .contains("marcador-de-teste comprador [cpf] fone [telefone •••• 7777] cep [cep]")
                .contains("java.lang.RuntimeException: token [token] recusado")
                .contains("Caused by: java.lang.IllegalStateException: Chave (email)=([e-mail]) já existe")
                .doesNotContain(RAW);
    }
}
