package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.utils.DateUtils;
import com.gsl.utils.Log;
import com.gsl.utils.cheque.ChequeInstrumentResolver;
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
                tcId, refs.getDocSchemeName(), LocalDate.now(), null, refs.getCashBankName(),
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

        // Instrument/Cheque Date: honor a variant-supplied override (e.g. a genuinely
        // post-dated cheque) when present; otherwise default to today — same as before, just
        // now formatted to match the UI's own dd/MM/yyyy expectation (see DateUtils' Javadoc —
        // a prior LocalDate.now().toString() here produced an ISO yyyy-MM-dd string the app
        // rejected as an invalid date, confirmed live for FIN-GV-ADD-COMBO-05).
        LocalDate instrumentDate = variant.getInstrumentDateOverride() != null
                ? variant.getInstrumentDateOverride() : LocalDate.now();

        // Round 6: payment is decided PER GRID ROW (variant.paymentForRow) — a legacy variant keeps
        // its single payment on row 1 and no payment on the others, a multi-row-payment variant
        // carries one assigned payment per row (config-weighted, see
        // GeneralVoucherFixtureFactory.assignKnownChequeFlags). Every CHEQUE row resolves its OWN
        // instrument (known = a distinct unused finchqdet cheque of the header bank, unknown = a
        // fresh synthetic one) and every NEFT/RTGS row its own reference, so no two rows share one.
        List<PaymentInformation> rowPayments = new ArrayList<>();
        java.util.Map<String, ChequeInstrumentResolver.ChequeBankMatch> rowBankMatches = new java.util.LinkedHashMap<>();
        java.util.Set<String> usedInstrumentNos = new java.util.HashSet<>();
        boolean anyRowWithoutPayment = false;
        for (int i = 0; i < lines.size(); i++) {
            String lineNo = lines.get(i).getLineNo();
            GeneralVoucherPaymentVariant rowVariant = variant.paymentForRow(i);
            PaymentInformation payment = switch (rowVariant) {
                case CASH -> new PaymentInformation(tcId, lineNo, PaymentSelectionType.CASH, null, null, null, null);
                case CHEQUE -> {
                    // Round 23: keep the resolver's ACTUAL outcome (a "known" row can silently fall back
                    // to unknown) — GeneralVoucherCreateChangeSets' bookcode rule needs the real one.
                    ChequeInstrumentResolver.ChequeResolution resolution = variant.knownChequeForRow(i)
                            ? ChequeInstrumentResolver.resolveKnownSameBank(DatabaseManager.getJdbi(), refs.getCashBankGlcode())
                            : ChequeInstrumentResolver.resolveUnknown();
                    Log.info("[GeneralVoucherTemplateLoader] Row {} cheque instrument resolved: bankMatch={}, chqno={}",
                            lineNo, resolution.bankMatch(), resolution.chqno());
                    rowBankMatches.put(lineNo, resolution.bankMatch());
                    usedInstrumentNos.add(resolution.chqno());
                    yield new PaymentInformation(tcId, lineNo, PaymentSelectionType.CHEQUE, resolution.chqno(),
                            instrumentDate.format(DateUtils.UI_DATE_FORMAT), null, null);
                }
                case NEFT_RTGS -> {
                    String reference = PaymentReferenceGenerator.generateNEFT();
                    while (!usedInstrumentNos.add(reference)) reference = PaymentReferenceGenerator.generateNEFT();
                    yield new PaymentInformation(tcId, lineNo, PaymentSelectionType.NEFT_RTGS, reference,
                            instrumentDate.format(DateUtils.UI_DATE_FORMAT), null, null);
                }
                case NONE -> null;
            };
            if (payment != null) rowPayments.add(payment);
            else anyRowWithoutPayment = true;
        }
        PaymentInformation paymentInfo = rowPayments.isEmpty() ? null : rowPayments.get(0);
        ChequeInstrumentResolver.ChequeBankMatch firstBankMatch = paymentInfo == null ? null
                : rowBankMatches.get(paymentInfo.getLineNo());
        // The app's post-save "instrument missing" warning applies to a Bank header with any row lacking one.
        boolean needsWarning = variant.getPaymentVariant().cashBankType() == com.gsl.enums.CashBankType.BANK
                && anyRowWithoutPayment;

        return MergedFinanceGeneralVoucherData.builder()
                .documentInformation(doc)
                .expenseIncomeDetailList(lines)
                .siteAllocationList(allocations)
                .cashBankPaymentInfo(paymentInfo)
                .rowPayments(rowPayments)
                .rowChequeBankMatches(rowBankMatches)
                // Round 23: the resolver's ACTUAL outcome of the first payment (single-payment consumers).
                .actualChequeBankMatch(firstBankMatch)
                .needsPaymentInfoWarning(needsWarning)
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

    // ─── Negative/boundary templates — mirrors ApVoucherTemplateLoader's negXxxTemplate shape ──
    //
    // All 4 resolve their GL/Site/Cash-Bank ledger live via LiveGvDbRefs (built by
    // DbGeneralVoucherDataSource.loadFreshForScenario off a GeneralVoucherFixtureOptions —
    // see CreateGeneralVoucherNegativeBoundary's @DataProvider), exactly like the positive
    // combination matrix — no hardcoded GL/Site/ledger names. Each scenario's amount-side
    // (Debit) and deliberate data-shape choice (under-allocation / empty allocation / blank Sub
    // Ledger / post-dated date) is the actual point of the scenario and stays hand-authored.

    /**
     * NEG_SITE_ALLOCATION_PENDING — Type2 row where the Allocate Sites Tagging Summary's
     * Pending Amount != 0 (under-allocation): the site is allocated 500.00 against a 900.00
     * line, deliberately less.
     */
    public static MergedFinanceGeneralVoucherData negSiteAllocationPendingTemplate(LiveGvDbRefs refs) {
        String tcId = "GV_NEG_PENDING";
        LiveGvDbRefs.ResolvedGlRow row = refs.getResolvedRows().get(0);
        DocumentInformation doc = new DocumentInformation(tcId, refs.getDocSchemeName(), LocalDate.now(), null,
                refs.getCashBankName(), "Negative: Allocate Sites Pending Amount != 0");

        ExpenseIncomeDetail line = new ExpenseIncomeDetail(tcId, "1", row.getGl().getGlname(), null, null,
                "Under-allocated site", "No", new BigDecimal("900.00"), null);
        line.setResolvedGlType(GlLineType.TYPE2_SITE_ONLY);

        SiteAllocation partial = new SiteAllocation();
        partial.setTestCaseID(tcId);
        partial.setLineNo("1");
        partial.setSiteName(row.getSiteNames().get(0));
        partial.setAmount(new BigDecimal("500.00")); // deliberately less than 900.00 → Pending != 0

        return MergedFinanceGeneralVoucherData.builder()
                .documentInformation(doc)
                .expenseIncomeDetailList(List.of(line))
                .siteAllocationList(List.of(partial))
                .needsPaymentInfoWarning(false)
                .expectedValidation(NegativeValidation.GV_SITE_ALLOCATION_PENDING)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /**
     * NEG_SUBLEDGER_REQUIRED — Type3 row with Sub Ledger deliberately left blank (exercises
     * {@code GeneralVoucherExpenseIncomeDetail.setSubLedger}'s blank-guard, which skips the fill
     * and lets the app's own validation fire on the Site-button click).
     *
     * <p>{@code refs} is resolved with {@code GeneralVoucherFixtureOptions.requireAmbiguousSubLedger
     * = true}, guaranteeing the chosen GL has MORE THAN ONE eligible Sub Ledger — per the app's own
     * auto-populate rule (user-confirmed), a Type3 GL with exactly one eligible Sub Ledger has it
     * filled automatically, with no validation left to trigger.
     */
    public static MergedFinanceGeneralVoucherData negSubledgerRequiredTemplate(LiveGvDbRefs refs) {
        String tcId = "GV_NEG_SUBLEDGER";
        LiveGvDbRefs.ResolvedGlRow row = refs.getResolvedRows().get(0);
        DocumentInformation doc = new DocumentInformation(tcId, refs.getDocSchemeName(), LocalDate.now(), null,
                refs.getCashBankName(), "Negative: Type3 row missing Sub Ledger");

        // Sub Ledger deliberately left null (subLedger/subLedgerId args below) even though refs
        // resolved one — GeneralVoucherExpenseIncomeDetail.setSubLedger's blank-guard skips the
        // fill, so clicking Site should raise "No sub-ledger selected".
        ExpenseIncomeDetail line = new ExpenseIncomeDetail(tcId, "1", row.getGl().getGlname(), null, null,
                "Missing sub ledger", "No", new BigDecimal("800.00"), null);
        line.setResolvedGlType(GlLineType.TYPE3_SITE_AND_SUBLEDGER);

        SiteAllocation alloc = new SiteAllocation();
        alloc.setTestCaseID(tcId);
        alloc.setLineNo("1");
        alloc.setSiteName(row.getSiteNames().get(0));
        alloc.setAmount(new BigDecimal("800.00"));

        return MergedFinanceGeneralVoucherData.builder()
                .documentInformation(doc)
                .expenseIncomeDetailList(List.of(line))
                .siteAllocationList(List.of(alloc))
                .needsPaymentInfoWarning(false)
                .expectedValidation(NegativeValidation.GV_SUBLEDGER_REQUIRED)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /**
     * NEG_SITE_NOT_ALLOCATED — Type2 row whose Site button is clicked but nothing is added in
     * the popup (empty {@link SiteAllocation} list).
     */
    public static MergedFinanceGeneralVoucherData negSiteNotAllocatedTemplate(LiveGvDbRefs refs) {
        String tcId = "GV_NEG_NOSITE";
        LiveGvDbRefs.ResolvedGlRow row = refs.getResolvedRows().get(0);
        DocumentInformation doc = new DocumentInformation(tcId, refs.getDocSchemeName(), LocalDate.now(), null,
                refs.getCashBankName(), "Negative: Type2 row, Site button clicked but nothing allocated");

        ExpenseIncomeDetail line = new ExpenseIncomeDetail(tcId, "1", row.getGl().getGlname(), null, null,
                "Unallocated site", "No", new BigDecimal("900.00"), null);
        line.setResolvedGlType(GlLineType.TYPE2_SITE_ONLY);

        return MergedFinanceGeneralVoucherData.builder()
                .documentInformation(doc)
                .expenseIncomeDetailList(List.of(line))
                .siteAllocationList(List.of()) // empty — Site button opens popup, nothing added, OK clicked
                .needsPaymentInfoWarning(false)
                .expectedValidation(NegativeValidation.GV_SITE_NOT_ALLOCATED)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /**
     * NEG_CASH_POST_DATED — Round 21: a Cash-type Cash/Bank ledger together with a post-dated
     * Document Date makes the app correctly reject the Save. Deliberately +30 days, not just +1 —
     * a safely future date sidesteps any midnight-boundary/Work-Date ambiguity entirely, since
     * this scenario only needs to GUARANTEE post-dated, not pin down the app's exact "today".
     * {@code refs} is resolved with {@code GeneralVoucherPaymentVariant.CASH}, so {@code
     * refs.getCashBankName()} is a live Cash-type (not Bank-type) ledger.
     */
    public static MergedFinanceGeneralVoucherData negCashPostDatedTemplate(LiveGvDbRefs refs) {
        String tcId = "GV_NEG_CASH_POSTDATED";
        LiveGvDbRefs.ResolvedGlRow row = refs.getResolvedRows().get(0);
        LocalDate postDatedDate = LocalDate.now().plusDays(30);
        DocumentInformation doc = new DocumentInformation(tcId, refs.getDocSchemeName(), postDatedDate, null,
                refs.getCashBankName(), "Negative: Cash payment mode cannot be post-dated");

        ExpenseIncomeDetail line = new ExpenseIncomeDetail(tcId, "1", row.getGl().getGlname(), null, null,
                "Cash post-dated rejection", "No", new BigDecimal("900.00"), null);
        line.setResolvedGlType(GlLineType.TYPE2_SITE_ONLY);

        SiteAllocation alloc = new SiteAllocation();
        alloc.setTestCaseID(tcId);
        alloc.setLineNo("1");
        alloc.setSiteName(row.getSiteNames().get(0));
        alloc.setAmount(new BigDecimal("900.00"));

        return MergedFinanceGeneralVoucherData.builder()
                .documentInformation(doc)
                .expenseIncomeDetailList(List.of(line))
                .siteAllocationList(List.of(alloc))
                .needsPaymentInfoWarning(false)
                .expectedValidation(NegativeValidation.GV_CASH_POST_DATED_NOT_ALLOWED)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /**
     * NEG_DOC_SCHEME_EXHAUSTED — a complete, otherwise-valid voucher (same shape as a positive combination)
     * whose Voucher No. is a document scheme with no numbers left; Save must be rejected with a proper message.
     * {@code refs} is resolved with {@code DocSchemeMode.EXHAUSTED}, so {@code refs.getDocSchemeName()} is the full scheme.
     */
    public static MergedFinanceGeneralVoucherData negDocSchemeExhaustedTemplate(LiveGvDbRefs refs) {
        MergedFinanceGeneralVoucherData data = combinationTemplate(refs);
        data.setExpectedValidation(NegativeValidation.DOC_SCHEME_EXHAUSTED);
        return data;
    }

    /**
     * Round 16 — {@code NEG_SITE_ALLOCATION_CROSS_OU}. {@code refs} is resolved with
     * {@code GeneralVoucherFixtureOptions#requireCrossOuSite} set, so {@code
     * row.getSiteNames().get(0)} is a real GL-linked, user-assigned site belonging to a DIFFERENT
     * Organizational Unit than the resolved Owner Site (see
     * {@code DbGeneralVoucherDataSource#resolveCrossOuSiteForNegativeTest}).
     *
     * <p>The {@link SiteAllocation} built here is deliberately NEVER submitted to the UI as a real
     * allocation — it's reused purely as the existing DTO slot carrying the site name under test,
     * the same "carry a resolved value through to the behavior predicate" idiom
     * {@link #negCashPostDatedTemplate} already uses its own {@code SiteAllocation} for, just for
     * a different (row-absence, not row-selection) purpose here.
     */
    public static MergedFinanceGeneralVoucherData negSiteAllocationCrossOuTemplate(LiveGvDbRefs refs) {
        String tcId = "GV_NEG_SITE_CROSS_OU";
        LiveGvDbRefs.ResolvedGlRow row = refs.getResolvedRows().get(0);
        DocumentInformation doc = new DocumentInformation(tcId, refs.getDocSchemeName(), LocalDate.now(), null,
                refs.getCashBankName(), "Negative: site from a different Organizational Unit than "
                + "the Owner Site must not be selectable in Allocate Sites");

        ExpenseIncomeDetail line = new ExpenseIncomeDetail(tcId, "1", row.getGl().getGlname(), null, null,
                "Cross-OU site rejection", "No", new BigDecimal("900.00"), null);
        line.setResolvedGlType(GlLineType.TYPE2_SITE_ONLY);

        SiteAllocation crossOuSiteUnderTest = new SiteAllocation();
        crossOuSiteUnderTest.setTestCaseID(tcId);
        crossOuSiteUnderTest.setLineNo("1");
        crossOuSiteUnderTest.setSiteName(row.getSiteNames().get(0));
        crossOuSiteUnderTest.setAmount(new BigDecimal("900.00"));

        return MergedFinanceGeneralVoucherData.builder()
                .documentInformation(doc)
                .expenseIncomeDetailList(List.of(line))
                .siteAllocationList(List.of(crossOuSiteUnderTest))
                .needsPaymentInfoWarning(false)
                .expectedValidation(NegativeValidation.GV_SITE_CROSS_OU_NOT_SELECTABLE)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }
}
