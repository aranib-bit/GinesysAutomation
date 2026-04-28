package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.CashBankType;
import com.gsl.model.finance.AdmCls;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant.AmountMode;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant.DateMode;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant.GridRelation;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant.PaymentMode;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant.RefSiteMode;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Catalogue of all {@link SelectDocumentVariant} combinations for the "Select Document"
 * AR Voucher scenario.
 *
 * <h3>Variant ordering in {@code DbArVoucherDataSource.load()}</h3>
 * <pre>
 *  Group 0 — {@link #mandatoryBaselineVariant}      : 1 baseline per SL category (system + user)
 *  Group 1 — {@link #group1Variants()}              : Reference uniqueness (static, 2 variants) — Bank GL
 *  Group 2 — {@link #forSystemClassificationType}   : System SL types (cmpcode=1) — 10 variants each
 *             {@link #forUserDefinedClassificationType} : User-defined SL types (cmpcode=0) — 3 variants each
 *  Groups 3–8 — {@link #groups3to8Variants()}       : Customer site, SL/Doc count, Amount mode,
 *                                                     Grid relation, Payment mode, Date — Bank GL
 *  Cash GL — {@link #cashGlVariants()}              : Explicit Cash GL variants
 * </pre>
 *
 * <p>All Bank GL variants use {@link CashBankType#BANK}; Cash GL variants use {@link CashBankType#CASH}.
 * Payment mode rules: Cash GL → always {@link PaymentMode#CASH}; Bank GL → NONE/CHEQUE/NEFT_RTGS/MIX.
 */
public final class SelectDocumentCombinations {

    private SelectDocumentCombinations() {}

    // ─── Group 0: Mandatory baselines ────────────────────────────────────────

    /**
     * Returns one mandatory baseline variant for the given classification type.
     *
     * <p>Mandatory fields only: 1SL × 1Doc, Amt:FULL, Grid:EQUAL, Pmt:NONE, Date:TODAY.
     * No payment rows, no grid shift, no back-dating — purely verifies the happy path
     * for this SL category (system or user-defined).
     *
     * <p>Called twice at DataSupplier time: once with the first system-defined {@link AdmCls}
     * and once with the first user-defined {@link AdmCls} found in pending AR.
     *
     * @param cls the AdmCls row (cmpcode=1 → system baseline, cmpcode=0 → user baseline)
     * @return one mandatory baseline {@link SelectDocumentVariant}
     */
    public static SelectDocumentVariant mandatoryBaselineVariant(AdmCls cls) {
        String label  = cls.getClsname() + "(" + cls.cmpLabel() + ")";
        String id     = "SD_MAND_" + cls.getClsname().toUpperCase().replace(" ", "_")
                        + "_" + cls.cmpLabel().toUpperCase();
        return SelectDocumentVariant.builder()
                .variantId(id)
                .testCaseName("SEL_DOC | [Bank GL] | " + label
                        + " | Mandatory | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slClsCode(cls.getClscode())
                .customerTypeSl(cls.isCustomerType())
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build();
    }

    // ─── Group 1: Reference uniqueness (static, Bank GL) ─────────────────────

    /**
     * Returns the two reference-uniqueness baseline variants (Bank GL).
     * Always runs after Group 0 mandatory baselines.
     */
    public static List<SelectDocumentVariant> group1Variants() {
        return List.of(

            // Finance module — duplicate reference is allowed by the application
            SelectDocumentVariant.builder()
                .variantId("SD_REF_FIN")
                .testCaseName("SEL_DOC | [Bank GL] | Ref:Finance-Dup | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(true)
                .build(),

            // Non-Finance module — reference must be unique in finvchmain
            SelectDocumentVariant.builder()
                .variantId("SD_REF_UNIQUE")
                .testCaseName("SEL_DOC | [Bank GL] | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build()
        );
    }

    // ─── Group 2: SL classification types (DYNAMIC) ───────────────────────────
    //
    // System types  (cmpcode=1): 10 variants each — full dimensional coverage.
    // User-defined  (cmpcode=0):  3 variants each — focused subset only.
    //
    // Dispatch in DbArVoucherDataSource.buildVariantList():
    //   cmpcode == 1  →  forSystemClassificationType(cls)
    //   cmpcode == 0  →  forUserDefinedClassificationType(cls)

    /**
     * Builds 9 Bank GL variants + 1 Cash GL variant for a system-defined SL classification
     * ({@code admcls.cmpcode = 1}: Customer, Supplier, Transporter, Jobber, Agent, Others, Channel).
     *
     * <h4>Block 1 — RANDOM payment, Bank GL</h4>
     * <ol>
     *   <li>1SL × 1Doc  | Amt:FULL    | Pmt:RANDOM | Date:TODAY</li>
     *   <li>1SL × 1Doc  | Amt:PARTIAL | Pmt:RANDOM | Date:BACKDATE(-N)</li>
     *   <li>1SL × NDocs | Amt:FULL    | Pmt:RANDOM | Date:TODAY</li>
     *   <li>1SL × NDocs | Amt:PARTIAL | Pmt:RANDOM | Date:TODAY</li>
     *   <li>NSL × NDocs | Amt:FULL    | Pmt:RANDOM | Date:TODAY</li>
     *   <li>NSL × NDocs | Amt:PARTIAL | Pmt:RANDOM | Date:TODAY</li>
     * </ol>
     *
     * <h4>Block 2 — explicit payment modes, Bank GL</h4>
     * <ol start="7">
     *   <li>1SL × 1Doc  | Amt:PARTIAL | Pmt:NONE      | Date:BACKDATE(-N)</li>
     *   <li>1SL × 1Doc  | Amt:FULL    | Pmt:CHEQUE    | Date:TODAY</li>
     *   <li>1SL × 1Doc  | Amt:FULL    | Pmt:NEFT/RTGS | Date:TODAY</li>
     * </ol>
     *
     * <h4>Block 3 — Cash GL variant</h4>
     * <ol start="10">
     *   <li>1SL × 1Doc  | Amt:FULL    | Pmt:CASH      | Date:TODAY</li>
     * </ol>
     *
     * @param cls one system-defined {@link AdmCls} row (cmpcode=1)
     * @return ten {@link SelectDocumentVariant}s for this classification type
     */
    public static List<SelectDocumentVariant> forSystemClassificationType(AdmCls cls) {
        String clsLabel = cls.getClsname() + "(" + cls.cmpLabel() + ")";
        String base     = "SD_CLS_" + cls.getClsname().toUpperCase().replace(" ", "_");

        int dynDocs   = ThreadLocalRandom.current().nextInt(2, 5);
        int dynSls    = ThreadLocalRandom.current().nextInt(2, 4);
        int dynSlDocs = ThreadLocalRandom.current().nextInt(2, 5);
        int backDays1 = ThreadLocalRandom.current().nextInt(1, 61);
        int backDays2 = ThreadLocalRandom.current().nextInt(1, 61);

        return List.of(
            // ── Block 1: RANDOM payment, Bank GL ─────────────────────────────────
            clsVariant(base + "_FULL_RND",        clsLabel, cls, CashBankType.BANK, 1,      1,         AmountMode.FULL,    PaymentMode.RANDOM,    DateMode.CURRENT_DATE, 0),
            clsVariant(base + "_PART_RND_BACK",   clsLabel, cls, CashBankType.BANK, 1,      1,         AmountMode.PARTIAL, PaymentMode.RANDOM,    DateMode.BACK_DATED,   backDays1),
            clsVariant(base + "_FULL_NDOC_RND",   clsLabel, cls, CashBankType.BANK, 1,      dynDocs,   AmountMode.FULL,    PaymentMode.RANDOM,    DateMode.CURRENT_DATE, 0),
            clsVariant(base + "_PART_NDOC_RND",   clsLabel, cls, CashBankType.BANK, 1,      dynDocs,   AmountMode.PARTIAL, PaymentMode.RANDOM,    DateMode.CURRENT_DATE, 0),
            clsVariant(base + "_FULL_NSL_RND",    clsLabel, cls, CashBankType.BANK, dynSls, dynSlDocs, AmountMode.FULL,    PaymentMode.RANDOM,    DateMode.CURRENT_DATE, 0),
            clsVariant(base + "_PART_NSL_RND",    clsLabel, cls, CashBankType.BANK, dynSls, dynSlDocs, AmountMode.PARTIAL, PaymentMode.RANDOM,    DateMode.CURRENT_DATE, 0),
            // ── Block 2: explicit payment modes, Bank GL ──────────────────────────
            clsVariant(base + "_PART_NONE_BACK",  clsLabel, cls, CashBankType.BANK, 1,      1,         AmountMode.PARTIAL, PaymentMode.NONE,      DateMode.BACK_DATED,   backDays2),
            clsVariant(base + "_FULL_CHQ",        clsLabel, cls, CashBankType.BANK, 1,      1,         AmountMode.FULL,    PaymentMode.CHEQUE,    DateMode.CURRENT_DATE, 0),
            clsVariant(base + "_FULL_NEFT",       clsLabel, cls, CashBankType.BANK, 1,      1,         AmountMode.FULL,    PaymentMode.NEFT_RTGS, DateMode.CURRENT_DATE, 0),
            // ── Block 3: Cash GL variant ──────────────────────────────────────────
            clsVariant(base + "_FULL_CASH",       clsLabel, cls, CashBankType.CASH, 1,      1,         AmountMode.FULL,    PaymentMode.CASH,      DateMode.CURRENT_DATE, 0)
        );
    }

    /**
     * Builds 3 focused variants for a user-defined SL classification
     * ({@code admcls.cmpcode = 0}: TDS, Staff, and any custom types).
     *
     * <p>A reduced set vs. system types — the full dimensional matrix is already
     * covered by system types and Groups 3–8. These variants verify that user-defined
     * SL types work end-to-end without duplicating every combination.
     *
     * <ol>
     *   <li>1SL × 1Doc | Amt:PARTIAL | Pmt:NONE      | Date:BACKDATE(-N) — Bank GL</li>
     *   <li>1SL × 1Doc | Amt:FULL    | Pmt:CHEQUE    | Date:TODAY        — Bank GL</li>
     *   <li>1SL × 1Doc | Amt:FULL    | Pmt:CASH      | Date:TODAY        — Cash GL</li>
     * </ol>
     *
     * @param cls one user-defined {@link AdmCls} row (cmpcode=0, ext=N)
     * @return three {@link SelectDocumentVariant}s for this classification type
     */
    public static List<SelectDocumentVariant> forUserDefinedClassificationType(AdmCls cls) {
        String clsLabel = cls.getClsname() + "(" + cls.cmpLabel() + ")";
        String base     = "SD_CLS_" + cls.getClsname().toUpperCase().replace(" ", "_");

        int backDays = ThreadLocalRandom.current().nextInt(1, 61);

        return List.of(
            clsVariant(base + "_PART_NONE_BACK", clsLabel, cls, CashBankType.BANK, 1, 1, AmountMode.PARTIAL, PaymentMode.NONE,   DateMode.BACK_DATED,   backDays),
            clsVariant(base + "_FULL_CHQ",       clsLabel, cls, CashBankType.BANK, 1, 1, AmountMode.FULL,    PaymentMode.CHEQUE, DateMode.CURRENT_DATE, 0),
            clsVariant(base + "_FULL_CASH",      clsLabel, cls, CashBankType.CASH, 1, 1, AmountMode.FULL,    PaymentMode.CASH,   DateMode.CURRENT_DATE, 0)
        );
    }

    private static SelectDocumentVariant clsVariant(
            String variantId, String clsLabel, AdmCls cls, CashBankType glType,
            int slCount, int docsPerSl,
            AmountMode amt, PaymentMode pmt, DateMode date, int backDateDays) {

        String glLabel   = glType == CashBankType.CASH ? "[Cash GL]" : "[Bank GL]";
        String pmtLabel  = pmt == PaymentMode.NEFT_RTGS ? "Pmt:NEFT/RTGS"
                         : pmt == PaymentMode.CASH      ? "Pmt:CASH"
                         : pmt == PaymentMode.RANDOM    ? "Pmt:RANDOM"
                         : "Pmt:" + pmt;
        String dateLabel = date == DateMode.BACK_DATED
                         ? "Date:BACKDATE(-" + backDateDays + ")"
                         : "Date:TODAY";

        return SelectDocumentVariant.builder()
            .variantId(variantId)
            .testCaseName("SEL_DOC | " + glLabel + " | " + clsLabel + " | " + slCount + "SL-" + docsPerSl + "Doc"
                    + " | Amt:" + amt + " | Grid:EQUAL | " + pmtLabel + " | " + dateLabel)
            .glType(glType)
            .slClsCode(cls.getClscode())
            .customerTypeSl(cls.isCustomerType())
            .slCount(slCount).docsPerSl(docsPerSl)
            .amountMode(amt)
            .gridRelation(GridRelation.EQUAL)
            .paymentMode(pmt)
            .dateMode(date)
            .backDateDays(date == DateMode.BACK_DATED ? backDateDays : 30)
            .allowDuplicateReference(false)
            .build();
    }

    // ─── Groups 3–8: Remaining static variants (Bank GL) ─────────────────────

    /**
     * Returns all non-classification static variants (Groups 3–8), all using Bank GLs.
     * Cash GL variants are in {@link #cashGlVariants()}.
     */
    public static List<SelectDocumentVariant> groups3to8Variants() {
        return List.of(

            // ─── Group 3: Customer-type SL – Reference Site variations ───────────────
            //
            // Non-customer SL types always auto-populate referenceSite with the
            // transaction owner site — no dedicated variants needed for them.
            // Customer-type SL (clstype='C') has a richer LOV (OS-OO-CM sites +
            // tagged sites) and requires explicit variant coverage.

            SelectDocumentVariant.builder()
                .variantId("SD_CUST_REFSITE_OS_OO_CM_OWN")
                .testCaseName("SEL_DOC | [Bank GL] | Customer(System) | CustSL:OS-OO-CM:Owner | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slClsCode(1L)
                .customerTypeSl(true)
                .refSiteMode(RefSiteMode.OS_OO_CM_OWNER)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_CUST_REFSITE_OS_OO_CM_ALT")
                .testCaseName("SEL_DOC | [Bank GL] | Customer(System) | CustSL:OS-OO-CM:Alt | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slClsCode(1L)
                .customerTypeSl(true)
                .refSiteMode(RefSiteMode.OS_OO_CM_ALT)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_CUST_REFSITE_TAGGED_OU")
                .testCaseName("SEL_DOC | [Bank GL] | Customer(System) | CustSL:Tagged:OU-Match | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slClsCode(1L)
                .customerTypeSl(true)
                .refSiteMode(RefSiteMode.TAGGED_OU_MATCH)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_CUST_REFSITE_TAGGED_OM_TS")
                .testCaseName("SEL_DOC | [Bank GL] | Customer(System) | CustSL:Tagged:OM-TS | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slClsCode(1L)
                .customerTypeSl(true)
                .refSiteMode(RefSiteMode.TAGGED_OM_TS)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            // ─── Group 4: SL count × Document count combinations ─────────────────────

            SelectDocumentVariant.builder()
                .variantId("SD_1SL_1DOC")
                .testCaseName("SEL_DOC | [Bank GL] | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_1SL_MULTI_DOC")
                .testCaseName("SEL_DOC | [Bank GL] | 1SL-3Doc | Amt:FULL | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(1).docsPerSl(3)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_MULTI_SL_1DOC")
                .testCaseName("SEL_DOC | [Bank GL] | 3SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(3).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_MULTI_SL_MULTI_DOC")
                .testCaseName("SEL_DOC | [Bank GL] | 3SL-3Doc | Amt:FULL | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(3).docsPerSl(3)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            // ─── Group 5: Received amount modes ──────────────────────────────────────

            SelectDocumentVariant.builder()
                .variantId("SD_AMT_FULL")
                .testCaseName("SEL_DOC | [Bank GL] | 1SL-2Doc | Amt:FULL | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(1).docsPerSl(2)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_AMT_PARTIAL")
                .testCaseName("SEL_DOC | [Bank GL] | 1SL-1Doc | Amt:PARTIAL | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.PARTIAL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_AMT_MIX")
                .testCaseName("SEL_DOC | [Bank GL] | 1SL-2Doc | Amt:MIX | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(1).docsPerSl(2)
                .amountMode(AmountMode.MIX)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            // ─── Group 6: Grid Amount Cr vs selected document amount ──────────────────

            SelectDocumentVariant.builder()
                .variantId("SD_GRID_EQUAL")
                .testCaseName("SEL_DOC | [Bank GL] | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_GRID_LESS")
                .testCaseName("SEL_DOC | [Bank GL] | 1SL-1Doc | Amt:FULL | Grid:LESS | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.LESS)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .expectValidationError(true)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_GRID_GREATER")
                .testCaseName("SEL_DOC | [Bank GL] | 1SL-1Doc | Amt:FULL | Grid:GREATER | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.GREATER)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .expectValidationError(true)
                .build(),

            // ─── Group 7: Payment modes (Bank GL) ────────────────────────────────────

            SelectDocumentVariant.builder()
                .variantId("SD_PMT_NONE")
                .testCaseName("SEL_DOC | [Bank GL] | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_PMT_CHEQUE")
                .testCaseName("SEL_DOC | [Bank GL] | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:CHEQUE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.CHEQUE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_PMT_NEFT")
                .testCaseName("SEL_DOC | [Bank GL] | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:NEFT/RTGS | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NEFT_RTGS)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_PMT_MIX")
                .testCaseName("SEL_DOC | [Bank GL] | 2SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:MIX | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(2).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.MIX)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            // ─── Group 8: Document date ───────────────────────────────────────────────
            // TODO: Clamp backDateDays to valid posting window at resolution time —
            //       query lock days (finparams), open Finance Month, open Accounting Year.

            SelectDocumentVariant.builder()
                .variantId("SD_DATE_TODAY")
                .testCaseName("SEL_DOC | [Bank GL] | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:NONE | Date:TODAY")
                .glType(CashBankType.BANK)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_DATE_BACKDATE")
                .testCaseName("SEL_DOC | [Bank GL] | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:NONE | Date:BACKDATE(-30)")
                .glType(CashBankType.BANK)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.NONE)
                .dateMode(DateMode.BACK_DATED)
                .allowDuplicateReference(false)
                .build()
        );
    }

    // ─── Cash GL variants ─────────────────────────────────────────────────────

    /**
     * Returns explicit Cash GL variants for the SELECT_DOCUMENT scenario.
     *
     * <p>All variants use {@link CashBankType#CASH} and {@link PaymentMode#CASH},
     * ensuring every SL line carries {@code payment_mode='H'} in the DB.
     */
    public static List<SelectDocumentVariant> cashGlVariants() {
        int backDays = ThreadLocalRandom.current().nextInt(1, 61);

        return List.of(

            SelectDocumentVariant.builder()
                .variantId("SD_CASH_1SL_1DOC_FULL")
                .testCaseName("SEL_DOC | [Cash GL] | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:CASH | Date:TODAY")
                .glType(CashBankType.CASH)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.CASH)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_CASH_1SL_1DOC_PART")
                .testCaseName("SEL_DOC | [Cash GL] | 1SL-1Doc | Amt:PARTIAL | Grid:EQUAL | Pmt:CASH | Date:BACKDATE(-" + backDays + ")")
                .glType(CashBankType.CASH)
                .slCount(1).docsPerSl(1)
                .amountMode(AmountMode.PARTIAL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.CASH)
                .dateMode(DateMode.BACK_DATED)
                .backDateDays(backDays)
                .allowDuplicateReference(false)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_CASH_1SL_MULTI_DOC")
                .testCaseName("SEL_DOC | [Cash GL] | 1SL-3Doc | Amt:FULL | Grid:EQUAL | Pmt:CASH | Date:TODAY")
                .glType(CashBankType.CASH)
                .slCount(1).docsPerSl(3)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.CASH)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build(),

            SelectDocumentVariant.builder()
                .variantId("SD_CASH_MULTI_SL")
                .testCaseName("SEL_DOC | [Cash GL] | 3SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:CASH | Date:TODAY")
                .glType(CashBankType.CASH)
                .slCount(3).docsPerSl(1)
                .amountMode(AmountMode.FULL)
                .gridRelation(GridRelation.EQUAL)
                .paymentMode(PaymentMode.CASH)
                .dateMode(DateMode.CURRENT_DATE)
                .allowDuplicateReference(false)
                .build()
        );
    }
}
