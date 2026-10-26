package com.gsl.utils.dataprovider.source.finance;

import com.gsl.enums.CashBankType;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInfoEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.dto.GeneralVoucherOperationMode;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditVariant;
import com.gsl.utils.dataprovider.fixture.finance.NegativeValidation;
import com.gsl.utils.dataprovider.fixture.finance.VoucherEditDateVariant;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * The last step of building one General Voucher Edit row — turning an already-resolved voucher (header values, existing lines, site
 * allocations, the edited-line / payment target) into the {@link MergedFinanceGeneralVoucherData} the Edit test consumes: which header
 * fields change per the variant's scope, the new document date, the expected negative validation, and the report description.
 *
 * <p>Shared by {@link DbGeneralVoucherEditDataSource} and {@link ApiGeneralVoucherEditDataSource} so the two sources can never drift apart
 * on those rules. It touches no database and no API: the only environment-specific input is the {@code editDateResolver} callback (the DB
 * source resolves dates from {@code admyear}/{@code admmonth}; the API source probes the application's own date validation).
 */
final class GeneralVoucherEditRowAssembler {

    private GeneralVoucherEditRowAssembler() {}

    /** The voucher header values the assembler needs, however they were read. */
    record EditHeader(String schemeDocno, String releaseStatus, LocalDate vchdt, String refno, String nartext) {}

    /**
     * @param editDateResolver called only when the variant changes the document date; maps its {@link VoucherEditDateVariant} to the date
     * @param logTag           prefix for the final log line, e.g. {@code "[DB-Edit]"}
     */
    static MergedFinanceGeneralVoucherData assemble(String logTag, GeneralVoucherEditVariant variant, long vchcode, EditHeader header,
            String siteName, String cashBankName, List<ExpenseIncomeDetail> expenseIncomeRows, List<SiteAllocation> siteAllocations,
            DetailLineEditTarget editedDetailLine, PaymentInfoEditTarget paymentEditTarget,
            Function<VoucherEditDateVariant, LocalDate> editDateResolver, List<String> gatheringLog) {

        // Scope fix: a DETAIL_LINE/PAYMENT_INFO scenario must edit ONLY its own target — the
        // header's date/reference/remarks stay exactly as they were before this Save. Generating a
        // new date/reference/remarks unconditionally here (the former behavior) silently turned
        // every "detail-only"/"payment-only" scenario into a combined header+other edit, which is
        // exactly the scope-ambiguity the report review flagged. That combined-edit shape is real
        // and will be covered later by its own regression-phase scenarios — not folded invisibly
        // into this one.
        boolean headerFieldsUnchanged = variant.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE
                || variant.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO;

        LocalDate newDate;
        String newReference;
        String newRemarks;
        NegativeValidation expectedValidation;

        if (headerFieldsUnchanged) {
            newDate = header.vchdt();
            newReference = header.refno();
            newRemarks = header.nartext();
            // PaymentInfoDateScenario#expectedRejection() is null for every ordinary Payment-Info
            // variant (unchanged default) and for the 3 positive date scenarios — only
            // NEFT_FUTURE_DATE_CR_REJECTED carries a real NegativeValidation. Mirrors exactly how
            // the HEADER_ONLY branch below derives its own expectedValidation from its own flags.
            expectedValidation = variant.getDateScenario() != null
                    ? variant.getDateScenario().expectedRejection() : null;
            gatheringLog.add("  " + variant.getScenarioType() + " scenario — header date/reference/remarks "
                    + "left unchanged (date=" + newDate + ", refno='" + newReference + "')");
        } else {
            // Field-isolated header edits (plan §4D): a HEADER_ONLY variant's scope decides which
            // of date/reference/remarks actually change — ALL (the pre-existing, still-default
            // behavior) changes every field; a single-field scope holds the other two at the
            // header's own existing value, the same "hold unchanged" idiom the DETAIL_LINE branch
            // above already uses for the fields a given scenario doesn't touch. This is what makes
            // a report row actually isolate one field's effect instead of always changing all three
            // together, which made it impossible to tell which field's change a given assertion was
            // really exercising.
            GeneralVoucherEditVariant.HeaderFieldScope scope = variant.getHeaderFieldScope() != null
                    ? variant.getHeaderFieldScope() : GeneralVoucherEditVariant.HeaderFieldScope.ALL;
            boolean changeDate = scope == GeneralVoucherEditVariant.HeaderFieldScope.ALL
                    || scope == GeneralVoucherEditVariant.HeaderFieldScope.DATE_ONLY;
            boolean changeReference = scope == GeneralVoucherEditVariant.HeaderFieldScope.ALL
                    || scope == GeneralVoucherEditVariant.HeaderFieldScope.REFERENCE_ONLY;
            boolean changeRemarks = scope == GeneralVoucherEditVariant.HeaderFieldScope.ALL
                    || scope == GeneralVoucherEditVariant.HeaderFieldScope.REMARKS_ONLY;

            VoucherEditDateVariant dateVariant = null;
            if (changeDate) {
                // Accounting-year-aware new document date (replaces the former unconditional
                // LocalDate.now(), which caused a live save failure once a claimed voucher's own
                // accounting year didn't bracket today — see the "GV Edit: accounting-year-aware
                // dates..." plan). HEADER_ONLY variants always set an explicit dateVariant.
                dateVariant = variant.getDateVariant() != null
                        ? variant.getDateVariant() : VoucherEditDateVariant.RANDOM_MID;
                newDate = editDateResolver.apply(dateVariant);
            } else {
                newDate = header.vchdt();
            }
            newReference = changeReference ? FakerUtils.randomReference() : header.refno();
            newRemarks = changeRemarks ? FakerUtils.randomRemarks() : header.nartext();

            // Non-null for CROSS_YEAR_INVALID (always) and FUTURE_DATE when the claimed
            // candidate's Cash/Bank ledger is Cash-type (via requiredCashBankType, set only by
            // GV_EDIT_HEADER_UNPOSTED_FUTURE_DATE_CASH_NEGATIVE — see claimOne's cashBankFilter
            // above, which guarantees this variant only ever claims a Cash-type candidate) — the
            // app is expected to REJECT the save with this exact message rather than succeed (see
            // EditGeneralVoucher's Step 9b). FUTURE_DATE against a Bank-type ledger
            // (GV_EDIT_HEADER_UNPOSTED_FUTURE_DATE_BANK) is NOT a rejection case — confirmed live
            // the app accepts a future-dated document for Bank-type within the same accounting
            // year, so it falls through to `default -> null` and flows through Step 9b's normal
            // positive save + full snapshot-validation path. Only reachable when changeDate is
            // true — a scope that holds the date unchanged can never be rejected on
            // accounting-year or post-dated-Cash grounds. See "Round 22" of the GV plan
            // (FIN-GV-EDIT-HEADER-04) — this was previously a single ambiguous FUTURE_DATE
            // variant with no Cash/Bank-type awareness at all.
            expectedValidation = changeDate ? switch (dateVariant) {
                case CROSS_YEAR_INVALID -> NegativeValidation.GV_EDIT_DATE_CROSSES_ACCOUNTING_YEAR;
                case FUTURE_DATE -> variant.getRequiredCashBankType() == CashBankType.CASH
                        ? NegativeValidation.GV_CASH_POST_DATED_NOT_ALLOWED : null;
                default -> null;
            } : null;

            gatheringLog.add("  Header field scope=" + scope + " — date="
                    + (changeDate ? newDate + " (dateVariant=" + dateVariant
                        + (expectedValidation != null ? ", expecting REJECTION: " + expectedValidation.expectedMessage()
                           : dateVariant == VoucherEditDateVariant.FUTURE_DATE
                                ? ", intentionally future-dated (Cash/Bank type=" + variant.getRequiredCashBankType()
                                  + ") — app is confirmed to ACCEPT this, full snapshot validation applies "
                                  + "same as any other positive header edit"
                                : "") + ")"
                       : newDate + " (unchanged)")
                    + ", reference=" + (changeReference ? "'" + newReference + "' (new)" : "'" + newReference + "' (unchanged)")
                    + ", remarks=" + (changeRemarks ? "(new)" : "(unchanged)"));
        }

        DocumentInformation newDocInfo = new DocumentInformation(
                variant.getVariantId(),
                null,                               // voucherno — left null so setDocScheme()'s blank-guard
                                                     // never touches/reads the Voucher No. field at all (see
                                                     // plan §3: passing the existing scheme_docno here timed
                                                     // out on a live run — that field's read-before-skip path
                                                     // requires visibility that Edit mode's disabled/
                                                     // differently-rendered Voucher No. widget doesn't give
                                                     // within the wait window).
                newDate,                            // documentdate — NEW value for HEADER_ONLY, UNCHANGED for DETAIL_LINE
                newReference,                       // reference — NEW value for HEADER_ONLY, UNCHANGED for DETAIL_LINE
                cashBankName,                      // unchanged Cash/Bank
                newRemarks);                        // remarks — NEW value for HEADER_ONLY, UNCHANGED for DETAIL_LINE
        newDocInfo.setOwnersite(siteName);

        gatheringLog.add("═══════════════════════════════════════════════════════════");

        MergedFinanceGeneralVoucherData data = MergedFinanceGeneralVoucherData.builder()
                .scenarioTypeLabel(variant.getVariantId())
                .existingVoucherNo(header.schemeDocno())
                .releaseStatus(header.releaseStatus())
                .currentDocDate(header.vchdt())
                .originalReference(header.refno())
                .originalRemarks(header.nartext())
                .reservedDocumentKey(String.valueOf(vchcode))
                .operationMode(GeneralVoucherOperationMode.EDIT)
                .documentInformation(newDocInfo)
                .expenseIncomeDetailList(expenseIncomeRows)
                .siteAllocationList(siteAllocations)
                .editedDetailLine(editedDetailLine)
                .paymentEditTarget(paymentEditTarget)
                // Cross-dependency with the shared Extent header (GeneralVoucherExtentDescriptionLogger
                // #appendExtraHeaderRows): setting these here — not just paymentEditTarget — is what
                // makes the "Cheque No."/"Cheque Known/Unknown" header rows render the Edit "after"
                // state automatically, with zero further changes needed in that reporting file.
                .cashBankPaymentInfo(paymentEditTarget != null ? paymentEditTarget.getNewPayment() : null)
                .actualChequeBankMatch(paymentEditTarget != null ? paymentEditTarget.getNewBankMatch() : null)
                .headerFieldScope(variant.getHeaderFieldScope())
                .entryMethod(variant.getEntryMethod())
                .expectedValidation(expectedValidation)
                .dbGatheringLog(gatheringLog)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();

        Log.info(logTag + " Built edit data for vchcode={}, scheme_docno='{}', variant='{}'",
                vchcode, header.schemeDocno(), variant.getVariantId());
        return data;
    }

}
