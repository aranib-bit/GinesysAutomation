package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.CashBankType;
import lombok.Builder;
import lombok.Getter;

/**
 * Describes one parameterised combination of the "Select Document" AR Voucher scenario.
 *
 * <p>Each instance maps to one TestNG DataProvider row and one test-report entry.
 * All dimension constants are defined as inner enums to keep the contract self-contained.
 *
 * <p>Instances are created by {@link SelectDocumentCombinations#group1Variants()},
 * {@link SelectDocumentCombinations#forClassificationType(com.gsl.model.finance.AdmCls)},
 * and {@link SelectDocumentCombinations#groups3to8Variants()}, then carried through
 * the placeholder lifecycle via {@link ArVoucherFixtureOptions#getSelectDocVariant()}.
 *
 * <p><b>Open items (fill in before enabling affected variants):</b>
 * <ul>
 *   <li>#2 — What qualifies an SL as "customer type" in the DB?</li>
 *   <li>#3 — Does LESS/GREATER grid amount produce a hard validation error or a save-able warning?</li>
 *   <li>#4 — Exact join column between {@code v_pending_adj / finsl} and {@code admcls}
 *             (e.g. {@code finsl.admcls_code = admcls.code})</li>
 * </ul>
 */
@Builder
@Getter
public class SelectDocumentVariant {

    // ─── Dimension enums ─────────────────────────────────────────────────────

    /**
     * How the SubLedgerInformation {@code amountCr} (Grid Amount Cr) is computed
     * relative to the sum of selected document pending amounts.
     */
    public enum AmountMode {
        /** Grid Cr = sum of all selected doc pending amounts. */
        FULL,
        /** Grid Cr = 50 % of the total pending, rounded to 2 dp. */
        PARTIAL,
        /** First doc: full pending; remaining docs: 50 % of their individual pending. */
        MIX
    }

    /**
     * How Grid Amount Cr compares to the total selected document amount.
     * LESS and GREATER are used to verify UI validation behaviour.
     * GREATER shows a "Proceed Anyway / Stay" confirmation popup (saveable warning, not a hard block).
     * LESS behaviour is still unconfirmed — see open item #3.
     */
    public enum GridRelation {
        /** Grid Cr == total doc amount (normal case). */
        EQUAL,
        /** Grid Cr is less than total doc amount — triggers UI mismatch validation. */
        LESS,
        /** Grid Cr is greater than total doc amount (by any amount) — shows Proceed Anyway / Stay popup. */
        GREATER
    }

    /**
     * Which payment information rows are generated.
     *
     * <p>Valid modes per GL type:
     * <ul>
     *   <li>{@link CashBankType#CASH} GL — only {@link #CASH} is valid; RANDOM resolves to CASH.</li>
     *   <li>{@link CashBankType#BANK} GL — NONE, CHEQUE, NEFT_RTGS, MIX; RANDOM picks from these three.</li>
     * </ul>
     */
    public enum PaymentMode {
        /** No payment rows; triggers "Ignore &amp; Proceed" warning after save. Valid for Bank GLs only. */
        NONE,
        /** One Cheque row per SL line, with a generated instrument number. Valid for Bank GLs only. */
        CHEQUE,
        /** One NEFT/RTGS row per SL line, with a generated instrument number. Valid for Bank GLs only. */
        NEFT_RTGS,
        /** Alternates Cheque / NEFT_RTGS across SL lines (odd = Cheque, even = NEFT/RTGS). Bank GLs only. */
        MIX,
        /**
         * Cash payment — one Cash row per SL line, no instrument number required.
         * Only applicable when {@link SelectDocumentVariant#glType} is {@link CashBankType#CASH}.
         */
        CASH,
        /**
         * Payment mode is chosen randomly at test-execution time.
         * Resolves to {@link #CASH} for Cash GLs; resolves to NONE/CHEQUE/NEFT_RTGS for Bank GLs.
         * The resolved mode is stamped into the dynamic test case name.
         */
        RANDOM
    }

    /**
     * The document date set on the AR Voucher header.
     */
    public enum DateMode {
        CURRENT_DATE,
        /** {@link SelectDocumentVariant#backDateDays} calendar days before today. */
        BACK_DATED
    }

