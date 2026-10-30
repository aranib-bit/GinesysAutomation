package com.gsl.api.webapi.factory;

import com.gsl.api.model.apvoucher.ApVoucherAdjustmentDetail;
import com.gsl.api.model.apvoucher.ApVoucherAttachment;
import com.gsl.api.model.apvoucher.ApVoucherCreateRequest;
import com.gsl.api.model.apvoucher.ApVoucherDetailLine;
import com.gsl.api.model.apvoucher.TdsSectionLookupResult;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.udf.UdfValueEntry;

import java.util.List;

/** Builds the AP Voucher create request from already-resolved values. No HTTP, no lookups, no shared state: the service resolves, this assembles. */
public final class ApVoucherRequestFactory {

    private ApVoucherRequestFactory() {}

    /**
     * Everything an AP Voucher create needs, resolved.
     *
     * @param grossAmount         the line amount before TDS (for an adjusting voucher: the sum of the adjusted amounts)
     * @param tds                 the TDS section to deduct against, or null for no TDS (always null for an adjusting voucher)
     * @param journalDocSchemeCode the TDS journal doc scheme; required when {@code tds} is set, otherwise ignored
     * @param adjustments         pending documents the line adjusts; null or empty = an on-account (advance) voucher
     */
    public record Inputs(SubLedgerLookupResult subLedger, long docSchemeCode, long cashBankGlCode, long referenceSiteCode, int ownerSiteCode, int ouCode,
                         long empCode, String workDate, double grossAmount, TdsSectionLookupResult tds, Long journalDocSchemeCode,
                         List<ApVoucherAdjustmentDetail> adjustments, List<UdfValueEntry> udfValues) {
        boolean adjusting() {
            return adjustments != null && !adjustments.isEmpty();
        }
    }

    public static ApVoucherCreateRequest create(Inputs in, String requestId) {
        SubLedgerLookupResult subLedger = in.subLedger();
        ApVoucherDetailLine.ApVoucherDetailLineBuilder lineBuilder = ApVoucherDetailLine.builder()
                .subLedgerCode(subLedger.getSlCode())
                .subLedgerName(subLedger.getSlName())
                .ledgerCode(subLedger.getGlCode())
                .generalLedgerGroupName(subLedger.getGeneralLedgerGroupName())
                .generalLedgerAbbre(subLedger.getGeneralLedgerAbbre() != null ? subLedger.getGeneralLedgerAbbre() : "")
                .referenceSiteCode(in.referenceSiteCode());

        Long journalDocSchemeCode = null;
        if (in.tds() != null) {
            double gross = in.grossAmount();
            double tdsAmtRaw = gross * in.tds().getTdsRate() / 100.0;
            double tdsRoundOffAmt = Math.round(tdsAmtRaw);
            double netPayable = gross - tdsRoundOffAmt;
            journalDocSchemeCode = in.journalDocSchemeCode();
            lineBuilder
                    .deductTDS(true)
                    .tdsRate(in.tds().getTdsRate())
                    .tdsTypeCode(in.tds().getCode())
                    .tdsAppAmt(gross)
                    .tdsAmt(tdsAmtRaw)
                    .tdsGrossAmt(gross)
                    .tdsRoundOffAmt(tdsRoundOffAmt)
                    .netPayable(netPayable)
                    .amount(netPayable);
        } else {
            lineBuilder.deductTDS(false).amount(in.grossAmount());
        }
        if (in.adjusting()) {
            lineBuilder.apVoucherAdjustmentDetail(in.adjustments());
        }

        return ApVoucherCreateRequest.builder()
                .voucherDate(in.workDate())
                .docSchemeCode(in.docSchemeCode())
                .empCode(in.empCode())
                .connectedOUCode(in.ouCode())
                .connectedSiteCode(in.ownerSiteCode())
                .ownerSiteCode(in.ownerSiteCode())
                .cashBankGLCode(in.cashBankGlCode())
                .autoCreatedJournalDocSchemeCode(journalDocSchemeCode)
                .onAccount(!in.adjusting())
                .apVoucherDetail(List.of(lineBuilder.build()))
                .requestId(requestId)
                .attachment(ApVoucherAttachment.builder().build())
                .udfValues(in.udfValues())
                .build();
    }
}
