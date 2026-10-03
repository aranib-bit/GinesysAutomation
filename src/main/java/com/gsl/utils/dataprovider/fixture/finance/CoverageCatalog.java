package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * Static, document-only catalog of every planned GV / AR / AP voucher test combination — the
 * "what exists" half of the per-run coverage workbook ({@code com.gsl.reports.CoverageWorkbookBuilder}
 * joins it to what actually ran). Lives next to the fixtures (domain layer, per
 * {@code docs/conventions/utility-layering.md}) because it knows what the combinations mean;
 * the workbook writer itself ({@code XlsxWriter}) stays domain-free. Nothing here drives execution.
 *
 * <p><b>Sources of truth</b>
 * <ul>
 *   <li>GV Create — {@link GeneralVoucherCombinations#allCombinations()} plus the standalone
 *       {@code MIXED_TYPES} voucher.</li>
 *   <li>GV Edit — {@link GeneralVoucherEditCombinations#allCombinations()} (30 variants).</li>
 *   <li>GV Revert — the Create matrix minus multi-row-payment rows (exactly the filter in
 *       {@code DbGeneralVoucherRevertDataSource.load}) plus the closed-period negative.</li>
 *   <li>GV Delete — the two rows {@code DbGeneralVoucherDeleteDataSource.load} always emits.</li>
 *   <li>AR — {@link ArVoucherScenarioType} plus the static {@link SelectDocumentCombinations} lists.</li>
 *   <li>AP — {@link ApVoucherScenarioType}, {@link ApVoucherEditScenarioType} and the
 *       UNTAGGED/TAGGED Revert/Delete rows.</li>
 * </ul>
 *
 * <p><b>Known limitation (AR/AP groups):</b> the per-variant execution groups of AR/AP rows that
 * are not backed by a shared static list are hardcoded inline inside
 * {@code DbArVoucherDataSource.load} / {@code DbApVoucherDataSource.load} (interleaved with live DB
 * lookups). Lifting them into shared lists could not be done without restructuring those methods,
 * so the groups below are transcribed from reading them and must be kept in sync by hand.
 * Rows that are expanded from DB discoveries (classification types) are represented by one
 * {@link CoverageRow#isDbExpanded()} placeholder per scenario type.
 */
public final class CoverageCatalog {

    private CoverageCatalog() {}

    /** Every planned row across all modules/flows, in module → flow order. */
    public static List<CoverageRow> all() {
        List<CoverageRow> out = new ArrayList<>();
        out.addAll(gvCreate());
        out.addAll(gvEdit());
        out.addAll(gvRevert());
        out.addAll(gvDelete());
        out.addAll(ar());
        out.addAll(ap());
        return out;
    }

    // ── General Voucher ──────────────────────────────────────────────────────────────────────

    /** GV Create: the full combination matrix plus the standalone mixed-ledger-types voucher. */
    public static List<CoverageRow> gvCreate() {
        List<CoverageRow> out = new ArrayList<>();
        for (GeneralVoucherVariant v : GeneralVoucherCombinations.allCombinations()) {
            out.add(gvMatrixRow("Create", v));
        }
        out.add(CoverageRow.builder().module("GV").flow("Create").variantId("MIXED_TYPES")
                .group(CoverageRow.GROUP_ALWAYS)
                .description("One voucher spanning multiple ledger types (Asset/Liability/Income/Expense, Type1/2/3)")
                .scenarioType("MIXED_TYPES").keyFields("mixed ledger types").build());
        return out;
    }

    /** GV Edit: the 30 hand-enumerated scenarios, each with its {@code description}. */
    public static List<CoverageRow> gvEdit() {
        List<CoverageRow> out = new ArrayList<>();
        for (GeneralVoucherEditVariant v : GeneralVoucherEditCombinations.allCombinations()) {
            StringBuilder key = new StringBuilder(v.getSourceStatus().name());
            if (v.getDateVariant() != null) key.append(" | date=").append(v.getDateVariant());
            if (v.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY
                    && v.getHeaderFieldScope() != GeneralVoucherEditVariant.HeaderFieldScope.ALL) {
                key.append(" | scope=").append(v.getHeaderFieldScope());
            }
            if (v.getGlLineType() != null) key.append(" | ").append(v.getGlLineType());
            if (v.getDetailLineEditShape() != DetailLineEditTarget.EditShape.IN_PLACE
                    && v.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE) {
                key.append(" | shape=").append(v.getDetailLineEditShape());
            }
            if (v.getRequiredCashBankType() != null) key.append(" | ").append(v.getRequiredCashBankType());
            if (v.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO) {
                key.append(" | payment=").append(v.getPaymentInfoEditShape());
            }
            if (v.isRequireClosedPeriod()) key.append(" | closed period");
            out.add(CoverageRow.builder().module("GV").flow("Edit").variantId(v.getVariantId())
                    .group(v.getExecutionGroup().name()).description(v.getDescription())
                    .scenarioType(v.getScenarioType().name()).keyFields(key.toString()).build());
        }
        return out;
    }

    /** GV Revert: Create matrix minus multi-row-payment rows, plus the closed-period negative. */
    public static List<CoverageRow> gvRevert() {
        List<CoverageRow> out = new ArrayList<>();
        for (GeneralVoucherVariant v : GeneralVoucherCombinations.allCombinations()) {
            if (v.isMultiRowPayment()) continue;   // Create-only dimension, as in DbGeneralVoucherRevertDataSource
            out.add(gvMatrixRow("Revert", v));
        }
        out.add(CoverageRow.builder().module("GV").flow("Revert").variantId("GV_REVERT_NEGATIVE_MONTH_CLOSED")
                .group(CoverageRow.GROUP_ALWAYS)
                .description("Negative: revert a Posted voucher whose accounting year/financial month is already closed - app must refuse")
                .scenarioType("NEGATIVE").keyFields("closed period").build());
        return out;
    }

    /** GV Delete: the positive row and the closed-period negative (both always emitted). */
    public static List<CoverageRow> gvDelete() {
        List<CoverageRow> out = new ArrayList<>();
        out.add(CoverageRow.builder().module("GV").flow("Delete").variantId("GV_DELETE_POSITIVE")
                .group(CoverageRow.GROUP_ALWAYS)
                .description("Delete an Unposted/Posted General Voucher (own accounting period open)")
                .scenarioType("POSITIVE").keyFields("open period").build());
        out.add(CoverageRow.builder().module("GV").flow("Delete").variantId("GV_DELETE_NEGATIVE_MONTH_CLOSED")
                .group(CoverageRow.GROUP_ALWAYS)
                .description("Negative: delete a voucher whose accounting year/financial month is already closed - the Unpost prep step must be rejected")
                .scenarioType("NEGATIVE").keyFields("closed period").build());
        return out;
    }

    private static CoverageRow gvMatrixRow(String flow, GeneralVoucherVariant v) {
        String key = v.getAccountType() + " | " + v.getGlLineType()
                + " | " + (v.isDebitSide() ? "Dr" : "Cr")
                + " | Reversal=" + (v.isReversal() ? "Y" : "N")
                + " | " + v.getPaymentVariant()
                + " | " + v.getGlCount() + " GL x " + v.getSiteCount() + " Site"
                + (v.isMultiRowPayment() ? " | per-row payments" : "");
        return CoverageRow.builder().module("GV").flow(flow).variantId(v.getVariantId())
                .group(v.getExecutionGroup().name()).description(v.getTestCaseName())
                .scenarioType("COMBINATION").keyFields(key).build();
    }

    // ── Accounts Receivable ──────────────────────────────────────────────────────────────────

    /**
     * AR Create. Groups for the non-{@code SELECT_DOCUMENT} scenarios are transcribed from
     * {@code DbArVoucherDataSource.load}; {@code SELECT_DOCUMENT} contributes the static
     * {@link SelectDocumentCombinations} lists with their real variant ids/groups plus one
     * DB-expanded placeholder for the classification-type-driven variants.
     */
    public static List<CoverageRow> ar() {
        List<CoverageRow> out = new ArrayList<>();
        out.add(arRow("DOC_NO_ADJ_SYSTEM", "MANDATORY", "No adjustment | Bank GL | System SL", "DOC_NO_ADJUSTMENT"));
        out.add(arRow("DOC_NO_ADJ_USER", "REGRESSION_PHASE1", "No adjustment | Bank GL | User SL", "DOC_NO_ADJUSTMENT"));
        for (String base : new String[]{"DB_FIFO", "DB_REF_FIFO"}) {
            String scenario = "DB_FIFO".equals(base) ? "FIFO_SETOFF" : "REFERENCE_SITEWISE_FIFO";
            out.add(arRow(base + "_CASH", "REGRESSION_PHASE1", scenario + " | Cash GL", scenario));
            out.add(arRow(base + "_BANK_SYSTEM", "MANDATORY", scenario + " | Bank GL | System SL", scenario));
            out.add(arRow(base + "_BANK_USER", "REGRESSION_PHASE1", scenario + " | Bank GL | User SL", scenario));
        }
        for (ArVoucherScenarioType s : new ArVoucherScenarioType[]{
                ArVoucherScenarioType.NEG_BLANK_CASHBANK, ArVoucherScenarioType.NEG_BLANK_SUBLEDGER,
                ArVoucherScenarioType.NEG_BLANK_MANDATORY_REFERENCE, ArVoucherScenarioType.BOUND_AMOUNT_MIN_VALID,
                ArVoucherScenarioType.BOUND_AMOUNT_MIN_INVALID}) {
            out.add(arRow("DB_" + s.name(), "REGRESSION_PHASE1", s.name() + " (negative / boundary)", s.name()));
        }
        List<SelectDocumentVariant> select = new ArrayList<>();
        select.addAll(SelectDocumentCombinations.group1Variants());
        select.addAll(SelectDocumentCombinations.groups3to8Variants());
        select.addAll(SelectDocumentCombinations.cashGlVariants());
        select.add(SelectDocumentCombinations.specificDocTypeVariant());
        for (SelectDocumentVariant v : select) {
            out.add(arRow(v.getVariantId(), v.getExecutionGroup().name(), v.getTestCaseName(), "SELECT_DOCUMENT"));
        }
        out.add(CoverageRow.builder().module("AR").flow("Create").variantId("SELECT_DOCUMENT")
                .group("VARIES").scenarioType("SELECT_DOCUMENT").dbExpanded(true)
                .description("Classification-type-driven Select-Document variants (mandatory baselines, per system/user SL type) - expanded from the DB at run time; see Results")
                .build());
        return out;
    }

    private static CoverageRow arRow(String id, String group, String description, String scenarioType) {
        return CoverageRow.builder().module("AR").flow("Create").variantId(id).group(group)
                .description(description).scenarioType(scenarioType).build();
    }

    // ── Accounts Payable ─────────────────────────────────────────────────────────────────────

    /**
     * AP Create / Edit / Revert / Delete. Create groups are transcribed from
     * {@code DbApVoucherDataSource.load} (and its {@code mismatchTier}); Edit groups from
     * {@code DbApVoucherEditDataSource.load}; Revert/Delete are MANDATORY UNTAGGED + TAGGED rows,
     * joined to results by test-case id since those rows carry no variant id.
     * {@code DOC_SELECT_DOCUMENT} is one DB-expanded placeholder (classification-type-driven).
     */
    public static List<CoverageRow> ap() {
        List<CoverageRow> out = new ArrayList<>();
        out.add(apRow("DB_ADV_NO_TDS_MAND", "MANDATORY", "Advance | Bank GL | No TDS", "ADVANCE_NO_TDS"));
        out.add(apRow("DB_ADV_MULTI_SL_PAY_REG2", "REGRESSION_PHASE2", "Advance | Bank GL | Multi-SL | Mixed payment", "ADVANCE_MULTI_SL_WITH_PAYMENT"));
        out.add(apRow("DB_ADV_TDS_MAND", "MANDATORY", "Advance | Bank GL | TDS | Gross = TDS (smoke)", "ADVANCE_WITH_TDS"));
        out.add(apRow("DB_ADV_TDS_PE_REG1", "REGRESSION_PHASE1", "Advance | Bank GL | TDS | Partial edit", "ADVANCE_WITH_TDS"));
        out.add(apRow("DB_ADV_TDS_CEPT_REG2", "REGRESSION_PHASE2", "Advance | Bank GL | TDS | Clear error then partial edit", "ADVANCE_WITH_TDS"));
        out.add(apRow("DB_ADV_TDS_NO_NATURE", "REGRESSION_PHASE1", "Advance | Bank GL | TDS | No nature tagged", "ADVANCE_WITH_TDS_NO_NATURE"));
        out.add(apRow("DB_ADV_TDS_SINGLE_NATURE", "REGRESSION_PHASE1", "Advance | Bank GL | TDS | Single nature tagged", "ADVANCE_WITH_TDS_SINGLE_NATURE"));
        for (ApVoucherScenarioType s : new ApVoucherScenarioType[]{
                ApVoucherScenarioType.NEG_BLANK_CASHBANK, ApVoucherScenarioType.NEG_BLANK_CASHBANK_ADD_ROW,
                ApVoucherScenarioType.NEG_BLANK_CASHBANK_ADD_ROW_RECOVER, ApVoucherScenarioType.NEG_BLANK_SUBLEDGER,
                ApVoucherScenarioType.NEG_BLANK_SUBLEDGER_RECOVER, ApVoucherScenarioType.BLANK_REFERENCE_ALLOWED,
                ApVoucherScenarioType.BOUND_AMOUNT_MIN_VALID, ApVoucherScenarioType.BOUND_AMOUNT_MIN_INVALID,
                ApVoucherScenarioType.DOC_AMOUNT_NOT_ADJUSTED_INVALID, ApVoucherScenarioType.DOC_AMOUNT_READ_ONLY}) {
            out.add(apRow("DB_" + s.name(), "REGRESSION_PHASE1", s.name() + " (negative / boundary)", s.name()));
        }
        out.add(apRow("DB_AP_NO_ADJ_CASH", "REGRESSION_PHASE1", "No adjustment | Cash GL", "DOC_NO_ADJUSTMENT"));
        out.add(apRow("DB_AP_NO_ADJ_BANK_USER", "REGRESSION_PHASE1", "No adjustment | Bank GL | User SL", "DOC_NO_ADJUSTMENT"));
        for (String base : new String[]{"DB_AP_FIFO", "DB_AP_REF_FIFO"}) {
            String scenario = "DB_AP_FIFO".equals(base) ? "DOC_FIFO" : "DOC_REFERENCE_SITEWISE_FIFO";
            out.add(apRow(base + "_BANK_SYSTEM", "MANDATORY", scenario + " | Bank GL | System SL", scenario));
            out.add(apRow(base + "_CASH", "REGRESSION_PHASE1", scenario + " | Cash GL", scenario));
            out.add(apRow(base + "_BANK_USER", "REGRESSION_PHASE1", scenario + " | Bank GL | User SL", scenario));
            out.add(apRow(base + "_CASH_PART", "REGRESSION_PHASE2", scenario + " | Cash GL | Partial", scenario));
            out.add(apRow(base + "_CASH_MULTI_DOC", "REGRESSION_PHASE2", scenario + " | Cash GL | 1 SL, 3 docs", scenario));
            out.add(apRow(base + "_CASH_MULTI_SL", "REGRESSION_PHASE2", scenario + " | Cash GL | 3 SL, 1 doc", scenario));
        }
        // Amount-mismatch Confirmation scenarios — tiers per DbApVoucherDataSource.mismatchTier.
        out.add(apRow("DB_DOC_PARTIAL_UPDATE_PAYMENT_SELECT_DOC", "MANDATORY", "Partial adjustment, Update payment (Select Document)", "DOC_PARTIAL_UPDATE_PAYMENT_SELECT_DOC"));
        out.add(apRow("DB_DOC_PARTIAL_ADJUST_MORE_SELECT_DOC", "REGRESSION_PHASE1", "Partial adjustment, Adjust more (Select Document)", "DOC_PARTIAL_ADJUST_MORE_SELECT_DOC"));
        out.add(apRow("DB_DOC_PARTIAL_MULTI_DOC_UPDATE_PAYMENT", "REGRESSION_PHASE1", "Partial adjustment across several documents, Update payment", "DOC_PARTIAL_MULTI_DOC_UPDATE_PAYMENT"));
        out.add(apRow("DB_DOC_PARTIAL_PER_DOC_AMOUNT_LESS_THAN_PENDING", "REGRESSION_PHASE1", "Per-document amount less than pending", "DOC_PARTIAL_PER_DOC_AMOUNT_LESS_THAN_PENDING"));
        out.add(apRow("DB_DOC_PARTIAL_ADJUST_MORE_FIFO", "REGRESSION_PHASE2", "Partial adjustment, Adjust more (FIFO)", "DOC_PARTIAL_ADJUST_MORE_FIFO"));
        out.add(apRow("DB_DOC_PARTIAL_ADJUST_MORE_REFSITE_FIFO", "REGRESSION_PHASE2", "Partial adjustment, Adjust more (Reference-sitewise FIFO)", "DOC_PARTIAL_ADJUST_MORE_REFSITE_FIFO"));
        out.add(apRow("DB_DOC_PARTIAL_ADJUST_MORE_THEN_UPDATE", "REGRESSION_PHASE2", "Partial adjustment, Adjust more then Update payment", "DOC_PARTIAL_ADJUST_MORE_THEN_UPDATE"));
        out.add(apRow("DB_DOC_AMOUNT_OVER_PENDING_NEGATIVE", "REGRESSION_PHASE3", "Negative: document amount over pending", "DOC_AMOUNT_OVER_PENDING_NEGATIVE"));
        out.add(CoverageRow.builder().module("AP").flow("Create").variantId("DOC_SELECT_DOCUMENT")
                .group("VARIES").scenarioType("DOC_SELECT_DOCUMENT").dbExpanded(true)
                .description("Classification-type-driven Select-Document variants - expanded from the DB at run time; see Results")
                .build());

        // Edit — groups per DbApVoucherEditDataSource.load
        out.add(apEditRow("AP_EDIT_HEADER_UNTAGGED", "FIN-AP-EDIT-HEADER-01", "MANDATORY", "Header edit, UNTAGGED voucher", "HEADER_ONLY"));
        out.add(apEditRow("AP_EDIT_HEADER_TAGGED", "FIN-AP-EDIT-HEADER-02", "MANDATORY", "Header edit, TAGGED voucher", "HEADER_ONLY"));
        out.add(apEditRow("DETAIL_MULTI_DOC_RESTORE", null, "MANDATORY", "Detail edit, document mode: restore every tag (no popup)", "DETAIL_MULTI_DOC_RESTORE"));
        out.add(apEditRow("DETAIL_AMOUNT_DOC_DECREASE_UPDATE_PAYMENT", null, "MANDATORY", "Detail edit, document mode: decrease amount then Update payment", "DETAIL_AMOUNT_DOC_DECREASE_UPDATE_PAYMENT"));
        out.add(apEditRow("DETAIL_AMOUNT_ADVANCE", null, "MANDATORY", "Detail edit, advance mode: plain amount edit", "DETAIL_AMOUNT_ADVANCE"));
        out.add(apEditRow("DETAIL_AMOUNT_DOC_DECREASE_ADJUST_MORE", null, "REGRESSION_PHASE1", "Detail edit, document mode: decrease amount then Adjust more", "DETAIL_AMOUNT_DOC_DECREASE_ADJUST_MORE"));
        out.add(apEditRow("DETAIL_AMOUNT_DOC_INCREASE_WITHIN_PENDING", null, "REGRESSION_PHASE2", "Detail edit, document mode: increase amount within pending", "DETAIL_AMOUNT_DOC_INCREASE_WITHIN_PENDING"));
        out.add(apEditRow("DETAIL_AMOUNT_DOC_OVER_PENDING_NEGATIVE", null, "REGRESSION_PHASE3", "Negative: detail edit, document mode, amount increased over pending", "DETAIL_AMOUNT_DOC_OVER_PENDING_NEGATIVE"));
        out.add(apEditRow("DETAIL_PAYMENT_INFO", "FIN-AP-EDIT-PAYMENT-INFO-01", "MANDATORY", "Payment Info change only", "DETAIL_PAYMENT_INFO"));
        out.add(apEditRow("AP_EDIT_DATE_FIELD_DISABLED", "FIN-AP-EDIT-DATE-DISABLED-01", "REGRESSION_PHASE1", "Negative/UI: Date field is disabled on Edit (business rule)", "HEADER_ONLY"));

        // Revert / Delete — MANDATORY UNTAGGED + TAGGED, no variant id on the executed rows.
        out.add(apTagStateRow("Revert", "UNTAGGED", "FIN-AP-REVERT-01"));
        out.add(apTagStateRow("Revert", "TAGGED", "FIN-AP-REVERT-02"));
        out.add(apTagStateRow("Delete", "UNTAGGED", "FIN-AP-DELETE-01"));
        out.add(apTagStateRow("Delete", "TAGGED", "FIN-AP-DELETE-02"));
        return out;
    }

    private static CoverageRow apRow(String id, String group, String description, String scenarioType) {
        return CoverageRow.builder().module("AP").flow("Create").variantId(id).group(group)
                .description(description).scenarioType(scenarioType).build();
    }

    private static CoverageRow apEditRow(String id, String testCaseId, String group, String description, String scenarioType) {
        return CoverageRow.builder().module("AP").flow("Edit").variantId(id).testCaseId(testCaseId).group(group)
                .description(description).scenarioType(scenarioType).build();
    }

    private static CoverageRow apTagStateRow(String flow, String tagState, String testCaseId) {
        return CoverageRow.builder().module("AP").flow(flow).variantId("AP_" + flow.toUpperCase() + "_" + tagState)
                .testCaseId(testCaseId).group("MANDATORY")
                .description(flow + " a Posted AP voucher, " + tagState + " (adjustment tag state)")
                .scenarioType(tagState).build();
    }
}
