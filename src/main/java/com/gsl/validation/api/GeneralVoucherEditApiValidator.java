package com.gsl.validation.api;

import com.gsl.api.model.generalvoucher.CostCenterAllocation;
import com.gsl.api.model.generalvoucher.GeneralVoucherDetailLine;
import com.gsl.api.model.generalvoucher.GeneralVoucherRootView;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditVariant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The {@code db.available=no} counterpart of the aud2 snapshot diff for General Voucher Edit: compares the voucher's {@code rootview} from
 * BEFORE the edit with AFTER it and checks that exactly what the variant set out to change changed — and nothing else. A header edit changes
 * only the header fields its scope names; a detail-line edit changes only the targeted line (amount / narration / reversal / GL, a new line,
 * or the site split); a payment edit changes only the first line's payment mode and instrument. Everything not expected to change is compared
 * for equality, so an accidental side effect fails the row.
 *
 * <p>No database, no browser: it reads two {@link GeneralVoucherRootView}s and the row's expected-change data.
 */
public final class GeneralVoucherEditApiValidator {

    private GeneralVoucherEditApiValidator() {}

    /** Every check that ran, split by outcome — the caller renders both and fails the step when {@code failed} is non-empty. */
    public record Result(List<String> passed, List<String> failed) {
        public boolean ok() { return failed.isEmpty(); }
    }

    public static Result validate(MergedFinanceGeneralVoucherData data, GeneralVoucherEditVariant.ScenarioType scenario,
                                  GeneralVoucherRootView before, GeneralVoucherRootView after) {
        Checks c = new Checks();
        DocumentInformation doc = data.getDocumentInformation();

        // Header: the assembler stores the intended values — unchanged ones equal the originals — so one comparison covers every scope.
        c.eq("Document date", doc.getDocumentdate(), date(after.getVoucherDate()));
        c.eq("Reference", norm(doc.getReference()), norm(after.getReference()));
        c.eq("Remarks", norm(doc.getRemarks()), norm(after.getRemarks()));
        c.eq("Owner site", before.getOwnerSite() == null ? null : before.getOwnerSite().getCode(),
                after.getOwnerSite() == null ? null : after.getOwnerSite().getCode());
        c.eq("Cash/Bank ledger", before.getCashBankLedger() == null ? null : before.getCashBankLedger().getGlCode(),
                after.getCashBankLedger() == null ? null : after.getCashBankLedger().getGlCode());

        List<GeneralVoucherDetailLine> b = nz(before.getExpenseIncomeDetail());
        List<GeneralVoucherDetailLine> a = nz(after.getExpenseIncomeDetail());

        DetailLineEditTarget detail = scenario == GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE ? data.getEditedDetailLine() : null;
        boolean addsLine = detail != null && detail.getShape() == DetailLineEditTarget.EditShape.ADD_NEW_LINE;
        c.eq("Number of lines", b.size() + (addsLine ? 1 : 0), a.size());

        int editedIdx = detail == null || addsLine ? -1 : Integer.parseInt(detail.getLineNo()) - 1;
        int common = Math.min(b.size(), a.size());
        for (int i = 0; i < common; i++) {
            String label = "Line " + (i + 1);
            boolean paymentEdited = scenario == GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO && i == 0;
            if (i == editedIdx) {
                expectEditedLine(c, label, detail, b.get(i), a.get(i));
            } else {
                sameLine(c, label, b.get(i), a.get(i), paymentEdited);
            }
        }
        if (addsLine && a.size() == b.size() + 1) {
            ExpenseIncomeDetail nv = detail.getNewValues();
            GeneralVoucherDetailLine added = a.get(a.size() - 1);
            c.eq("Added line GL", nv.getGeneralLedger(), added.getGlName());
            c.money("Added line amount", amountOf(nv), amountOf(added));
            c.eq("Added line narration", norm(nv.getLedgerNarration()), norm(added.getLedgerNarration()));
        }

        if (scenario == GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO && data.getPaymentEditTarget() != null && !a.isEmpty()) {
            PaymentInformation np = data.getPaymentEditTarget().getNewPayment();
            GeneralVoucherDetailLine first = a.get(0);
            c.eq("Payment mode", np.getPaymentMode().dbCode(), first.getPaymentMode());
            c.eq("Instrument number", norm(np.getInstrumentNo()), norm(first.getInstrumentNumber()));
        }
        return new Result(c.passed, c.failed);
    }

