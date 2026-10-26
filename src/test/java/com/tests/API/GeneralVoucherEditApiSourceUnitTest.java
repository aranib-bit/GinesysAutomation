package com.tests.API;

import com.gsl.api.model.generalvoucher.CostCenterAllocation;
import com.gsl.api.model.generalvoucher.GeneralVoucherDetailLine;
import com.gsl.api.model.generalvoucher.GeneralVoucherRootView;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInfoEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.ValidDateRangeFinder;
import com.gsl.utils.dataprovider.fixture.GeneralVoucherEditFixtureFactory;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditCombinationFilter;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditCombinations;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditExecutionGroup;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditVariant;
import com.gsl.validation.api.GeneralVoucherEditApiValidator;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Server-free unit tests for the pieces of the {@code db.available=no} General Voucher Edit path that need no application: the Mandatory
 * row count (header 4, detail 2, payment 3 — one row per variant, never combined), the accounting-year edge finder, and the rootview
 * before/after validator.
 */
public class GeneralVoucherEditApiSourceUnitTest {

    // ───────────────────────────── 9 separate Mandatory rows ─────────────────────────────

    private static long mandatoryRows(String method) {
        return GeneralVoucherEditCombinations.allCombinations().stream()
                .filter(v -> GeneralVoucherEditCombinationFilter.survives(v, GeneralVoucherEditExecutionGroup.MANDATORY))
                .filter(v -> GeneralVoucherEditFixtureFactory.shouldRun(v.getScenarioType(), method))
                .filter(v -> !v.isRequireClosedPeriod())
                .count();
    }

    @Test
    public void mandatoryEditRowsAreHeader4Detail2Payment3() {
        assertEquals(mandatoryRows("editGeneralVoucherHeader"), 4);
        assertEquals(mandatoryRows("editGeneralVoucherDetailLine"), 2);
        assertEquals(mandatoryRows("editGeneralVoucherPaymentInfo"), 3);
    }

    // ───────────────────────────── accounting-year edge finder ─────────────────────────────

    @Test
    public void findsBothEdgesOfTheAcceptedRange() {
        LocalDate first = LocalDate.of(2026, 4, 1);
        LocalDate last = LocalDate.of(2027, 3, 31);
        ValidDateRangeFinder.Range r = ValidDateRangeFinder.find(LocalDate.of(2026, 10, 26),
                d -> !d.isBefore(first) && !d.isAfter(last), 400);
        assertEquals(r.first(), first);
        assertEquals(r.last(), last);
        assertTrue(r.fullyBounded());
        assertEquals(ValidDateRangeFinder.spanDays(r), 365);
        assertTrue(r.probes() < 40, "should need only a few dozen probes, used " + r.probes());
    }

    @Test
    public void anAnchorOnTheEdgeIsHandled() {
        LocalDate first = LocalDate.of(2026, 4, 1);
        LocalDate last = LocalDate.of(2027, 3, 31);
        ValidDateRangeFinder.Range r = ValidDateRangeFinder.find(last, d -> !d.isBefore(first) && !d.isAfter(last), 400);
        assertEquals(r.first(), first);
        assertEquals(r.last(), last);
    }

