package org.sgj.rljobscheduler.master.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.sgj.rljobscheduler.master.annotation.Loggable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AOP 日志切面，统一处理方法级日志记录
 */
@Aspect
@Component
public class LoggingAspect {

    private static final String PLACEHOLDER = "_";

    @Pointcut("@annotation(org.sgj.rljobscheduler.master.annotation.Loggable)")
    public void loggableMethods() {}

    @Around("loggableMethods()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Loggable loggable = method.getAnnotation(Loggable.class);

        Logger log = getLogger(method);
        Loggable.LogLevel level = loggable.level();

        String methodName = buildMethodName(signature);
        long startNs = System.nanoTime();

        // 入口日志
        if (loggable.logParams()) {
            String paramsSummary = buildParamsSummary(joinPoint, signature);
            logAtLevel(log, level, ">>> [ENTRANCE] {} start. params: {}", methodName, paramsSummary);
        } else {
            logAtLevel(log, level, ">>> [ENTRANCE] {} start.", methodName);
        }

        Object result = null;
        Throwable thrown = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            thrown = t;
            throw t;
        } finally {
            long elapsedNs = System.nanoTime() - startNs;
            long elapsedMs = elapsedNs / 1_000_000;

            if (thrown != null) {
                // 异常日志
                logAtLevel(log, Loggable.LogLevel.ERROR, ">>> [EXCEPTION] {} threw {}: {} in {}ms",
                        methodName,
                        thrown.getClass().getSimpleName(),
                        thrown.getMessage(),
                        elapsedMs,
                        thrown);
            } else {
                // 正常返回日志
                StringBuilder exitMsg = new StringBuilder();
                exitMsg.append(">>> [EXIT] ").append(methodName).append(" completed");
                if (loggable.logExecutionTime()) {
                    exitMsg.append(" in ").append(elapsedMs).append("ms");
                }
                if (loggable.logReturn() && result != null) {
                    exitMsg.append(". result: ").append(safeTruncate(summarizeReturn(result)));
                }
                logAtLevel(log, level, exitMsg.toString());
            }
        }
    }

    private Logger getLogger(Method method) {
        return LoggerFactory.getLogger(method.getDeclaringClass());
    }

    private String buildMethodName(MethodSignature signature) {
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName() + "()";
    }

    private String buildParamsSummary(ProceedingJoinPoint joinPoint, MethodSignature signature) {
        Parameter[] params = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        if (params == null || args == null || params.length == 0) {
            return "{}";
        }

        Map<String, String> summary = new HashMap<>();
        for (int i = 0; i < params.length; i++) {
            String name = params[i].getName();
            Object value = args[i];
            summary.put(name, summarizeValue(value));
        }

        return summary.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private String summarizeValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) {
            String s = (String) value;
            return s.length() > 50 ? "\"" + s.substring(0, 50) + "...\"" : "\"" + s + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        // 复杂对象：类名 + hashCode，避免打印整个对象
        String className = value.getClass().getSimpleName();
        String hash = Integer.toHexString(value.hashCode());
        return className + "@" + hash;
    }

    private String summarizeReturn(Object result) {
        if (result == null) return "null";
        if (result instanceof String) {
            String s = (String) result;
            return s.length() > 100 ? s.substring(0, 100) + "..." : s;
        }
        if (result instanceof Number || result instanceof Boolean) {
            return String.valueOf(result);
        }
        if (result instanceof org.springframework.data.domain.Page) {
            return "Page";
        }
        if (result instanceof Iterable) {
            Iterable<?> iterable = (Iterable<?>) result;
            int count = 0;
            for (Object ignored : iterable) count++;
            return "Iterable[size=" + count + "]";
        }
        if (result.getClass().isArray()) {
            return result.getClass().getComponentType().getSimpleName() + "[]";
        }
        return result.getClass().getSimpleName() + "@" + Integer.toHexString(result.hashCode());
    }

    private String safeTruncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    private void logAtLevel(Logger log, Loggable.LogLevel level, String format, Object... args) {
        switch (level) {
            case DEBUG -> log.debug(format, args);
            case WARN -> log.warn(format, args);
            case ERROR -> log.error(format, args);
            default -> log.info(format, args);
        }
    }
}
