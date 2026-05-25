package com.gsl.annotations;

import com.gsl.network.pattern.FinanceApiPattern;
import java.lang.annotation.*;

/**
 * Declares which Finance API flow a UI test class exercises.
 * The value is a FinanceApiPattern enum constant — URL patterns live in the enum,
 * so a URL change requires editing only the enum, not every annotated test class.
 *
 * Used by FailureBundleOrchestrator to filter the CDP Network Traffic section on
 * passing tests (unless network.capture.pass.show.all=true in config).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ApiFlow {
    FinanceApiPattern value();
}