    @Test
    public void aProbeThatNeverRejectsIsReportedUnbounded() {
        ValidDateRangeFinder.Range r = ValidDateRangeFinder.find(LocalDate.of(2026, 10, 26), d -> true, 400);
        assertFalse(r.firstBounded());
        assertFalse(r.lastBounded());
        assertFalse(r.fullyBounded());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void anUnacceptedAnchorIsRejected() {
        ValidDateRangeFinder.find(LocalDate.of(2026, 10, 26), d -> false, 400);
    }

    // ───────────────────────────── rootview before/after validator ─────────────────────────────

    private static GeneralVoucherDetailLine line(long gl, String name, double expense, String narration, String reversal,
                                                 String mode, String instrument, List<CostCenterAllocation> alloc) {
        return GeneralVoucherDetailLine.builder().glCode(gl).glName(name).expense(expense).income(0).ledgerNarration(narration)
                .isReversal(reversal).paymentMode(mode).instrumentNumber(instrument).costCenterAllocations(alloc).build();
    }

    private static GeneralVoucherRootView view(String date, String ref, String remarks, GeneralVoucherDetailLine... lines) {
        GeneralVoucherRootView v = new GeneralVoucherRootView();
        v.setVoucherDate(date + "T00:00:00");
        v.setReference(ref);
        v.setRemarks(remarks);
        v.setExpenseIncomeDetail(List.of(lines));
        return v;
    }

    private static MergedFinanceGeneralVoucherData data(LocalDate date, String ref, String remarks) {
        return MergedFinanceGeneralVoucherData.builder()
                .documentInformation(new DocumentInformation("V", null, date, ref, "Cash", remarks))
                .build();
    }

    @Test
    public void headerEditPassesWhenOnlyTheHeaderChanged() {
        GeneralVoucherRootView before = view("2026-10-26", "old", "old remarks", line(1, "A", 100, "n", "N", "H", null, null));
        GeneralVoucherRootView after = view("2026-10-20", "new", "new remarks", line(1, "A", 100, "n", "N", "H", null, null));
        GeneralVoucherEditApiValidator.Result r = GeneralVoucherEditApiValidator.validate(
                data(LocalDate.of(2026, 10, 20), "new", "new remarks"), GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY, before, after);
        assertTrue(r.ok(), r.failed().toString());
    }

    @Test
    public void headerEditFailsOnAnUnintendedLineChange() {
        GeneralVoucherRootView before = view("2026-10-26", "old", "r", line(1, "A", 100, "n", "N", "H", null, null));
        GeneralVoucherRootView after = view("2026-10-26", "new", "r", line(1, "A", 999, "n", "N", "H", null, null));
        GeneralVoucherEditApiValidator.Result r = GeneralVoucherEditApiValidator.validate(
                data(LocalDate.of(2026, 10, 26), "new", "r"), GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY, before, after);
        assertFalse(r.ok());
        assertTrue(r.failed().stream().anyMatch(s -> s.startsWith("Line 1 amount")), r.failed().toString());
    }

    @Test
    public void headerEditFailsWhenTheDateDidNotChange() {
        GeneralVoucherRootView before = view("2026-10-26", "r", "r", line(1, "A", 100, "n", "N", "H", null, null));
        GeneralVoucherRootView after = view("2026-10-26", "r", "r", line(1, "A", 100, "n", "N", "H", null, null));
        GeneralVoucherEditApiValidator.Result r = GeneralVoucherEditApiValidator.validate(
                data(LocalDate.of(2026, 10, 20), "r", "r"), GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY, before, after);
        assertFalse(r.ok());
        assertTrue(r.failed().stream().anyMatch(s -> s.startsWith("Document date")), r.failed().toString());
    }

    @Test
    public void detailLineEditChecksTheEditedLineAndLeavesTheOthersAlone() {
        GeneralVoucherRootView before = view("2026-10-26", "r", "r",
                line(1, "A", 100, "n1", "N", "H", null, null), line(2, "B", 50, "n2", "N", "H", null, null));
        GeneralVoucherRootView after = view("2026-10-26", "r", "r",
                line(1, "A", 600, "n1", "N", "H", null, null), line(2, "B", 50, "n2", "N", "H", null, null));
        ExpenseIncomeDetail newValues = new ExpenseIncomeDetail(null, "1", "A", null, null, "n1", "No", new BigDecimal("600.00"), null);
        MergedFinanceGeneralVoucherData d = MergedFinanceGeneralVoucherData.builder()
                .documentInformation(new DocumentInformation("V", null, LocalDate.of(2026, 10, 26), "r", "Cash", "r"))
                .editedDetailLine(DetailLineEditTarget.builder().lineNo("1").newValues(newValues)
                        .shape(DetailLineEditTarget.EditShape.IN_PLACE).build())
                .build();
        GeneralVoucherEditApiValidator.Result r = GeneralVoucherEditApiValidator.validate(
                d, GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE, before, after);
        assertTrue(r.ok(), r.failed().toString());
    }

    @Test
    public void paymentEditChecksModeAndInstrument() {
        GeneralVoucherRootView before = view("2026-10-26", "r", "r", line(1, "A", 100, "n", "N", "C", "111111", null));
        GeneralVoucherRootView after = view("2026-10-26", "r", "r", line(1, "A", 100, "n", "N", "C", "222222", null));
        PaymentInformation np = new PaymentInformation("V", "CASHBANK", PaymentSelectionType.CHEQUE, "222222", null, null, null);
        MergedFinanceGeneralVoucherData d = MergedFinanceGeneralVoucherData.builder()
                .documentInformation(new DocumentInformation("V", null, LocalDate.of(2026, 10, 26), "r", "Bank", "r"))
                .paymentEditTarget(PaymentInfoEditTarget.builder().newPayment(np).build())
                .build();
        assertTrue(GeneralVoucherEditApiValidator.validate(d, GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO, before, after).ok());

        GeneralVoucherRootView unchanged = view("2026-10-26", "r", "r", line(1, "A", 100, "n", "N", "C", "111111", null));
        GeneralVoucherEditApiValidator.Result bad = GeneralVoucherEditApiValidator.validate(
                d, GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO, before, unchanged);
        assertFalse(bad.ok());
        assertTrue(bad.failed().stream().anyMatch(s -> s.startsWith("Instrument number")), bad.failed().toString());
    }
}
