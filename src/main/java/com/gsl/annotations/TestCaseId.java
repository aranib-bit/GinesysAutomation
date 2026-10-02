package com.gsl.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a test method's stable, human-readable scenario ID. Read via reflection by
 * {@code TestListener} (to prefix the ExtentReports test title/skip label, e.g.
 * {@code "[FIN-GV-ADD-COMBO-01] ..."}) and by data-provider classes such as
 * {@code MergedFinGeneralVoucherDataSupplier} (to stamp each generated data row with
 * {@code "<value>-<invocationIndex>"} so every row of a data-driven test gets its own numbered ID).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TestCaseId {
    String value(); // base ID, e.g. "FIN-AR-ADD"
}
