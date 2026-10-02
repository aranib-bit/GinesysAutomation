package com.gsl.config;

import org.aeonbits.owner.Config;

/**
 * Finance-module-specific tuning: subledger-selection strategy, ExtJS mask wait timeouts,
 * backdating rules, DB-creation reconciliation tolerance, cheque-country selection, and the
 * known/unknown cheque weighting used by GV/AP/AR voucher test-data generation. Read from
 * {@code finance.properties} and consumed across {@code com.gsl.pages.Finance} and
 * {@code com.gsl.database.finance}.
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "file:${user.dir}/src/test/resources/env/${env}/finance.properties",
        "file:${user.dir}/src/test/resources/config/finance.properties"
})
public interface FinanceConfig extends Config {

    /** Strategy used to pick a subledger in Finance vouchers (e.g. JAVASCRIPT vs. native UI selection). */
    @Key("subledger.selection.mode")
    @DefaultValue("JAVASCRIPT")
    String subLedgerSelectionMode();

    /** Timeout (seconds) for an ExtJS {@code .x-mask}/{@code .x-mask-msg} wait to clear. */
    @Key("extjs.timeout")
    @DefaultValue("40")
    long extjsTimeout();

    /** Minimum ExtJS mask wait (seconds); ignored if greater than {@link #extjsTimeout()} — see {@link ConfigValidator#validate()}. */
    @Key("extjs.min.timeout")
    @DefaultValue("10")
    long extjsMinTimeout();

    /** Max number of days a Finance voucher may be backdated. */
    @Key("finance.backdate.max.days")
    @DefaultValue("58")
    int financeBackdateMaxDays();

    /** Allowed time skew (seconds) between a voucher's expected and actual DB creation timestamp during validation. */
    @Key("db.creation.tolerance")
    @DefaultValue("60")
    long dbCreationTolerance();

    /** Cheque-issuing country used for cheque-related test data (e.g. ROTATE to cycle through supported countries). */
    @Key("cheque.country")
    @DefaultValue("ROTATE")
    String chequeCountry();

    /**
     * Temporary escape hatch for a known, already-logged UI defect: a Numeric UDF field
     * (General Voucher's {@code finvchmain.udfnum01}) displays/saves {@code 0} instead of blank
     * when no value is present. {@code false} by default so the Edit snapshot validator stays
     * strict everywhere except an environment where a tester has deliberately flipped this on
     * (in their own local {@code finance.properties}, not the checked-in default) to work around
     * the known bug — remove this flag entirely once the UI defect is fixed, it is not meant to
     * be a permanent fixture.
     */
    @Key("finance.gv.suppress.udfnum01.zero.bug")
    @DefaultValue("false")
    boolean suppressUdfnum01ZeroBug();

    // ── Round 20 — known/unknown cheque ratio weights (not percentages — see
    // WeightedSequenceBuilder's own Javadoc) ────────────────────────────────────────────────

    /** Relative weight of a known-cheque outcome for General Voucher test data (see class Javadoc re: weights vs. percentages). */
    @Key("gv.cheque.known.weight")
    @DefaultValue("9")
    int gvChequeKnownWeight();

    /** Relative weight of an unknown-cheque outcome for General Voucher test data. */
    @Key("gv.cheque.unknown.weight")
    @DefaultValue("1")
    int gvChequeUnknownWeight();

    // ── Round 6 — per-row payment mix for a multi-row General Voucher (Bank header: each grid row
    // draws Cheque / NEFT-RTGS / no payment at this exact ratio over the run) ──────────────────

    /** Relative weight of a Cheque payment on a grid row of a multi-row-payment GV. */
    @Key("gv.row.payment.cheque.weight")
    @DefaultValue("1")
    int gvRowPaymentChequeWeight();

    /** Relative weight of a NEFT/RTGS payment on a grid row of a multi-row-payment GV. */
    @Key("gv.row.payment.neft.weight")
    @DefaultValue("1")
    int gvRowPaymentNeftWeight();

    /** Relative weight of a grid row of a multi-row-payment GV carrying no payment information. */
    @Key("gv.row.payment.none.weight")
    @DefaultValue("1")
    int gvRowPaymentNoneWeight();

    /** Relative weight of a known-cheque outcome for AP voucher test data. */
    @Key("ap.cheque.known.weight")
    @DefaultValue("9")
    int apChequeKnownWeight();

    /** Relative weight of an unknown-cheque outcome for AP voucher test data. */
    @Key("ap.cheque.unknown.weight")
    @DefaultValue("1")
    int apChequeUnknownWeight();

    /** Relative weight of a known-cheque outcome for AR voucher test data (rare — see {@link #arChequeUnknownWeight()}). */
    @Key("ar.cheque.known.weight")
    @DefaultValue("1")
    int arChequeKnownWeight();

    /** Relative weight of an unknown-cheque outcome for AR voucher test data (the common case). */
    @Key("ar.cheque.unknown.weight")
    @DefaultValue("99")
    int arChequeUnknownWeight();

    /** Sub-split WITHIN AR's rare known bucket — same-bank vs. different-bank match. */
    @Key("ar.cheque.sameBank.weight")
    @DefaultValue("1")
    int arChequeSameBankWeight();

    @Key("ar.cheque.differentBank.weight")
    @DefaultValue("1")
    int arChequeDifferentBankWeight();
}
