package com.revenda.common.privacy;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.event.KeyValuePair;

/**
 * Envolve as saídas de verdade (console, arquivo): cada evento chega a elas com a mensagem já montada, o
 * MDC, os pares chave-valor e a mensagem de cada exceção da cadeia passados pelo {@link PiiMasker}.
 */
public class MaskingAppender extends AppenderBase<ILoggingEvent> implements AppenderAttachable<ILoggingEvent> {

    public static final String NAME = "MASKED";

    private final AppenderAttachableImpl<ILoggingEvent> outputs = new AppenderAttachableImpl<>();

    @Override
    protected void append(ILoggingEvent event) {
        ILoggingEvent masked = masked(event, PiiMasker.mask(event.getFormattedMessage()), maskedMdc(event),
                masked(event.getThrowableProxy()), maskedKeyValuePairs(event));
        outputs.appendLoopOnAppenders(masked);
    }

    private static ILoggingEvent masked(ILoggingEvent event, String message, Map<String, String> mdc,
            IThrowableProxy throwable, List<KeyValuePair> pairs) {
        return (ILoggingEvent) Proxy.newProxyInstance(ILoggingEvent.class.getClassLoader(),
                new Class<?>[] {ILoggingEvent.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getFormattedMessage", "getMessage" -> message;
                    case "getArgumentArray" -> null;
                    case "getThrowableProxy" -> throwable;
                    case "getKeyValuePairs" -> pairs;
                    case "getMDCPropertyMap", "getMdc" -> mdc;
                    default -> invoke(method, event, arguments);
                });
    }

    private static IThrowableProxy masked(IThrowableProxy throwable) {
        if (throwable == null) {
            return null;
        }
        return (IThrowableProxy) Proxy.newProxyInstance(IThrowableProxy.class.getClassLoader(),
                new Class<?>[] {IThrowableProxy.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMessage" -> PiiMasker.mask(throwable.getMessage());
                    case "getCause" -> masked(throwable.getCause());
                    case "getSuppressed" -> throwable.getSuppressed() == null
                            ? null
                            : Arrays.stream(throwable.getSuppressed())
                                    .map(MaskingAppender::masked)
                                    .toArray(IThrowableProxy[]::new);
                    default -> invoke(method, throwable, arguments);
                });
    }

    private static Object invoke(Method method, Object target, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private static Map<String, String> maskedMdc(ILoggingEvent event) {
        Map<String, String> mdc = new LinkedHashMap<>();
        event.getMDCPropertyMap().forEach((key, value) -> mdc.put(key, PiiMasker.mask(value)));
        return mdc;
    }

    private static List<KeyValuePair> maskedKeyValuePairs(ILoggingEvent event) {
        if (event.getKeyValuePairs() == null) {
            return null;
        }
        return event.getKeyValuePairs().stream()
                .map(pair -> new KeyValuePair(pair.key,
                        pair.value == null ? null : PiiMasker.mask(String.valueOf(pair.value))))
                .toList();
    }

    @Override
    public void addAppender(Appender<ILoggingEvent> appender) {
        outputs.addAppender(appender);
    }

    @Override
    public Iterator<Appender<ILoggingEvent>> iteratorForAppenders() {
        return outputs.iteratorForAppenders();
    }

    @Override
    public Appender<ILoggingEvent> getAppender(String name) {
        return outputs.getAppender(name);
    }

    @Override
    public boolean isAttached(Appender<ILoggingEvent> appender) {
        return outputs.isAttached(appender);
    }

    @Override
    public void detachAndStopAllAppenders() {
        outputs.detachAndStopAllAppenders();
    }

    @Override
    public boolean detachAppender(Appender<ILoggingEvent> appender) {
        return outputs.detachAppender(appender);
    }

    @Override
    public boolean detachAppender(String name) {
        return outputs.detachAppender(name);
    }
}
