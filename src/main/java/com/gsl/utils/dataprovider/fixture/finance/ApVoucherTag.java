package com.gsl.utils.dataprovider.fixture.finance;

/**
 * Tag taxonomy for AP Voucher test variants.
 *
 * <p>Tags are orthogonal to {@link ApVoucherExecutionGroup} — groups answer
 * <em>how often</em> to run (MANDATORY/REGRESSION); tags answer <em>what</em>
 * is being tested. Both filters can be applied independently and combined in AND:
 *
 * <pre>{@code
 *   mvn clean test -Papv-advance -Dapv.group=MANDATORY
 *   # Runs MANDATORY advance tests only
 * }</pre>
 *
 * <h3>Matching semantics: OR</h3>
 * {@code apv.tag=ADVANCE,DOC_FIFO} runs tests whose tag set intersects
 * {@code {ADVANCE, DOC_FIFO}} — i.e. all advance tests <em>plus</em> all FIFO
 * document tests. An empty active-tag set means "no filter — run all".
 *
 * <h3>Three tiers</h3>
 * <ol>
 *   <li><b>Path</b> (coarse) — matches the entire On Account path.</li>
 *   <li><b>Feature</b> (medium) — matches a specific scenario type.</li>
 *   <li><b>Dimensional</b> (fine-grained) — cross-cutting attribute (GL type, SL count, etc.).</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * Tags are assigned at placeholder-build time by
 * {@code DbApVoucherDataSource.tagsFor(ApVoucherScenarioType, ApVoucherFixtureOptions)}.
 * The filter is resolved from system property {@code apv.tag} (Maven Surefire path)
 * or TestNG XML parameter {@code apv.tag} (IntelliJ direct-run path).
 */
public enum ApVoucherTag {

    // ─── Tier 1: Path ─────────────────────────────────────────────────────────

    /** All On Account = Yes tests (advance payment). */
    ADVANCE,

    /** All On Account = No tests (document payment). */
    DOCUMENT,

    // ─── Tier 2: Feature ──────────────────────────────────────────────────────

    /** Advance payment without TDS deduction ({@link ApVoucherScenarioType#ADVANCE_NO_TDS}). */
    ADVANCE_NO_TDS,

    /** Advance payment with TDS deduction ({@link ApVoucherScenarioType#ADVANCE_WITH_TDS}). */
    ADVANCE_TDS,

    /** Document payment, no adjustment type ({@link ApVoucherScenarioType#DOC_NO_ADJUSTMENT}). */
    DOC_NO_ADJUSTMENT,

    /** Document payment via manual Select Document popup ({@link ApVoucherScenarioType#DOC_SELECT_DOCUMENT}). */
    DOC_SELECT_DOCUMENT,

    /** Document payment via FIFO automatic set-off ({@link ApVoucherScenarioType#DOC_FIFO}). */
    DOC_FIFO,

    /** Document payment via Reference Sitewise FIFO ({@link ApVoucherScenarioType#DOC_REFERENCE_SITEWISE_FIFO}). */
    DOC_REF_SITEWISE_FIFO,

    // ─── Tier 3: Dimensional ──────────────────────────────────────────────────

    /** Variant uses a Cash GL ({@code fingl.srctype = 'C'}). */
    CASH_GL,

    /** Variant uses a Bank GL ({@code fingl.srctype = 'B'}). */
    BANK_GL,

    /** Variant has more than one sub-ledger row ({@code subLedgerCount > 1}). */
    MULTI_SL,

    /** Variant uses a back-dated document header ({@code ApVoucherFixtureOptions.isBackdated()}). */
    BACKDATE,

    /** Advance variant with TDS exception rate populated ({@code isTdsExceptionRate = true}). */
    TDS_EXCEPTION_RATE
}
