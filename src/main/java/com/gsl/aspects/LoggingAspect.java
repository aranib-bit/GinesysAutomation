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

    /** Returns the current AOP-traced call depth on this thread (0 = top-level). */
    public static int currentDepth() {
        return CALL_STACK.get().size();
    }

    private String formatParamValue(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return "\"" + v + "\"";
        if (v instanceof java.util.Collection<?> c) {
            String preview = c.stream().limit(5)
                    .map(Object::toString)
                    .collect(java.util.stream.Collectors.joining(", "));
            return "[" + preview + (c.size() > 5 ? ", …(+" + (c.size() - 5) + ")" : "") + "]";
        }
        String s = String.valueOf(v);
        // Truncate very long toString() values (e.g. enum lists, large POJOs)
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    /**
     * Returns true for framework/driver types whose toString() is either enormous
     * or meaningless in a trace log — these are skipped in the → params: line.
     */
    private boolean isVerboseFrameworkType(Object v) {
        if (v == null) return false;
        Class<?> c = v.getClass();
        String pkg = c.getName();
        return pkg.startsWith("org.openqa.selenium")
                || pkg.startsWith("com.google.common")
                || pkg.startsWith("org.jdbi.v3.core.Jdbi")
                || pkg.startsWith("com.zaxxer.hikari")
                || pkg.startsWith("org.jdbi.v3.core.Handle")
                || pkg.startsWith("java.sql.")
                || pkg.startsWith("sun.")
                || pkg.startsWith("com.sun.")
                || v instanceof org.openqa.selenium.By      // By is logged separately via structured element block
                || v instanceof java.io.InputStream
                || v instanceof java.io.OutputStream;
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
     * Logs the return value of a method to trace.log, immediately before the [END] line.
     * Skips null (void methods), framework/driver objects, and empty strings.
     * Collections show size + up to first 3 items for readability.
     */
    private void logResult(int depth, Object result) {
        if (result == null) return;
        if (isVerboseFrameworkType(result)) return;
        try {
            String rendered;
            if (result instanceof java.util.Collection<?> c) {
                if (c.isEmpty()) {
                    rendered = "[] (empty)";
                } else {
                    String preview = c.stream().limit(3)
                            .map(item -> isVerboseFrameworkType(item) ? item.getClass().getSimpleName()
                                    : formatParamValue(item))
                            .collect(java.util.stream.Collectors.joining(", "));
                    rendered = "[" + c.size() + " item(s): " + preview
                            + (c.size() > 3 ? ", …(+" + (c.size() - 3) + " more)" : "") + "]";
                }
            } else {
                rendered = formatParamValue(result);
            }
            if (rendered != null && !rendered.isBlank()) {
                TRACE.info("{}  → result: {}", indent(depth), rendered);
            }
        } catch (Exception ignored) {
            // Never let result logging break method execution
        }
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
            "&& !within(com.gsl.database..*) " +
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

        // Log parameter name=value pairs for every method.
        // Skips Selenium/JDBI framework objects whose toString() is either enormous
        // or meaningless (WebDriver session strings, HikariConfig internals, etc.).
        try {
            org.aspectj.lang.reflect.MethodSignature sig =
                    (org.aspectj.lang.reflect.MethodSignature) pjp.getSignature();
            String[] paramNames  = sig.getParameterNames();
            Object[] paramValues = pjp.getArgs();
            if (paramNames != null && paramNames.length > 0) {
                StringBuilder params = new StringBuilder();
                for (int i = 0; i < paramNames.length; i++) {
                    Object v = paramValues[i];
                    if (isVerboseFrameworkType(v)) continue;
                    if (params.length() > 0) params.append(", ");
                    params.append(paramNames[i]).append("=");
                    params.append(formatParamValue(v));
                }
                if (params.length() > 0) {
                    TRACE.info("{}  → params: {}", indent(depth), params);
                }
            }
        } catch (Exception ignored) {
            // Never let param logging break the actual method execution
        }

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
            logResult(depth, result);
            logEnd(depth, className, methodName);
            return result;
        } catch (Throwable e) {
            String ts = now();
            TRACE.error("{}[{}] [TRACE_ID={}] [ERROR]  {}.{}() — {}",
                    indent(depth), ts, traceId(), className, methodName, e.getMessage());
            Log.error("[AOP] Exception in {}.{}: {}", className, methodName, e.getMessage());
            // Don't paint the Extent node red for a skip:
            //  • A TestNG SkipException is itself a skip signal, not a failure.
            //  • While DataLoadScope is active, any exception (typically an IllegalStateException
            //    raised deep in the DB resolution chain) is a data-availability shortfall that the
            //    data source converts to a SkipException at its boundary. Suppressing the fail here
            //    stops one red FAIL (+ screenshot) being logged per advised frame the raw exception
            //    unwinds through before that conversion happens — which is what made amber skips
            //    render as misleading red fails (ExtentReports shows a node at its highest-severity
            //    log: FAIL > WARNING/SKIP).
            //
            //  • Lambda frames throwing NoSuchElementException or StaleElementReferenceException
            //    are fluent-wait probe attempts — the wait catches and retries them. Log these as
            //    WARNING with a [RETRY] prefix so the node status is not poisoned by recoverable
            //    retry-throws on tests that ultimately pass.
            if (ExtentManager.hasTest()
                    && !(e instanceof org.testng.SkipException)
                    && !DataLoadScope.isActive()) {
                boolean isFluentWaitRetry = methodName.contains("lambda$")
                        && (e instanceof org.openqa.selenium.NoSuchElementException
                            || e instanceof org.openqa.selenium.StaleElementReferenceException);
                if (isFluentWaitRetry) {
                    String shortMsg = e.getMessage() != null
                            ? e.getMessage().split("\n")[0] : "no message";
                    ExtentLogger.warning("[RETRY] " + className + "." + methodName + ": " + shortMsg);
                } else {
                    ExtentLogger.fail("[AOP] " + className + "." + methodName + ": " + e.getMessage());
                }
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
     * Intercepts calls TO JDBI DAO interface methods from within com.gsl/com.tests code.
     * Because JDBI generates runtime proxies, execution() never fires for DAO methods —
     * this call() advice matches at the call site (which IS inside woven code) instead.
     *
     * Logs [START]/[END] + "→ params: name=value" lines to trace.log, matching the
     * format used by logMethod() so DAO calls appear inline in the method-trace hierarchy.
     */
    @Around("call(* com.gsl.database.dao..*(..)) && within(com.gsl..*)")
    public Object logDaoCall(ProceedingJoinPoint pjp) throws Throwable {
        Deque<String> stack = CALL_STACK.get();

        String className  = pjp.getSignature().getDeclaringType().getSimpleName();
        String methodName = pjp.getSignature().getName();
        String fullMethod = className + "." + methodName;

        stack.push(fullMethod);
        int depth = stack.size() - 1;

        logStart(depth, className, methodName);

        // Log all parameters by name=value — DAO methods use @Bind("name") params
        // which AspectJ resolves from the compiled call site's debug info.
        try {
            org.aspectj.lang.reflect.MethodSignature sig =
                    (org.aspectj.lang.reflect.MethodSignature) pjp.getSignature();
            String[] paramNames  = sig.getParameterNames();
            Object[] paramValues = pjp.getArgs();
            if (paramNames != null && paramNames.length > 0) {
                StringBuilder params = new StringBuilder();
                for (int i = 0; i < paramNames.length; i++) {
                    if (paramValues[i] instanceof org.jdbi.v3.core.Jdbi) continue;
                    if (params.length() > 0) params.append(", ");
                    params.append(paramNames[i]).append("=");
                    params.append(formatParamValue(paramValues[i]));
                }
                if (params.length() > 0) {
                    TRACE.info("{}  → params: {}", indent(depth), params);
                }
            }
        } catch (Exception ignored) {}

        try {
            Object result = pjp.proceed();
            logResult(depth, result);
            logEnd(depth, className, methodName);
            return result;
        } catch (Throwable e) {
            String ts = now();
            TRACE.error("{}[{}] [TRACE_ID={}] [ERROR]  {}.{}() — {}",
                    indent(depth), ts, traceId(), className, methodName, e.getMessage());
            Log.error("[AOP-DAO] Exception in {}.{}: {}", className, methodName, e.getMessage());
            throw e;
        } finally {
            stack.pop();
            if (stack.isEmpty()) CALL_STACK.remove();
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
        try {
            return pjp.proceed();
        } catch (Throwable e) {
            // SkipException = skipped step (e.g. test-data unavailable), not a failure.
            // Likewise, anything thrown inside an active DataLoadScope is a skip-bound
            // data-availability shortfall \u2014 don't log it as a red FAIL.
            if (ExtentManager.hasTest()
                    && !(e instanceof org.testng.SkipException)
                    && !DataLoadScope.isActive()) {
                ExtentLogger.fail("Failed: " + logStep.value() + " \u2014 " + e.getMessage());
            }
            throw e;
        }
    }
}
