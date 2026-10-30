package com.gsl.validation.api;

import com.gsl.api.model.generalvoucher.CostCenterAllocation;
import com.gsl.api.model.generalvoucher.GeneralVoucherDetailLine;
import com.gsl.api.model.generalvoucher.GeneralVoucherRootView;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * {@code db.available=no} checks for the General Voucher Create, Revert and Delete tests, all of them reading the voucher back through the
 * API ({@code rootview}) instead of the database:
 * <ul>
 *   <li>{@link #created} — a newly created voucher holds exactly what the test entered (header, every line, site allocations, payment);</li>
 *   <li>{@link #statusChangedOnly} — Revert: the voucher ended at the expected release status and nothing else about it changed;</li>
 *   <li>{@link #deleted} — Delete: the voucher can no longer be read or listed.</li>
 * </ul>
 * Checks accumulate into a {@link Result} (never throw), so one run reports every discrepancy. The Edit counterpart is
 * {@link GeneralVoucherEditApiValidator}.
 */
public final class GeneralVoucherApiStateChecks {

    private GeneralVoucherApiStateChecks() {}

    /** Every check that ran, split by outcome — the caller renders both and fails the step when {@code failed} is non-empty. */
    public record Result(List<String> passed, List<String> failed, List<ApiCheckRow> rows, java.util.Map<String, String> titles) {
        public boolean ok() { return failed.isEmpty(); }
    }

    // ───────────────────────────── Create ─────────────────────────────

    public static Result created(MergedFinanceGeneralVoucherData data, GeneralVoucherRootView actual) {
        ApiChecks c = new ApiChecks();
        if (actual == null) {
            c.fail("The created voucher could not be read back through the API");
            return new Result(c.passed, c.failed, c.rows, c.titles);
        }
        DocumentInformation doc = data.getDocumentInformation();
        if (doc != null) {
            c.eq("Document date", doc.getDocumentdate(), date(actual.getVoucherDate()));
            c.eq("Reference", norm(doc.getReference()), norm(actual.getReference()));
            c.eq("Remarks", norm(doc.getRemarks()), norm(actual.getRemarks()));
            if (doc.getCashbank() != null && actual.getCashBankLedger() != null) {
                c.eq("Cash/Bank ledger", doc.getCashbank().trim(), actual.getCashBankLedger().getGlName() == null ? null
                        : actual.getCashBankLedger().getGlName().trim());
            }
            if (doc.getOwnersite() != null && actual.getOwnerSite() != null) {
                c.eq("Owner site", doc.getOwnersite().trim(), actual.getOwnerSite().getName() == null ? null : actual.getOwnerSite().getName().trim());
            }
        }

        List<ExpenseIncomeDetail> expected = data.getExpenseIncomeDetailList() == null ? List.of() : data.getExpenseIncomeDetailList();
        List<GeneralVoucherDetailLine> lines = actual.getExpenseIncomeDetail() == null ? List.of() : actual.getExpenseIncomeDetail();
        c.eq("Number of lines", expected.size(), lines.size());
        for (int i = 0; i < Math.min(expected.size(), lines.size()); i++) {
            ExpenseIncomeDetail e = expected.get(i);
            GeneralVoucherDetailLine l = lines.get(i);
            String label = "Line " + (i + 1);
            c.title("Row " + (i + 1), ApiLineTitles.fromExpected(i, e, data.getSiteAllocationsForLine(e.getLineNo()), paymentFor(data, e.getLineNo(), i)));
            c.eq(label + " GL", trim(e.getGeneralLedger()), trim(l.getGlName()));
            if (norm(e.getSubLedger()) != null) c.eq(label + " sub-ledger", trim(e.getSubLedger()), trim(l.getSlName()));
            BigDecimal expAmount = e.isDebit() ? e.getExpenseDr() : e.getIncomeCr();
            c.money(label + " amount", expAmount, BigDecimal.valueOf(l.getExpense() != 0 ? l.getExpense() : l.getIncome()));
            c.eq(label + " debit/credit side", e.isDebit(), l.getExpense() != 0);
            if (norm(e.getLedgerNarration()) != null) c.eq(label + " narration", norm(e.getLedgerNarration()), norm(l.getLedgerNarration()));
            c.eq(label + " is-reversal", e.isReversalYes(), "Y".equalsIgnoreCase(l.getIsReversal()));

            List<SiteAllocation> expAlloc = data.getSiteAllocationsForLine(e.getLineNo());
            if (!expAlloc.isEmpty()) {
                c.eq(label + " site allocation", expectedAllocations(expAlloc), allocationsOf(l.getCostCenterAllocations()));
            }
            PaymentInformation pay = paymentFor(data, e.getLineNo(), i);
            if (pay != null && pay.getPaymentMode() != null) {
                c.eq(label + " payment mode", pay.getPaymentMode().dbCode(), l.getPaymentMode());
                if (norm(pay.getInstrumentNo()) != null) c.eq(label + " instrument number", norm(pay.getInstrumentNo()), norm(l.getInstrumentNumber()));
            }
        }
        return new Result(c.passed, c.failed, c.rows, c.titles);
    }

    private static PaymentInformation paymentFor(MergedFinanceGeneralVoucherData data, String lineNo, int index) {
        if (data.getRowPayments() != null) {
            for (PaymentInformation p : data.getRowPayments()) {
                if (p != null && Objects.equals(p.getLineNo(), lineNo)) return p;
            }
            return null;
        }
        return index == 0 ? data.getCashBankPaymentInfo() : null;
    }

    // ───────────────────────────── Revert ─────────────────────────────

    /** The voucher's release status is {@code expectedStatus} ({@code "P"} or {@code "U"}) and everything else about it equals {@code before}. */
    public static Result statusChangedOnly(GeneralVoucherRootView before, GeneralVoucherRootView after, String expectedStatus) {
        ApiChecks c = new ApiChecks();
        if (before == null || after == null) {
            c.fail("rootview unavailable (before=" + (before != null) + ", after=" + (after != null) + ")");
            return new Result(c.passed, c.failed, c.rows, c.titles);
        }
        c.eq("Release status", expectedStatus, after.isPosted() ? "P" : "U");
        c.eq("Document date", date(before.getVoucherDate()), date(after.getVoucherDate()));
        c.eq("Reference", norm(before.getReference()), norm(after.getReference()));
        c.eq("Remarks", norm(before.getRemarks()), norm(after.getRemarks()));
        c.eq("Cash/Bank ledger", before.getCashBankLedger() == null ? null : before.getCashBankLedger().getGlCode(),
                after.getCashBankLedger() == null ? null : after.getCashBankLedger().getGlCode());
        List<GeneralVoucherDetailLine> b = before.getExpenseIncomeDetail() == null ? List.of() : before.getExpenseIncomeDetail();
        List<GeneralVoucherDetailLine> a = after.getExpenseIncomeDetail() == null ? List.of() : after.getExpenseIncomeDetail();
        c.eq("Number of lines", b.size(), a.size());
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            String label = "Line " + (i + 1);
            c.title("Row " + (i + 1), ApiLineTitles.fromLine(i, a.get(i)));
            c.eq(label + " GL", b.get(i).getGlCode(), a.get(i).getGlCode());
            c.money(label + " amount", BigDecimal.valueOf(b.get(i).getExpense() + b.get(i).getIncome()),
                    BigDecimal.valueOf(a.get(i).getExpense() + a.get(i).getIncome()));
            c.eq(label + " narration", norm(b.get(i).getLedgerNarration()), norm(a.get(i).getLedgerNarration()));
            c.eq(label + " payment mode", b.get(i).getPaymentMode(), a.get(i).getPaymentMode());
            c.eq(label + " instrument number", norm(b.get(i).getInstrumentNumber()), norm(a.get(i).getInstrumentNumber()));
            c.eq(label + " site allocation", allocationsOf(b.get(i).getCostCenterAllocations()), allocationsOf(a.get(i).getCostCenterAllocations()));
        }
        return new Result(c.passed, c.failed, c.rows, c.titles);
    }

    // ───────────────────────────── Delete ─────────────────────────────

    /** The voucher can no longer be read ({@code rootviewAfter == null}) nor found by number ({@code listedAfter == false}). */
    public static Result deleted(String docNo, GeneralVoucherRootView rootviewAfter, boolean listedAfter) {
        ApiChecks c = new ApiChecks();
        c.eq("Voucher " + docNo + " readable after Delete", false, rootviewAfter != null);
        c.eq("Voucher " + docNo + " listed after Delete", false, listedAfter);
        return new Result(c.passed, c.failed, c.rows, c.titles);
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private static Map<String, BigDecimal> expectedAllocations(List<SiteAllocation> list) {
        Map<String, BigDecimal> m = new TreeMap<>();
        if (list != null) for (SiteAllocation s : list) m.merge(s.getSiteName().trim(), s.getAmount().stripTrailingZeros(), BigDecimal::add);
        return m;
    }

    private static Map<String, BigDecimal> allocationsOf(List<CostCenterAllocation> list) {
        Map<String, BigDecimal> m = new TreeMap<>();
        if (list != null) {
            for (CostCenterAllocation s : list) m.merge(s.getName().trim(), BigDecimal.valueOf(s.getAmount()).stripTrailingZeros(), BigDecimal::add);
        }
        return m;
    }

    private static LocalDate date(String iso) {
        return iso == null || iso.length() < 10 ? null : LocalDate.parse(iso.substring(0, 10));
    }

    private static String norm(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
