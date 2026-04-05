package org.sgj.rljobscheduler.master.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级日志注解，使用 AOP 自动记录方法入口/出口、执行时间和异常
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Loggable {

    /**
     * 日志级别
     */
    LogLevel level() default LogLevel.INFO;

    /**
     * 是否打印入参
     */
    boolean logParams() default true;

    /**
     * 是否打印返回值
     */
    boolean logReturn() default false;

    /**
     * 是否打印执行时间
     */
    boolean logExecutionTime() default true;

    enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
}
