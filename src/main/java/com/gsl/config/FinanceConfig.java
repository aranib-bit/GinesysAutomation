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

    /**
     * Minimum numbers a document scheme must still have left ({@code 10^width - 1 - lastno}) to be
     * considered available by {@code DocSchemeResolver}. A scheme below this is "exhausted".
     */
    @Key("docscheme.min.headroom")
    @DefaultValue("1")
    int docSchemeMinHeadroom();

    /**
     * Docnames of the dedicated small-width test schemes (created by the doc-scheme provisioner and
     * filled by the k6 filler). {@code DocSchemeResolver} AUTO mode never picks these, so regular
     * tests cannot consume them; the filler/provisioner touch ONLY these docnames. Comma-separated.
     */
    @Key("docscheme.test.names")
    @DefaultValue("")
    String[] docSchemeTestNames();

    /**
     * Environment folders ({@code -Denv}) in which the doc-scheme provisioner and k6 filler may run.
     * They create/fill data in bulk, so anything not listed here (e.g. a shared or production-like
     * tenant) is refused. Comma-separated.
     */
    @Key("docscheme.provision.allowed.envs")
    @DefaultValue("clone-staging")
    String[] docSchemeProvisionAllowedEnvs();

    /**
     * Expected UI error text when Save is clicked on a document scheme with no numbers left. Deliberately
     * has NO default: the app currently shows no proper message (known defect), so the wording is whatever
     * product decides — the exhausted-scheme scenario fails fast with a clear message while this is blank.
     */
    @Key("docscheme.exhausted.expected.message")
    @DefaultValue("")
    String docSchemeExhaustedExpectedMessage();

    /** Dedicated test scheme (docname, number prefix) used to produce a full scheme for General Voucher. */
    @Key("docscheme.test.gv.name")
    @DefaultValue("GV Test")
    String docSchemeTestGvName();

    @Key("docscheme.test.gv.prefix")
    @DefaultValue("TGV/")
    String docSchemeTestGvPrefix();

    /** Dedicated test scheme shared by AR and AP (like the real "AR/AP Voucher" scheme). */
    @Key("docscheme.test.arap.name")
    @DefaultValue("AR AP Test")
    String docSchemeTestArApName();

    @Key("docscheme.test.arap.prefix")
    @DefaultValue("TAR/")
    String docSchemeTestArApPrefix();

    /** Digits in the dedicated test schemes' numbers: cap = 10^width - 1 documents to fill (3 -> 999). */
    @Key("docscheme.test.width")
    @DefaultValue("3")
    int docSchemeTestWidth();
}
