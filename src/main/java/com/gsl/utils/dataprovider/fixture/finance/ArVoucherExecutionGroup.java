package com.gsl.utils.dataprovider.fixture.finance;

/**
 * Classifies each AR Voucher test variant into a named execution bucket.
 *
 * <p>Pass {@code -Darv.group=MANDATORY} (or REGRESSION_PHASE1/2/3) to Maven Surefire
 * to restrict the DataSupplier to that group only. Omit the property to run all groups.
 *
 * <p>Dedicated TestNG suites and Maven profiles are provided for each group:
 * <ul>
 *   <li>MANDATORY        → testng-mandatory.xml         / {@code -Pmandatory}</li>
 *   <li>REGRESSION_PHASE1 → testng-regression-phase1.xml / {@code -Pregression-phase1}</li>
 *   <li>REGRESSION_PHASE2 → testng-regression-phase2.xml / {@code -Pregression-phase2}</li>
 *   <li>REGRESSION_PHASE3 → testng-regression-phase3.xml / {@code -Pregression-phase3}</li>
 *   <li>(all groups)     → testng-regression.xml        / {@code -Pregression}</li>
 * </ul>
 */
public enum ArVoucherExecutionGroup {

    /** 6 core smoke tests — fast CI build verification. */
    MANDATORY,

    /** Payment validation, basic SL/doc count, amount, grid, date and new scenario variants. */
    REGRESSION_PHASE1,

    /** Full party-type matrix, customer SL configs, grid edge cases, payment MIX, backdate. */
    REGRESSION_PHASE2,

    /** Specific document-type filtering (INV-only). DB query extension required. */
    REGRESSION_PHASE3
}