    /**
     * The {@code referenceSite} value for customer-type SL rows.
     * Only applied when {@link #isCustomerTypeSl()} is {@code true}.
     *
     * <p>Non-customer SL types always auto-populate with the transaction owner site —
     * no {@code RefSiteMode} is needed for those variants.
     */
    public enum RefSiteMode {
        /** OS-OO-CM OU-matched site → pick the transaction owner site itself. */
        OS_OO_CM_OWNER,
        /** OS-OO-CM OU-matched site → pick any other active OS-OO-CM site (not owner). */
        OS_OO_CM_ALT,
        /** Tagged site of type MS-CO-CM or US-CO-CM with OU match. */
        TAGGED_OU_MATCH,
        /** Tagged site of type US-CO-OM, US-CO-OM-TS, or MS-CO-OM-TS. */
        TAGGED_OM_TS
    }

    // ─── Variant fields ───────────────────────────────────────────────────────

    /**
     * Unique short identifier used as the TestNG test-case ID.
     * Example: {@code "SD_1SL_1DOC_FULL_NO_PMT_TODAY"}.
     */
    String variantId;

    /**
     * Human-readable name that appears in ExtentReports and TestNG reports.
     * Pattern: {@code "SEL_DOC | [Cash GL] | <N>SL-<M>Doc | Amt:<mode> | Grid:<rel> | Pmt:<pmt> | Date:<date>"}
     */
    String testCaseName;

    /**
     * The GL type this variant targets — {@link CashBankType#CASH} or {@link CashBankType#BANK}.
     * Drives which GL is picked from the DB and enforces payment-mode rules:
     * Cash GL → all SL lines use {@link PaymentMode#CASH}; Bank GL → NONE/CHEQUE/NEFT_RTGS/MIX.
     * Defaults to {@link CashBankType#BANK} for backward compatibility.
     */
    @Builder.Default
    CashBankType glType = CashBankType.BANK;

    /**
     * {@code admcls.clscode} value used to filter sub-ledgers by classification type.
     * {@code 0} (default) means no filtering — any SL type is accepted.
     * Positive values map to a specific classification in admcls (joined via finsl.clscode).
     *
     * <p>Run {@link com.gsl.database.query.AdmClsQuery#findAllFromAdmCls} to discover
     * available clscode values in your environment, then replace the placeholder values
     * in {@link SelectDocumentCombinations}.
     *
     * <p>cmpcode=1 = system-defined, cmpcode=0 = user-defined (from admcls.cmpcode).
     */
    long slClsCode;

    /**
     * When {@code true}, the sub-ledger is expected to be a "customer type" SL,
     * and {@link #refSiteMode} controls the referenceSite value.
     * <!-- TODO: per open item #2 – define the DB filter that identifies customer-type SLs -->
     */
    boolean customerTypeSl;

    /** Number of sub-ledger rows to request from DB (maps to ArVoucherFixtureOptions.subLedgerCount). */
    int slCount;

    /** Maximum documents per SL to request from DB (maps to ArVoucherFixtureOptions.documentCount). */
    int docsPerSl;

    /** How Grid Amount Cr is derived from the pending document amounts. */
    AmountMode amountMode;

    /**
     * Relationship between Grid Amount Cr and total selected document amount.
     * EQUAL is the default; LESS and GREATER trigger UI validation.
     */
    GridRelation gridRelation;

    /** Which payment information rows to generate. */
    PaymentMode paymentMode;

    /** The document date to use on the AR Voucher header. */
    DateMode dateMode;

    /**
     * referenceSite mode for customer-type SL rows.
     * Ignored when {@link #isCustomerTypeSl()} is {@code false}.
     */
    RefSiteMode refSiteMode;

    /**
     * When {@code true}, the voucher reference value may repeat an existing {@code finvchmain.refno}
     * (Finance module behaviour — duplicates are allowed).
     * When {@code false}, a fresh unique reference is generated and DB-verified.
     */
    boolean allowDuplicateReference;

    /**
     * When {@code true}, the test expects the UI to show a validation error (save blocked)
     * after the grid/document amount mismatch — relevant for LESS and GREATER grid relations.
     * When {@code false}, a warning is shown but the voucher saves successfully.
     * <!-- TODO: per open item #3 – confirm this flag for LESS/GREATER -->
     */
    boolean expectValidationError;

    /**
     * Number of calendar days to subtract from today when {@link #dateMode} is
     * {@link DateMode#BACK_DATED}. Defaults to 30.
     * Classification-type variants randomise this between 1 and 60 at DataSupplier time
     * so different back-dated windows are covered across runs.
     */
    @Builder.Default
    int backDateDays = 30;

    /**
     * Execution bucket this variant belongs to.
     * {@code null} means the variant runs in all groups (legacy / untagged).
     * Set via {@link ArVoucherExecutionGroup}.
     */
    ArVoucherExecutionGroup executionGroup;
}
