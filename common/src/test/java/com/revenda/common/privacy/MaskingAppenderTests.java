package com.revenda.common.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.read.ListAppender;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

class MaskingAppenderTests {

    private static final String[] RAW = {"123.456.789-09", "bruno.sem-cadastro@example.test", "ana.cliente@example.test",
            "98888-7777", "988887777", "955555555", "01310-100"};

    private final LoggerContext context = new LoggerContext();
    private final ListAppender<ILoggingEvent> console = new ListAppender<>();
    private final MaskingAppender masking = new MaskingAppender();
    private Logger logger;

    @BeforeEach
    void wire() {
        context.setMDCAdapter(new LogbackMDCAdapter());
        console.setContext(context);
        console.start();
        masking.setContext(context);
        masking.addAppender(console);
        masking.start();
        logger = context.getLogger("purchases");
    }

    @Test
    void masksMessageArgumentsExceptionChainMdcAndPairs() {
        masking.doAppend(failedChargeEvent());

        ILoggingEvent out = console.list.getFirst();
        assertThat(out.getFormattedMessage()).isEqualTo("falha ao cobrar [telefone •••• 7777] no CEP [cep]");
        assertThat(out.getArgumentArray()).isNull();
        assertThat(out.getLevel()).isEqualTo(Level.ERROR);
        assertThat(out.getLoggerName()).isEqualTo("purchases");
        assertThat(out.getMDCPropertyMap())
                .containsEntry("requestId", "gw-2f6c1a9e-0001")
                .containsEntry("contato", "[e-mail]");
        assertThat(out.getKeyValuePairs()).singleElement().satisfies(pair -> {
            assertThat(pair.key).isEqualTo("pagador");
            assertThat(pair.value).isEqualTo("[telefone •••• 5555]");
        });
        assertThat(out.getThrowableProxy().getClassName()).isEqualTo(RuntimeException.class.getName());
        assertThat(out.getThrowableProxy().getMessage()).isEqualTo("comprador [cpf] recusado");
        assertThat(out.getThrowableProxy().getCause().getMessage()).isEqualTo("Chave (email)=([e-mail]) já existe");
        assertThat(out.getThrowableProxy().getSuppressed()[0].getMessage())
                .isEqualTo("telefone [telefone •••• 7777] inválido");
        assertThat(ThrowableProxyUtil.asString(out.getThrowableProxy()))
                .contains("Caused by: java.lang.IllegalStateException: Chave (email)=([e-mail]) já existe")
                .contains("at com.revenda.common.privacy.MaskingAppenderTests")
                .doesNotContain(RAW);
    }

    @Test
    void eventWithoutExceptionNorPairsStaysWithout() {
        masking.doAppend(new LoggingEvent("purchases", logger, Level.INFO, "compra {} reservada",
                null, new Object[] {"6f1d2c3b-0000-4000-8000-446655440000"}));

        ILoggingEvent out = console.list.getFirst();
        assertThat(out.getFormattedMessage()).isEqualTo("compra 6f1d2c3b-0000-4000-8000-446655440000 reservada");
        assertThat(out.getThrowableProxy()).isNull();
        assertThat(out.getKeyValuePairs()).isNull();
        assertThat(out.getMDCPropertyMap()).isEmpty();
    }

    @Test
    void structuredJsonOutputIsMaskedToo() {
        context.putObject(Environment.class.getName(), new StandardEnvironment());
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat("ecs");
        encoder.start();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        OutputStreamAppender<ILoggingEvent> json = new OutputStreamAppender<>();
        json.setContext(context);
        json.setEncoder(encoder);
        json.setOutputStream(bytes);
        json.start();
        masking.addAppender(json);

        masking.doAppend(failedChargeEvent());

        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .startsWith("{")
                .contains("\"message\":\"falha ao cobrar [telefone •••• 7777] no CEP [cep]\"")
                .contains("\"requestId\":\"gw-2f6c1a9e-0001\"")
                .contains("comprador [cpf] recusado", "[e-mail]")
                .doesNotContain(RAW);
    }

    private LoggingEvent failedChargeEvent() {
        IllegalStateException cause =
                new IllegalStateException("Chave (email)=(bruno.sem-cadastro@example.test) já existe");
        RuntimeException failure = new RuntimeException("comprador 123.456.789-09 recusado", cause);
        failure.addSuppressed(new IllegalArgumentException("telefone (11) 98888-7777 inválido"));
        LoggingEvent event = new LoggingEvent("purchases", logger, Level.ERROR, "falha ao cobrar {} no CEP {}",
                failure, new Object[] {"+5511988887777", "01310-100"});
        event.setMDCPropertyMap(Map.of("requestId", "gw-2f6c1a9e-0001", "contato", "ana.cliente@example.test"));
        event.addKeyValuePair(new KeyValuePair("pagador", "+5511955555555"));
        return event;
    }
}