    private static void expectEditedLine(Checks c, String label, DetailLineEditTarget target, GeneralVoucherDetailLine before,
                                         GeneralVoucherDetailLine after) {
        ExpenseIncomeDetail nv = target.getNewValues();
        c.eq(label + " GL", nv.getGeneralLedger(), after.getGlName());
        c.money(label + " amount", amountOf(nv), amountOf(after));
        c.eq(label + " debit/credit side", nv.isDebit(), after.getExpense() != 0);
        c.eq(label + " narration", norm(nv.getLedgerNarration()), norm(after.getLedgerNarration()));
        c.eq(label + " is-reversal", nv.isReversalYes(), "Y".equalsIgnoreCase(after.getIsReversal()));
        if (target.getShape() == DetailLineEditTarget.EditShape.SITE_SPLIT_ONLY) {
            c.eq(label + " site allocation", expectedAllocations(target.getNewSiteAllocations()),
                    actualAllocations(after.getCostCenterAllocations()));
        } else if (target.getShape() != DetailLineEditTarget.EditShape.GL_TYPE_TRANSITION) {
            c.eq(label + " site allocation unchanged", actualAllocations(before.getCostCenterAllocations()),
                    actualAllocations(after.getCostCenterAllocations()));
        }
    }

    /** An untouched line must be identical before and after (payment fields excluded on the line a payment edit targets). */
    private static void sameLine(Checks c, String label, GeneralVoucherDetailLine b, GeneralVoucherDetailLine a, boolean paymentEdited) {
        c.eq(label + " GL", b.getGlCode(), a.getGlCode());
        c.eq(label + " sub-ledger", b.getSlCode() == null ? 0L : b.getSlCode(), a.getSlCode() == null ? 0L : a.getSlCode());
        c.money(label + " amount", amountOf(b), amountOf(a));
        c.eq(label + " debit/credit side", b.getExpense() != 0, a.getExpense() != 0);
        c.eq(label + " narration", norm(b.getLedgerNarration()), norm(a.getLedgerNarration()));
        c.eq(label + " is-reversal", "Y".equalsIgnoreCase(b.getIsReversal()), "Y".equalsIgnoreCase(a.getIsReversal()));
        c.eq(label + " site allocation", actualAllocations(b.getCostCenterAllocations()), actualAllocations(a.getCostCenterAllocations()));
        if (!paymentEdited) {
            c.eq(label + " payment mode", b.getPaymentMode(), a.getPaymentMode());
            c.eq(label + " instrument number", norm(b.getInstrumentNumber()), norm(a.getInstrumentNumber()));
        }
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private static BigDecimal amountOf(ExpenseIncomeDetail d) {
        return d.isDebit() ? d.getExpenseDr() : d.getIncomeCr();
    }

    private static BigDecimal amountOf(GeneralVoucherDetailLine l) {
        return BigDecimal.valueOf(l.getExpense() != 0 ? l.getExpense() : l.getIncome());
    }

    private static Map<String, BigDecimal> expectedAllocations(List<SiteAllocation> list) {
        Map<String, BigDecimal> m = new TreeMap<>();
        if (list != null) for (SiteAllocation s : list) m.merge(s.getSiteName().trim(), s.getAmount().stripTrailingZeros(), BigDecimal::add);
        return m;
    }

    private static Map<String, BigDecimal> actualAllocations(List<CostCenterAllocation> list) {
        Map<String, BigDecimal> m = new TreeMap<>();
        if (list != null) {
            for (CostCenterAllocation s : list) {
                m.merge(s.getName().trim(), BigDecimal.valueOf(s.getAmount()).stripTrailingZeros(), BigDecimal::add);
            }
        }
        return m;
    }

    private static LocalDate date(String iso) {
        return iso == null || iso.length() < 10 ? null : LocalDate.parse(iso.substring(0, 10));
    }

    private static String norm(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <T> List<T> nz(List<T> l) {
        return l == null ? List.of() : l;
    }

    /** Accumulates pass/fail lines instead of throwing, so one run reports every discrepancy. */
    private static final class Checks {
        final List<String> passed = new ArrayList<>();
        final List<String> failed = new ArrayList<>();

        void eq(String what, Object expected, Object actual) {
            if (Objects.equals(expected, actual)) passed.add(what + " = " + show(actual));
            else failed.add(what + ": expected " + show(expected) + " but was " + show(actual));
        }

        void money(String what, BigDecimal expected, BigDecimal actual) {
            if (expected != null && actual != null && expected.compareTo(actual) == 0) {
                passed.add(what + " = " + actual.stripTrailingZeros().toPlainString());
            } else {
                failed.add(what + ": expected " + show(expected) + " but was " + show(actual));
            }
        }

        private static String show(Object o) {
            return o == null ? "(none)" : o instanceof BigDecimal bd ? bd.stripTrailingZeros().toPlainString() : "'" + o + "'";
        }
    }
}
