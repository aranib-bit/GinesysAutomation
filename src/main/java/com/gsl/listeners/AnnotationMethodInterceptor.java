package com.gsl.listeners;

import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.enums.ISubModule;
import com.gsl.enums.SubModuleResolver;
import com.gsl.utils.Log;
import org.testng.IMethodInstance;
import org.testng.IMethodInterceptor;
import org.testng.ITestContext;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Filters test methods at runtime via system properties for CI/CD pipeline stage selection.
 *
 * Usage (any combination):
 *   -Dfw.module=FINANCE
 *   -Dfw.group=Vouchers
 *   -Dfw.subModule=FinanceARVoucher
 *   -Dfw.operation=ADD
 *
 * All four properties are optional and compose freely.
 * Unannotated test methods are never filtered out.
 */
public class AnnotationMethodInterceptor implements IMethodInterceptor {

    /**
     * Filters {@code methods} down to those matching every {@code fw.*} system property that was
     * supplied for this run. A method without a {@link FrameworkAnnotation} always runs; when
     * {@code fw.group} is set, the group is resolved via {@link SubModuleResolver} and a method
     * whose sub-module can't be resolved is excluded (logged as a warning) rather than crashing
     * the filter.
     *
     * @return {@code methods} unchanged when no {@code fw.*} property is set, otherwise the filtered subset
     */
    @Override
    public List<IMethodInstance> intercept(List<IMethodInstance> methods, ITestContext context) {
        String propModule    = System.getProperty("fw.module");
        String propSubModule = System.getProperty("fw.subModule");
        String propGroup     = System.getProperty("fw.group");
        String propOperation = System.getProperty("fw.operation");

        if (propModule == null && propSubModule == null
                && propGroup == null && propOperation == null) {
            return methods;   // no filter — run everything
        }

        Log.info("[AnnotationMethodInterceptor] Filtering: module={} subModule={} group={} operation={}",
                propModule, propSubModule, propGroup, propOperation);

        List<IMethodInstance> filtered = methods.stream().filter(m -> {
            FrameworkAnnotation ann = m.getMethod()
                    .getConstructorOrMethod().getMethod()
                    .getAnnotation(FrameworkAnnotation.class);

            if (ann == null) return true;   // unannotated tests always run

            if (propModule    != null && !ann.module().name().equals(propModule))    return false;
            if (propOperation != null && !ann.operation().name().equals(propOperation)) return false;
            if (propSubModule != null && !ann.subModule().equals(propSubModule))     return false;
            if (propGroup     != null) {
                try {
                    ISubModule sm = SubModuleResolver.resolve(ann.module(), ann.subModule());
                    if (!sm.getGroup().equals(propGroup)) return false;
                } catch (IllegalArgumentException e) {
                    Log.warn("[AnnotationMethodInterceptor] Could not resolve subModule '{}' — excluding method '{}'",
                            ann.subModule(), m.getMethod().getMethodName());
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());

        Log.info("[AnnotationMethodInterceptor] {} of {} methods selected after filtering",
                filtered.size(), methods.size());
        return filtered;
    }
}
