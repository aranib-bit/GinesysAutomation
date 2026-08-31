package com.gsl.reports;

import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.dto.GeneralVoucherOperationMode;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditVariant;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.NegativeValidation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds a human-readable English sentence describing the General Voucher test scenario.
 *
 * <p>Unlike {@link ArVoucherDescriptionBuilder}/{@code ApVoucherDescriptionBuilder}, GV has no
 * compact {@code " | "}-token {@code testcasename} convention to parse for its combination matrix
 * — instead {@link #buildCombinationSentence} builds the sentence directly from
 * {@link GeneralVoucherFixtureOptions}, which (unlike {@code expenseIncomeDetailList}) is already
 * populated at placeholder time, before any DB load — so it's available in time to win
 * {@code TestListener}'s title-priority check (sentence beats richName beats the static
 * {@code @Test} description; see {@code DbGeneralVoucherDataSource}'s "Root cause #3").
 *
 * <p>The sentence's verb ("created" vs. "reverted") is derived from whether
 * {@code data.getExistingVoucherNo()} is non-null — that field is populated only by Revert data
 * sources, never by Create — so the same builder correctly describes both {@code
 * createGeneralVoucher} and {@code revertGeneralVoucher} test runs without any new DTO field.
 */
public final class GeneralVoucherDescriptionBuilder {

    private GeneralVoucherDescriptionBuilder() {}

    /**
     * Returns a descriptive sentence for the given test data, or {@code null} if
     * the data does not contain enough information to build one.
     */
    public static String build(MergedFinanceGeneralVoucherData data) {
        if (data == null) return null;

        if (data.getExpectedValidation() != null) {
            String negDesc = buildNegativeDescription(data.getExpectedValidation());
            if (negDesc != null) return negDesc;
        }

        // Closed-period negative scenarios (Revert/Edit/Delete all deliberately claim a candidate
        // whose own accounting year/financial month is closed — see the "GV Revert picks
        // documents in a now-closed accounting period, + closed-period negative-test coverage"
        // plan) never reach a positive outcome at all, so describe the rejection being verified
        // instead of falling through to the normal action-verb sentence below.
        if (data.getExpectedRevertOutcome() != null) {
            String actionLabel = switch (data.getOperationMode()) {
                case EDIT -> "editing (Unpost prerequisite)";
                case DELETE -> "deleting (Unpost prerequisite)";
                default -> "reverting";
            };
            return "Verify that " + actionLabel + " a General Voucher whose own accounting year or "
                    + "financial month is already closed is correctly REJECTED by the application.";
        }

        // Checked BEFORE the isRevert fallback below, since Edit also sets existingVoucherNo —
        // without this, an Edit run would be mislabeled "reverted successfully" by the old
        // existingVoucherNo-based inference (see GeneralVoucherOperationMode's Javadoc).
        if (data.getOperationMode() == GeneralVoucherOperationMode.EDIT) {
            if (data.getEditedDetailLine() != null) {
                return "Verify that a General Voucher is edited successfully with an updated "
                        + "Expense/Income Detail line (LineNo '" + data.getEditedDetailLine().getLineNo() + "')"
                        + buildDetailLineEditSummary(data.getEditedDetailLine()) + ".";
            }
            // Plan §6/§9b: state the actual isolated field AND its precise old→new value for a
            // HeaderFieldScope-scoped variant, instead of always naming all three fields
            // regardless of what really changed (4D) or naming the field with no value at all
            // (the report-review "how will a user understand what has changed" finding) — mirrors
            // buildDetailLineEditSummary's own precise before→after phrasing below.
            return "Verify that a General Voucher is edited successfully with an updated "
                    + headerFieldScopePhrase(data) + ".";
        }

        if (data.getOperationMode() == GeneralVoucherOperationMode.DELETE) {
            return "Verify that an existing General Voucher (" + data.getExistingVoucherNo()
                    + ") is deleted successfully.";
        }

        // Revert data sources populate existingVoucherNo (and only Revert does — see that
        // field's Javadoc); Create leaves it null. Used to swap the sentence's verb so a Revert
        // test's report title doesn't claim the voucher was "created successfully".
        boolean isRevert = data.getExistingVoucherNo() != null;
        String actionVerb = isRevert ? "reverted" : "created";

        if (data.getScenarioType() == GeneralVoucherScenarioType.MIXED_TYPES) {
            return "Verify that a General Voucher is " + actionVerb + " successfully with entries across "
                    + "multiple ledger types (Asset/Liability/Income/Expense, Type1/2/3) in a single voucher.";
        }

        if (data.getFixtureOptions() != null) {
            String sentence = buildCombinationSentence(data.getFixtureOptions(), actionVerb);
            if (sentence != null) return sentence;
        }

        if (data.getExpenseIncomeDetailList() == null || data.getExpenseIncomeDetailList().isEmpty())
            return null;

        return "Verify that a General Voucher is " + actionVerb + " successfully with "
                + data.getExpenseIncomeDetailList().size() + " Expense/Income line(s).";
    }

    /**
     * Builds the combination-matrix sentence from the placeholder-time-available
     * {@code fixtureOptions} — states the ledger type (account type + GL line type), the
     * GL-count/Site-count breakup (see {@code GeneralVoucherCombinations}'s class Javadoc), the
     * debit/credit side, reversal, and payment variant. Mirrors AP/AR's own token-to-sentence
     * phrasing style, e.g. "Verify that an AP Voucher is created successfully using Bank Ledger
     * ... with Select Document Adjustment (Full Amount) and NONE Payment."
     */
    private static String buildCombinationSentence(GeneralVoucherFixtureOptions o, String actionVerb) {
        if (o.getAccountType() == null || o.getGlLineType() == null || o.getPaymentVariant() == null) return null;

        String glTypePhrase = switch (o.getGlLineType()) {
            case TYPE1_NONE -> "no Site or Sub Ledger applicability";
            case TYPE2_SITE_ONLY -> "Site allocation only";
            case TYPE3_SITE_AND_SUBLEDGER -> "Site allocation and a Sub Ledger";
        };
        String paymentPhrase = switch (o.getPaymentVariant()) {
            case CHEQUE -> "Cheque payment";
            case NEFT_RTGS -> "NEFT/RTGS payment";
            case NONE -> "no Payment info (instrument-missing warning expected)";
            case CASH -> "Cash payment";
        };

        int glCount = Math.max(1, o.getGlCount());
        int siteCount = Math.max(1, o.getSiteCount());

        String glPhrase = glCount == 1
                ? "a " + o.getAccountType().label() + " GL"
                : glCount + " " + o.getAccountType().label() + " GLs";
        String sitePhrase = o.getGlLineType() == GlLineType.TYPE1_NONE
                ? ""
                : siteCount == 1
                        ? ", allocated to 1 Site"
                        : ", each allocated across " + siteCount + " unique Sites";

        return "Verify that a General Voucher is " + actionVerb + " successfully using " + glPhrase + " with "
                + glTypePhrase + sitePhrase + ", a " + (o.isDebitSide() ? "Debit" : "Credit") + " line, "
                + (o.isReversal() ? "with Reversal" : "no Reversal") + ", and " + paymentPhrase + ".";
    }

    /**
     * Plan §6/§9b: names the one field a {@code HeaderFieldScope}-scoped variant actually changed
     * AND its precise old→new value (report-review finding: naming the field alone still left "a
     * user unable to understand what has changed" for a report title) — mirrors how
     * {@link #buildDetailLineEditSummary} already embeds precise before→after values for a
     * detail-line edit. Old values come from {@link MergedFinanceGeneralVoucherData
     * #getOriginalReference()}/{@code #getOriginalRemarks()}/{@code #getCurrentDocDate()} — all
     * {@code null} only for a variant built before those fields existed, or for Create/Revert
     * (unreachable here — this method is only ever called for an {@code EDIT} with no edited
     * detail line). {@code ALL} states all three; a single-field scope states only its own field.
     */
    private static String headerFieldScopePhrase(MergedFinanceGeneralVoucherData data) {
        GeneralVoucherEditVariant.HeaderFieldScope scope = data.getHeaderFieldScope() != null
                ? data.getHeaderFieldScope() : GeneralVoucherEditVariant.HeaderFieldScope.ALL;
        var doc = data.getDocumentInformation();
        String oldRef = data.getOriginalReference();
        String newRef = doc != null ? doc.getReference() : null;
        String oldRemarks = data.getOriginalRemarks();
        String newRemarks = doc != null ? doc.getRemarks() : null;
        String oldDate = data.getCurrentDocDate() != null ? data.getCurrentDocDate().format(VoucherReportUtils.DATE_FMT) : null;
        String newDate = doc != null && doc.getDocumentdate() != null
                ? doc.getDocumentdate().format(VoucherReportUtils.DATE_FMT) : null;

        return switch (scope) {
            case REFERENCE_ONLY -> "Reference ('" + oldRef + "' → '" + newRef + "')";
            case REMARKS_ONLY   -> "Remarks ('" + oldRemarks + "' → '" + newRemarks + "')";
            case DATE_ONLY      -> "Date (" + oldDate + " → " + newDate + ")";
            case ALL            -> "document header (Reference: '" + oldRef + "' → '" + newRef
                    + "', Remarks: '" + oldRemarks + "' → '" + newRemarks
                    + "', Date: " + oldDate + " → " + newDate + ")";
        };
    }

    /**
     * Plan §4A (fixed in §8 — the original version only ever stated the amount change inside the
     * {@code if (siteApplicable)} block, so a {@code TYPE1_NONE} line's summary said nothing
     * about what actually changed at all): states the GL before/after (name and, when resolved,
     * account class), Sub Ledger applicable Y/N, Site applicable Y/N, and — ALWAYS, regardless of
     * site-applicability — the amount before→after, since that's the change itself, not
     * structural context. {@code SITE_SPLIT_ONLY} is the one shape where the amount deliberately
     * doesn't move, so it gets its own clause instead of the amount diff.
     * {@link DetailLineEditTarget#getBeforeRow()} is {@code null} only for {@code ADD_NEW_LINE},
     * which gets its own "(new line added: ...)" phrasing since there is no before side to diff.
     */
    private static String buildDetailLineEditSummary(DetailLineEditTarget target) {
        ExpenseIncomeDetail after = target.getNewValues();
        ExpenseIncomeDetail before = target.getBeforeRow();

        if (before == null) {
            return " (new line added: GL '" + glLabel(after) + "')";
        }

        List<String> clauses = new ArrayList<>();

        boolean glChanged = !Objects.equals(before.getGeneralLedger(), after.getGeneralLedger());
        clauses.add(glChanged
                ? "GL changed from '" + glLabel(before) + "' to '" + glLabel(after) + "'"
                : "GL unchanged: '" + glLabel(after) + "'");

        boolean subLedgerApplicable = after.getResolvedGlType() != null && after.getResolvedGlType().isSubLedgerApplicable();
        boolean siteApplicable = after.getResolvedGlType() != null && after.getResolvedGlType().isSiteApplicable();
        clauses.add("Sub Ledger applicable: " + (subLedgerApplicable ? "Yes" : "No"));
        clauses.add("Site applicable: " + (siteApplicable ? "Yes" : "No"));

        boolean splitOnly = target.getShape() == DetailLineEditTarget.EditShape.SITE_SPLIT_ONLY;
        if (siteApplicable && splitOnly) {
            clauses.add("same total amount — only the sitewise allocation split changed");
        } else {
            BigDecimal beforeAmt = before.isDebit() ? before.getExpenseDr() : before.getIncomeCr();
            BigDecimal afterAmt  = after.isDebit()  ? after.getExpenseDr()  : after.getIncomeCr();
            String label = after.isDebit() ? "Expense(Dr)" : "Income(Cr)";
            boolean amtChanged = beforeAmt == null ? afterAmt != null : beforeAmt.compareTo(afterAmt) != 0;
            clauses.add(label + (amtChanged
                    ? ": " + VoucherReportUtils.fmtAmount(beforeAmt) + " → " + VoucherReportUtils.fmtAmount(afterAmt)
                    : " unchanged (" + VoucherReportUtils.fmtAmount(afterAmt) + ")"));
        }

        if (before.isReversalYes() != after.isReversalYes()) {
            clauses.add("Is Reversal: " + (before.isReversalYes() ? "YES" : "NO")
                    + " → " + (after.isReversalYes() ? "YES" : "NO"));
        }
        if (!Objects.equals(before.getLedgerNarration(), after.getLedgerNarration())) {
            clauses.add("Ledger Narration changed");
        }

        return " (" + String.join(", ", clauses) + ")";
    }

    private static String glLabel(ExpenseIncomeDetail row) {
        return row.getGeneralLedger() + (row.getResolvedAccountClass() != null
                ? " [" + row.getResolvedAccountClass().label() + "]" : "");
    }

    private static String buildNegativeDescription(NegativeValidation expected) {
        return switch (expected) {
            case GV_SITE_ALLOCATION_PENDING -> "Verify that saving a Site-applicable line with an "
                    + "incomplete site allocation shows the site-allocation-pending validation error.";
            case GV_SUBLEDGER_REQUIRED -> "Verify that clicking Site on a Sub-Ledger-applicable row "
                    + "with no Sub Ledger selected shows the sub-ledger-required validation error.";
            case GV_SITE_NOT_ALLOCATED -> "Verify that saving a Site-applicable line whose Site was "
                    + "never allocated shows the site-not-allocated validation error.";
            default -> null;
        };
    }
}
