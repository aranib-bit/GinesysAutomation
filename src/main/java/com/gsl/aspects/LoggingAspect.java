package com.gsl.aspects;

import com.gsl.annotations.LogStep;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ExtentManager;
import com.gsl.utils.ElementMeta;
import com.gsl.utils.Log;
import com.gsl.utils.SensitiveDataMasker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.openqa.selenium.By;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;

@Aspect
public class LoggingAspect {

    private static final Logger TRACE = LogManager.getLogger("MethodTrace");

    private static final ThreadLocal<Deque<String>> CALL_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    // DateTimeFormatter is immutable and thread-safe
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // Target line width for dot-padding; keeps end timestamps visually aligned
    private static final int LINE_WIDTH = 130;

    private String now() {
        return LocalDateTime.now().format(DATE_FMT);
    }

    private String indent(int depth) {
        return "  ".repeat(depth);
    }

    private String traceId() {
        String id = ThreadContext.get("testId");
        if (id == null) id = ThreadContext.get("testName");
        if (id == null) id = "T-" + Thread.currentThread().getId();
        return id;
    }

    private void logStart(int depth, String className, String methodName) {
        String indent = indent(depth);
        String ts     = now();
        String label  = indent + "[" + ts + "] [TRACE_ID=" + traceId() + "] [START] "
                        + className + "." + methodName + "()";
        int dots = Math.max(4, LINE_WIDTH - label.length() - 29);
        TRACE.info("{} {} [{}]", label, ".".repeat(dots), ts);
    }

    private void logEnd(int depth, String className, String methodName) {
        String indent = indent(depth);
        String ts     = now();
        String label  = indent + "[" + ts + "] [TRACE_ID=" + traceId() + "] [END]   "
                        + className + "." + methodName + "()";
        int dots = Math.max(4, LINE_WIDTH - label.length() - 29);
        TRACE.info("{} {} [{}]", label, ".".repeat(dots), ts);
    }

    /**
     * Intercepts ALL methods (any visibility) in com.gsl and com.tests packages.
     * Logs indented [START]/[END] lines with timestamps to trace.log via MethodTrace logger.
     * When args contain By + ElementMeta, logs structured element name and locator.
     * When args contain a String value on sendKeys methods, masks sensitive values.
     * On exception: logs [ERROR] line + Log4j2 ERROR + Extent fail (when test is active).
     *
     * Excluded:
     *   com.gsl.reports..*  — prevents infinite loops on ExtentLogger/ReportLogger
     *   com.gsl.aspects..*  — prevents self-interception
     */
    @Around("(execution(* com.gsl..*(..)) || execution(* com.tests..*(..))) " +
            "&& !within(com.gsl.reports..*) " +
            "&& !within(com.gsl.aspects..*) " +
            "&& !within(com.gsl.testdata..*) " +
            "&& !within(com.gsl.utils.Selenium.ScreenshotUtils)")
    public Object logMethod(ProceedingJoinPoint pjp) throws Throwable {
        Deque<String> stack = CALL_STACK.get();

        Object target     = pjp.getTarget();
        String className  = (target != null)
                ? target.getClass().getSimpleName()
                : pjp.getSignature().getDeclaringType().getSimpleName();
        String methodName = pjp.getSignature().getName();
        String fullMethod = className + "." + methodName;

        stack.push(fullMethod);
        int depth = stack.size() - 1;

        logStart(depth, className, methodName);

        // Extract typed args for structured element logging
        By          locator = null;
        String      value   = null;
        ElementMeta meta    = null;

        Object[] args = pjp.getArgs();
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof By)          locator = (By) arg;
                else if (arg instanceof ElementMeta) meta = (ElementMeta) arg;
                else if (arg instanceof String)  value   = (String) arg;
            }
        }

        if (meta != null && locator != null) {
            TRACE.info("{}  → {} | {}", indent(depth), meta.getName(), locator);
        } else if (locator != null) {
            TRACE.info("{}  → [xpath] {}", indent(depth), locator);
        }

        if ("sendKeys".equalsIgnoreCase(methodName) && value != null) {
            boolean sensitive = SensitiveDataMasker.isSensitive(meta, locator);
            String  display   = sensitive ? SensitiveDataMasker.mask(value) : value;
            TRACE.info("{}  → Input Value: {}", indent(depth), display);
        }

        try {
            Object result = pjp.proceed();
            logEnd(depth, className, methodName);
            return result;
        } catch (Throwable e) {
            String ts = now();
            TRACE.error("{}[{}] [TRACE_ID={}] [ERROR]  {}.{}() — {}",
                    indent(depth), ts, traceId(), className, methodName, e.getMessage());
            Log.error("[AOP] Exception in {}.{}: {}", className, methodName, e.getMessage());
            if (ExtentManager.hasTest()) {
                ExtentLogger.fail("[AOP] " + className + "." + methodName + ": " + e.getMessage());
            }
            throw e;
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                CALL_STACK.remove();
            }
        }
    }

    /**
     * Intercepts methods annotated with @LogStep.
     * Logs the step description to Extent info + Log4j2 INFO.
     * On exception: logs fail message to Extent (if a test is active).
     */
    @Around("@annotation(logStep)")
    public Object logAnnotatedStep(ProceedingJoinPoint pjp, LogStep logStep) throws Throwable {
        Log.info("[STEP] {}", logStep.value());
        if (ExtentManager.hasTest()) {
            ExtentLogger.info(logStep.value());
        }
        try {
            return pjp.proceed();
        } catch (Throwable e) {
            if (ExtentManager.hasTest()) {
                ExtentLogger.fail("Failed: " + logStep.value() + " \u2014 " + e.getMessage());
            }
            throw e;
        }
    }
}
