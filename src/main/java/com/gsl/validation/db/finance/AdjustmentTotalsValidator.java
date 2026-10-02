package com.gsl.validation.db.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinTagDao;
import com.gsl.database.dao.finance.FinVchDetDao;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.model.finance.FinTag;
import com.gsl.model.finance.FinVchDet;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ReportLogger;
import org.jdbi.v3.core.Jdbi;
import org.testng.Assert;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generic DB read-back for the AP/AR document-adjustment invariant the amount-mismatch
 * Confirmation enforces in the UI: for a non-advance voucher, the saved Sub-Ledger payment amount
 * must equal the total tagged in {@code fintag} ("Adjusted amount should be equal to payment
 * amount of current record"). Shared by the Create mismatch scenarios and the Edit detail-line
 * scenarios so the assertion lives once.
 *
 * <p>Mechanics only — takes primitives ({@code scheme_docno}, expected amounts, expected
 * documents), no domain test-data types.
 */
public final class AdjustmentTotalsValidator {

    private AdjustmentTotalsValidator() {}

    /** Payment amount carried by the voucher's {@code finvchdet} lines (Dr, else Cr, per line). */
    public static BigDecimal paymentTotal(List<FinVchDet> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (FinVchDet l : lines) {
            BigDecimal d = l.getDamount();
            BigDecimal c = l.getCamount();
            total = total.add(d != null && d.signum() != 0 ? d : (c != null ? c : BigDecimal.ZERO));
        }
        return total;
    }

    /** Total tagged against {@code vchcode} on either side of {@code fintag}. */
    public static BigDecimal taggedTotal(Jdbi jdbi, long vchcode) {
        FinTagDao dao = jdbi.onDemand(FinTagDao.class);
        BigDecimal total = BigDecimal.ZERO;
        for (FinTag t : dao.findByEntCode1(vchcode)) total = total.add(t.getAmount());
        for (FinTag t : dao.findByEntCode2(vchcode)) total = total.add(t.getAmount());
        return total;
    }

    /**
     * Asserts {@code finvchdet} payment total equals {@code expectedPayment} (skipped when null)
     * AND equals the {@code fintag} total — i.e. the voucher is fully, exactly adjusted.
     */
    public static void assertPaymentMatchesTags(String schemeDocno, BigDecimal expectedPayment) {
        Jdbi jdbi = DatabaseManager.getJdbi();
        FinVchMain header = jdbi.onDemand(FinVchMainDao.class).findBySchemeDocno(schemeDocno)
                .orElseThrow(() -> new IllegalStateException(
                        "[AdjustmentTotals] finvchmain row not found for scheme_docno=" + schemeDocno));
        List<FinVchDet> lines = jdbi.onDemand(FinVchDetDao.class).findByVoucherCode(header.getVchcode());
        Assert.assertFalse(lines.isEmpty(), "[AdjustmentTotals] finvchdet has no lines for " + schemeDocno);

        BigDecimal payment = paymentTotal(lines);
        BigDecimal tagged = taggedTotal(jdbi, header.getVchcode());
        if (expectedPayment != null) {
            Assert.assertEquals(payment.compareTo(expectedPayment), 0,
                    "[AdjustmentTotals] Expected payment amount " + expectedPayment + " but finvchdet carries "
                    + payment + " for " + schemeDocno);
        }
        Assert.assertEquals(tagged.compareTo(payment), 0,
                "[AdjustmentTotals] fintag total " + tagged + " != payment amount " + payment
                + " for " + schemeDocno + " — voucher is not exactly adjusted.");
        ReportLogger.pass("DB validated — payment " + payment + " == tagged total " + tagged
                + " for voucher: " + schemeDocno);
    }

    /**
     * Asserts the set of counter-document numbers currently tagged against {@code schemeDocno}
     * equals {@code expectedDocNos} (order-insensitive), resolved through {@code finpost}
     * ({@code fintag.entcode} is not always a voucher — see {@code FinPostDao#findSchemeDocnoByPostCode}).
     */
    public static void assertTaggedDocuments(String schemeDocno, Set<String> expectedDocNos) {
        Jdbi jdbi = DatabaseManager.getJdbi();
        FinVchMain header = jdbi.onDemand(FinVchMainDao.class).findBySchemeDocno(schemeDocno)
                .orElseThrow(() -> new IllegalStateException(
                        "[AdjustmentTotals] finvchmain row not found for scheme_docno=" + schemeDocno));
        Set<String> actual = counterDocumentNumbers(jdbi, header.getVchcode());
        Assert.assertEquals(actual, expectedDocNos,
                "[AdjustmentTotals] Tagged counter-documents differ for " + schemeDocno);
    }

    /**
     * Documents expected to be tagged after {@code plan} ran: {@code baseDocs} plus any documents
     * added by SELECT_DOCUMENT "I will adjust more" steps. Empty when the plan (or
     * {@code baseIsServerChosen}) involves FIFO / Ref-Site FIFO, where the server picks documents
     * so the exact set cannot be predicted — callers then rely on the totals assertion only.
     */
    public static java.util.Optional<Set<String>> expectedDocumentsAfter(
            java.util.Collection<String> baseDocs,
            com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentMismatchPlan plan,
            boolean baseIsServerChosen) {
        if (baseIsServerChosen) return java.util.Optional.empty();
        Set<String> docs = new java.util.TreeSet<>(baseDocs);
        if (plan != null) {
            for (com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentMismatchPlan.Step s : plan.getSteps()) {
                if (s.getExtra() == null) continue;
                if (s.getExtra().getType() != com.gsl.enums.AdjustmentType.SELECT_DOCUMENT) {
                    return java.util.Optional.empty();
                }
                s.getExtra().getDocs().forEach(d -> docs.add(d.getDocumentNumber()));
            }
        }
        return java.util.Optional.of(docs);
    }

    /** Counter-document numbers tagged against {@code vchcode} (either side), via finpost. */
    public static Set<String> counterDocumentNumbers(Jdbi jdbi, long vchcode) {
        FinTagDao dao = jdbi.onDemand(FinTagDao.class);
        com.gsl.database.dao.finance.FinPostDao postDao = jdbi.onDemand(com.gsl.database.dao.finance.FinPostDao.class);
        List<String> out = new java.util.ArrayList<>();
        for (FinTag t : dao.findByEntCode1(vchcode)) {
            postDao.findSchemeDocnoByPostCode(t.getPostcode2()).ifPresent(out::add);
        }
        for (FinTag t : dao.findByEntCode2(vchcode)) {
            postDao.findSchemeDocnoByPostCode(t.getPostcode1()).ifPresent(out::add);
        }
        return out.stream().collect(Collectors.toCollection(java.util.TreeSet::new));
    }
}
