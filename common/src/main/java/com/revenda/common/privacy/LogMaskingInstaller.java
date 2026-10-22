package com.revenda.common.privacy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.ResolvableType;

public class LogMaskingInstaller implements GenericApplicationListener {

    public static final int ORDER = LoggingApplicationListener.DEFAULT_ORDER + 1;

    @Override
    public boolean supportsEventType(ResolvableType eventType) {
        return ApplicationEnvironmentPreparedEvent.class.isAssignableFrom(eventType.toClass());
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            install(context);
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    public static void install(LoggerContext context) {
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        if (root.getAppender(MaskingAppender.NAME) != null) {
            return;
        }
        List<Appender<ILoggingEvent>> outputs = new ArrayList<>();
        root.iteratorForAppenders().forEachRemaining(outputs::add);
        if (outputs.isEmpty()) {
            return;
        }
        MaskingAppender masking = new MaskingAppender();
        masking.setName(MaskingAppender.NAME);
        masking.setContext(context);
        outputs.forEach(output -> {
            root.detachAppender(output);
            masking.addAppender(output);
        });
        masking.start();
        root.addAppender(masking);
    }
}
