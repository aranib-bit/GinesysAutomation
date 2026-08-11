package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.PaymentSelectionType;
import com.gsl.utils.cheque.UniqueChequeGenerator;
import com.gsl.utils.payment.PaymentReferenceGenerator;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.dataprovider.fixture.LiveGvDbRefs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link MergedFinanceGeneralVoucherData} from {@link LiveGvDbRefs} for one
 * combination-matrix variant — the GV analogue of {@code ApVoucherTemplateLoader}.
 */
public final class GeneralVoucherTemplateLoader {

    private GeneralVoucherTemplateLoader() {}

    private static final BigDecimal TYPE1_AMOUNT = new BigDecimal("901.00");
    private static final BigDecimal TYPE2_AMOUNT = new BigDecimal("900.00");
    private static final BigDecimal TYPE3_AMOUNT = new BigDecimal("800.00");

    /**
     * Builds the header + one {@link ExpenseIncomeDetail} row per {@link LiveGvDbRefs#getResolvedRows()}
     * entry (the "multi GL" entity axis — 1 entry for the mandatory baseline, N for the
     * {@code MULTI_GL}/{@code MULTI_GL_N_SITES} regression variants), each carrying its own
     * {@link SiteAllocation} list (the "N sites" sub-axis — see {@link #buildSiteAllocations}) and
     * {@link PaymentInformation} for the shared Cash/Bank counter-leg.
     *
     * <p>Payment info is built for {@link GeneralVoucherPaymentVariant#CASH} (no instrument
     * number), {@link GeneralVoucherPaymentVariant#CHEQUE} (unique cheque number via
     * {@link UniqueChequeGenerator}), and {@link GeneralVoucherPaymentVariant#NEFT_RTGS}
     * (unique reference via {@link PaymentReferenceGenerator}) — mirroring {@code
     * ApVoucherTemplateLoader}/{@code ArVoucherTemplateLoader}'s own {@code buildPayments}.
     * {@code NONE} deliberately carries no payment info at all (that's the point of the
     * variant — the app's own post-save warning is expected instead).
     *
     * <p><b>UNVERIFIED — CHEQUE/NEFT_RTGS untested against the live UI</b> (see
     * {@link GeneralVoucherPaymentVariant}'s class Javadoc and
     * {@link com.gsl.pages.Finance.Operations.Common.GvPaymentInformationPopup} — the "manual
     * UI-discovery spike" these variants were blocked pending).
     */
    public static MergedFinanceGeneralVoucherData combinationTemplate(LiveGvDbRefs refs) {
        GeneralVoucherVariant variant = refs.getVariant();
        String tcId = variant.getVariantId();

        BigDecimal amount = switch (variant.getGlLineType()) {
            case TYPE1_NONE -> TYPE1_AMOUNT;
            case TYPE2_SITE_ONLY -> TYPE2_AMOUNT;
            case TYPE3_SITE_AND_SUBLEDGER -> TYPE3_AMOUNT;
        };

        DocumentInformation doc = new DocumentInformation(
                tcId, null, LocalDate.now(), null, refs.getCashBankName(),
                "Automated GV DB-driven combination test — " + variant.getTestCaseName());
        doc.setOwnersite(refs.getOwnerSiteName());

        BigDecimal expenseDr = variant.isDebitSide() ? amount : null;
        BigDecimal incomeCr = variant.isDebitSide() ? null : amount;

        List<ExpenseIncomeDetail> lines = new ArrayList<>();
        List<SiteAllocation> allocations = new ArrayList<>();
        List<LiveGvDbRefs.ResolvedGlRow> resolvedRows = refs.getResolvedRows();
        for (int i = 0; i < resolvedRows.size(); i++) {
            LiveGvDbRefs.ResolvedGlRow row = resolvedRows.get(i);
            String lineNo = String.valueOf(i + 1);

            ExpenseIncomeDetail line = new ExpenseIncomeDetail(
                    tcId, lineNo, row.getGl().getGlname(),
                    row.getSubLedgerName(), row.getSubLedgerId(),
                    "Combination matrix row — " + variant.getAccountType().label(),
                    variant.isReversal() ? "Yes" : "No", expenseDr, incomeCr);
            line.setResolvedGlType(variant.getGlLineType());
            lines.add(line);

            allocations.addAll(buildSiteAllocations(tcId, lineNo, row.getSiteNames(), amount));
        }

        PaymentInformation paymentInfo = switch (variant.getPaymentVariant()) {
            case CASH -> new PaymentInformation(tcId, "CASHBANK", PaymentSelectionType.CASH,
                    null, null, null, null);
            case CHEQUE -> new PaymentInformation(tcId, "CASHBANK", PaymentSelectionType.CHEQUE,
                    UniqueChequeGenerator.generateUnique(), LocalDate.now().toString(), null, null);
            case NEFT_RTGS -> new PaymentInformation(tcId, "CASHBANK", PaymentSelectionType.NEFT_RTGS,
                    PaymentReferenceGenerator.generateNEFT(), LocalDate.now().toString(), null, null);
            case NONE -> null;
        };

        return MergedFinanceGeneralVoucherData.builder()
                .documentInformation(doc)
                .expenseIncomeDetailList(lines)
                .siteAllocationList(allocations)
                .cashBankPaymentInfo(paymentInfo)
                .needsPaymentInfoWarning(variant.getPaymentVariant().needsPaymentInfoWarning())
                .scenarioTypeLabel(tcId)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /**
     * Builds the {@link SiteAllocation} list for one row — empty for {@code TYPE1_NONE} (no Site
     * dimension), one entry for the mandatory baseline ({@code siteNames.size() == 1}), or N
     * entries with the row's amount apportioned across them for the {@code N_SITES}/
     * {@code MULTI_GL_N_SITES} regression variants. The last site absorbs any rounding remainder
     * so the allocations always sum exactly to {@code amount} — mirrors AR's
     * {@code AmountMode.PARTIAL}/{@code MIX} apportionment idiom.
     */
    private static List<SiteAllocation> buildSiteAllocations(String tcId, String lineNo,
            List<String> siteNames, BigDecimal amount) {
        if (siteNames == null || siteNames.isEmpty()) return List.of();

        List<SiteAllocation> allocations = new ArrayList<>();
        BigDecimal perSite = amount.divide(BigDecimal.valueOf(siteNames.size()), 2, RoundingMode.HALF_UP);
        BigDecimal runningTotal = BigDecimal.ZERO;
        for (int i = 0; i < siteNames.size(); i++) {
            boolean isLast = i == siteNames.size() - 1;
            BigDecimal siteAmount = isLast ? amount.subtract(runningTotal) : perSite;
            runningTotal = runningTotal.add(siteAmount);

            SiteAllocation alloc = new SiteAllocation();
            alloc.setTestCaseID(tcId);
            alloc.setLineNo(lineNo);
            alloc.setSiteName(siteNames.get(i));
            alloc.setAmount(siteAmount);
            allocations.add(alloc);
        }
        return allocations;
    }

    /**
     * Merges N already-resolved single-ledger-type results (each built by {@link #combinationTemplate}
     * via {@code DbGeneralVoucherDataSource.loadFreshForMixedTypes}) into ONE voucher whose
     * Expense/Income Detail rows span all of them — the one true "MIXED" scenario. Re-numbers each
     * merged row's {@code lineNo} sequentially across the combined set so lines/site-allocations
     * stay correctly paired after merging.
     */
    public static MergedFinanceGeneralVoucherData mergeIntoMixedVoucher(
            List<MergedFinanceGeneralVoucherData> perType) {
        String tcId = "MIXED_" + perType.size() + "_TYPES";
        List<ExpenseIncomeDetail> mergedLines = new ArrayList<>();
        List<SiteAllocation> mergedAllocations = new ArrayList<>();
        List<String> mergedGatheringLog = new ArrayList<>();
        DocumentInformation mergedDoc = null;
        PaymentInformation paymentInfo = null;
        boolean needsWarning = false;
        int lineCounter = 1;

        for (MergedFinanceGeneralVoucherData single : perType) {
            if (single.getDbGatheringLog() != null && !single.getDbGatheringLog().isEmpty()) {
                if (!mergedGatheringLog.isEmpty()) mergedGatheringLog.add("");
                mergedGatheringLog.addAll(single.getDbGatheringLog());
            }
            if (mergedDoc == null) {
                DocumentInformation firstDoc = single.getDocumentInformation();
                mergedDoc = new DocumentInformation(tcId, firstDoc.getVoucherno(), firstDoc.getDocumentdate(),
                        firstDoc.getReference(), firstDoc.getCashbank(),
                        "Automated GV DB-driven MIXED-types test — spans " + perType.size() + " ledger types");
                mergedDoc.setOwnersite(firstDoc.getOwnersite());
            }
            if (paymentInfo == null) paymentInfo = single.getCashBankPaymentInfo();
            needsWarning = needsWarning || single.isNeedsPaymentInfoWarning();

            for (ExpenseIncomeDetail line : single.getExpenseIncomeDetailList()) {
                String newLineNo = String.valueOf(lineCounter++);
                for (SiteAllocation sa : single.getSiteAllocationsForLine(line.getLineNo())) {
                    SiteAllocation copy = new SiteAllocation();
                    copy.setTestCaseID(tcId);
                    copy.setLineNo(newLineNo);
                    copy.setSiteName(sa.getSiteName());
                    copy.setAmount(sa.getAmount());
                    mergedAllocations.add(copy);
                }
                ExpenseIncomeDetail renumbered = new ExpenseIncomeDetail(tcId, newLineNo, line.getGeneralLedger(),
                        line.getSubLedger(), line.getSubLedgerId(), line.getLedgerNarration(),
                        line.getIsReversal(), line.getExpenseDr(), line.getIncomeCr());
                renumbered.setResolvedGlType(line.getResolvedGlType());
                mergedLines.add(renumbered);
            }
        }

        return MergedFinanceGeneralVoucherData.builder()
                .documentInformation(mergedDoc)
                .expenseIncomeDetailList(mergedLines)
                .siteAllocationList(mergedAllocations)
                .cashBankPaymentInfo(paymentInfo)
                .needsPaymentInfoWarning(needsWarning)
                .scenarioTypeLabel(tcId)
                .invocationIndex(1)
                .totalInvocations(1)
                .dbGatheringLog(mergedGatheringLog)
                .build();
    }
}
