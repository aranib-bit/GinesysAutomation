package com.tests.API;

import com.gsl.api.model.generalvoucher.GeneralVoucherDetailLine;
import com.gsl.api.model.generalvoucher.GeneralVoucherRootView;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.validation.api.GeneralVoucherApiStateChecks;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Server-free unit tests for the {@code db.available=no} Create / Revert / Delete checks. */
public class GeneralVoucherApiStateChecksUnitTest {

    private static GeneralVoucherDetailLine line(long gl, String name, double expense, String narration) {
        return GeneralVoucherDetailLine.builder().glCode(gl).glName(name).expense(expense).income(0).ledgerNarration(narration)
                .isReversal("N").paymentMode("H").build();
    }

    private static GeneralVoucherRootView view(String status, String reference, GeneralVoucherDetailLine... lines) {
        GeneralVoucherRootView v = new GeneralVoucherRootView();
        v.setDocumentStatus(status);
        v.setVoucherDate("2026-10-26T00:00:00");
        v.setReference(reference);
        v.setRemarks("r");
        v.setExpenseIncomeDetail(List.of(lines));
        return v;
    }

    private static MergedFinanceGeneralVoucherData expected(String reference, String glName, String amount) {
        return MergedFinanceGeneralVoucherData.builder()
                .documentInformation(new DocumentInformation("V", null, LocalDate.of(2026, 10, 26), reference, null, "r"))
                .expenseIncomeDetailList(List.of(new ExpenseIncomeDetail(null, "1", glName, null, null, "n", "No", new BigDecimal(amount), null)))
                .build();
    }

    @Test
    public void createPassesWhenTheVoucherHoldsWhatWasEntered() {
        GeneralVoucherApiStateChecks.Result r = GeneralVoucherApiStateChecks.created(expected("ref", "Cash GL", "100.00"),
                view("P", "ref", line(1, "Cash GL", 100, "n")));
        assertTrue(r.ok(), r.failed().toString());
    }

    @Test
    public void createFailsOnAWrongAmountAndOnAMissingVoucher() {
        GeneralVoucherApiStateChecks.Result wrong = GeneralVoucherApiStateChecks.created(expected("ref", "Cash GL", "100.00"),
                view("P", "ref", line(1, "Cash GL", 7, "n")));
        assertFalse(wrong.ok());
        assertTrue(wrong.failed().stream().anyMatch(s -> s.startsWith("Line 1 amount")), wrong.failed().toString());
        assertFalse(GeneralVoucherApiStateChecks.created(expected("ref", "Cash GL", "100.00"), null).ok());
    }

    @Test
    public void revertExpectsTheStatusToChangeAndNothingElse() {
        GeneralVoucherRootView before = view("P", "ref", line(1, "A", 100, "n"));
        assertTrue(GeneralVoucherApiStateChecks.statusChangedOnly(before, view("U", "ref", line(1, "A", 100, "n")), "U").ok());

        GeneralVoucherApiStateChecks.Result stillPosted = GeneralVoucherApiStateChecks.statusChangedOnly(before,
                view("P", "ref", line(1, "A", 100, "n")), "U");
        assertFalse(stillPosted.ok());
        assertTrue(stillPosted.failed().get(0).startsWith("Release status"), stillPosted.failed().toString());

        assertFalse(GeneralVoucherApiStateChecks.statusChangedOnly(before, view("U", "changed", line(1, "A", 100, "n")), "U").ok());
    }

    @Test
    public void aBlockedRevertExpectsTheStatusUnchanged() {
        GeneralVoucherRootView posted = view("P", "ref", line(1, "A", 100, "n"));
        assertTrue(GeneralVoucherApiStateChecks.statusChangedOnly(posted, view("P", "ref", line(1, "A", 100, "n")), "P").ok());
    }

    @Test
    public void deleteRequiresTheVoucherToBeUnreadableAndUnlisted() {
        assertTrue(GeneralVoucherApiStateChecks.deleted("VG/1", null, false).ok());
        assertFalse(GeneralVoucherApiStateChecks.deleted("VG/1", view("U", "r", line(1, "A", 1, "n")), false).ok());
        assertFalse(GeneralVoucherApiStateChecks.deleted("VG/1", null, true).ok());
    }
}
