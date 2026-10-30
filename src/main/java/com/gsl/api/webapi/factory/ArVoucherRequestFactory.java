package com.gsl.api.webapi.factory;

import com.gsl.api.model.arvoucher.ArVoucherCreateRequest;
import com.gsl.api.model.arvoucher.ArVoucherDetailLine;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.udf.UdfValueEntry;

import java.util.List;

/** Builds the AR Voucher create request from already-resolved values. No HTTP, no lookups, no shared state: the service resolves, this assembles. */
public final class ArVoucherRequestFactory {

    private ArVoucherRequestFactory() {}

    /** Everything an AR Voucher create needs, resolved. The line is a cash receipt ({@code paymentMode "C"}). */
    public record Inputs(SubLedgerLookupResult subLedger, long referenceSiteCode, String referenceSiteName, long docSchemeCode, long cashBankGlCode,
                         int ownerSiteCode, int ouCode, long empCode, String workDate, double amount, List<UdfValueEntry> udfValues) {}

    public static ArVoucherCreateRequest create(Inputs in, String requestId) {
        SubLedgerLookupResult subLedger = in.subLedger();
        ArVoucherDetailLine line = ArVoucherDetailLine.builder()
                .subLedgerCode(subLedger.getSlCode())
                .subLedgerName(subLedger.getSlName())
                .subLedgerId(subLedger.getSlid())
                .subLedgerClassName(subLedger.getClassName())
                .ledgerCode(subLedger.getGlCode())
                .ledgerName(subLedger.getGlName())
                .ledgerGroupName(subLedger.getGeneralLedgerGroupName())
                .ledgerAbbreviation(subLedger.getGeneralLedgerAbbre())
                .referenceSiteName(in.referenceSiteName())
                .referenceSiteCode(in.referenceSiteCode())
                .paymentMode("C")
                .amount(in.amount())
                .build();
        return ArVoucherCreateRequest.builder()
                .voucherDate(in.workDate())
                .docSchemeCode(in.docSchemeCode())
                .empCode(in.empCode())
                .connectedOUCode(in.ouCode())
                .connectedSiteCode(in.ownerSiteCode())
                .ownerSiteCode(in.ownerSiteCode())
                .cashBankGLCode(in.cashBankGlCode())
                .arVoucherDetail(List.of(line))
                .requestId(requestId)
                .udfValues(in.udfValues())
                .build();
    }
}
