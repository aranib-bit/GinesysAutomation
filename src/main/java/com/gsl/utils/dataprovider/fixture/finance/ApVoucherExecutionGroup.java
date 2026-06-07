package com.gsl.utils.dataprovider.fixture.finance;

/**
 * Classifies each AP Voucher test variant into a named execution bucket.
 *
 * <p>Pass {@code -Dapv.group=MANDATORY} (or REGRESSION_PHASE1/2/3) to Maven Surefire
 * to restrict the DataSupplier to that group only. Omit the property to run all groups.
 *
 * <p>Dedicated TestNG suites and Maven profiles are provided for each group:
 * <ul>
 *   <li>MANDATORY          → testng-apv-all.xml        / {@code -Papv-all -Dapv.group=MANDATORY}</li>
 *   <li>REGRESSION_PHASE1  → testng-apv-all.xml        / {@code -Papv-all -Dapv.group=REGRESSION_PHASE1}</li>
 *   <li>REGRESSION_PHASE2  → testng-apv-all.xml        / {@code -Papv-all -Dapv.group=REGRESSION_PHASE2}</li>
 *   <li>REGRESSION_PHASE3  → testng-apv-all.xml        / {@code -Papv-all -Dapv.group=REGRESSION_PHASE3}</li>
 *   <li>(all groups)       → testng-apv-all.xml        / {@code -Papv-all}</li>
 * </ul>
 *
 * <p>Combine with {@code apv.tag} for path-level filtering:
 * <pre>{@code
 *   mvn clean test -Papv-advance -Dapv.group=MANDATORY
 *   mvn clean test -Papv-advance-tds -Dapv.group=REGRESSION_PHASE2
 * }</pre>
 *
 * <h3>Bucket assignments</h3>
 * <ul>
 *   <li><b>MANDATORY</b> (7 tests):
 *       Advance/Bank GL/No-TDS baseline,
 *       Advance/Cash GL/No-TDS baseline,
 *       <b>Advance/Bank GL/With-TDS/GROSS_EQUALS_TDS (smoke baseline — same value accepted as-is)</b>,
 *       Doc-FIFO/Bank GL/System SL,
 *       Doc-FIFO/Cash GL,
 *       Doc-Ref-Sitewise-FIFO/Bank GL/System SL,
 *       Doc-Select-Document/Bank GL/System SL baseline</li>
 *   <li><b>REGRESSION_PHASE1</b> (~23 tests):
 *       <b>Advance/Bank GL/With-TDS/PARTIAL_EDIT (TDS Applicable Amount overridden to partial value)</b>,
 *       Advance/Cash GL/With-TDS,
 *       Doc-No-Adj/Bank GL/System SL,
 *       Doc-No-Adj/Bank GL/User SL,
 *       Doc-FIFO/Bank GL/User SL,
 *       Doc-Ref-Sitewise-FIFO/Bank GL/User SL,
 *       Doc-Select-Document/Bank GL/User SL,
 *       Doc-Select-Document/Cash GL variants,
 *       Doc-Select-Document payment-mode variants (NONE, CHEQUE, NEFT_RTGS, MIX)</li>
 *   <li><b>REGRESSION_PHASE2</b> (~30+ tests):
 *       <b>Advance/Bank GL/With-TDS/CLEAR_ERROR_THEN_PARTIAL_EDIT
 *          (clear → validation error → dismiss → retype → partial edit)</b>,
 *       Multi-SL, multi-doc, amount modes (PARTIAL, MIX),
 *       Grid relation (LESS, GREATER),
 *       Back-dated variants,
 *       Per-classification-type matrix for Doc-Select-Document</li>
 *   <li><b>REGRESSION_PHASE3</b> (3 tests):
 *       Specific document-type filtering (INV-only, DN-only, etc.)</li>
 * </ul>
 */
public enum ApVoucherExecutionGroup {

    /**
     * Core smoke tests — fast CI build verification.
     * Covers the mandatory baseline for both Advance and Document payment paths.
     */
    MANDATORY,

    /**
     * Payment-mode variants, TDS, and basic SL/doc count tests.
     * Extends MANDATORY coverage to user-defined SL classifications and additional payment types.
     */
    REGRESSION_PHASE1,

    /**
     * Full classification-type matrix, multi-SL configs, amount modes, grid edge cases,
     * back-dated document headers.
     */
    REGRESSION_PHASE2,

    /**
     * Specific document-type filtering (INV-only, DN-only, etc.).
     * Requires extended pending-document queries with {@code entype} filter.
     */
    REGRESSION_PHASE3
}
