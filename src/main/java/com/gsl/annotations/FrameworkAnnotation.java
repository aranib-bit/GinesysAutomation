package com.gsl.annotations;

import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a test method's authorship and place in the module/sub-module/operation taxonomy.
 * Read at runtime by two independent consumers:
 * <ul>
 *   <li>{@code AnnotationMethodInterceptor} — filters which methods run for a given suite based on
 *       the {@code -Dfw.module}/{@code -Dfw.subModule}/{@code -Dfw.group}/{@code -Dfw.operation}
 *       system properties supplied for a CI/CD pipeline stage; an unannotated method always runs.</li>
 *   <li>{@code TestListener} — reads {@link #value()} to assign the ExtentReports author category.</li>
 * </ul>
 * {@code fw.group} filtering resolves {@link #module()}/{@link #subModule()} to a group via
 * {@code SubModuleResolver}, so {@link #subModule()} must be a value that resolver recognizes for
 * the declared {@link #module()}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface FrameworkAnnotation {
    /** Test author, used for the ExtentReports author category. */
    TestAuthor     value()     default TestAuthor.ARANI;
    /** Top-level application module this test exercises. */
    MainModuleType module()    default MainModuleType.FINANCE;
    /** Sub-module name within {@link #module()} — must be resolvable by {@code SubModuleResolver} for {@code fw.group} filtering to work. */
    String         subModule() default "FinanceARVoucher";
    /** CRUD-style operation this test exercises (e.g. ADD, EDIT, DELETE); {@code NONE} when not applicable. */
    AppOperation   operation() default AppOperation.NONE;
}
