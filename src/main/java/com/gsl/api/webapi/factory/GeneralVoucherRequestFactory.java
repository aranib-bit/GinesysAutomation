package com.gsl.api.webapi.factory;

import com.gsl.api.model.common.CashBankLedgerLookupResult;
import com.gsl.api.model.common.UserSiteContext;
import com.gsl.api.model.generalvoucher.CostCenterAllocation;
import com.gsl.api.model.generalvoucher.GeneralVoucherCreateRequest;
import com.gsl.api.model.generalvoucher.GeneralVoucherDetailLine;
import com.gsl.api.model.generalvoucher.GeneralVoucherDocSchemeRef;
import com.gsl.api.model.generalvoucher.GeneralVoucherOwnerSite;
import com.gsl.api.model.generalvoucher.GlLedgerLookupResult;
import com.gsl.api.model.udf.UdfValueEntry;

import java.util.List;

/** Builds the simple one-line General Voucher create request from already-resolved values. No HTTP, no lookups, no shared state. */
public final class GeneralVoucherRequestFactory {

    private GeneralVoucherRequestFactory() {}

    /**
     * Everything the one-line voucher needs, resolved.
     *
     * @param header      the Cash/Bank ledger the voucher is paid against (cash = pay mode "H", bank = "C")
     * @param allocations the cost-centre allocation of a site-applicable (Type2) ledger, or null for a Type1 ledger
     * @param debit       true for Asset/Expense lines (an expense amount), false for Liability/Income (an income amount)
     */
    public record Inputs(UserSiteContext.Site site, int ouCode, long empCode, CashBankLedgerLookupResult header, long docSchemeCode, GlLedgerLookupResult gl,
                         List<CostCenterAllocation> allocations, boolean debit, String workDate, boolean release, List<UdfValueEntry> udf) {}

    public static GeneralVoucherCreateRequest simple(Inputs in, String requestId) {
        boolean cash = "C".equalsIgnoreCase(in.header().getSourceType());
        GeneralVoucherDetailLine line = GeneralVoucherDetailLine.builder()
                .glCode(in.gl().getGlCode()).glName(in.gl().getGlName()).siteApplicable(in.allocations() == null ? "N" : "Y").groupName(in.gl().getGroupName())
                .expense(in.debit() ? 1 : 0).income(in.debit() ? 0 : 1)
                .paymentMode(cash ? "H" : "C")
                .costCenterAllocations(in.allocations())
                .build();
        UserSiteContext.Site site = in.site();
        return GeneralVoucherCreateRequest.builder()
                .empCode(in.empCode()).connectedOUCode(in.ouCode())
                .ownerSite(GeneralVoucherOwnerSite.builder().code((int) site.getSiteCode()).name(site.getSiteName()).siteType(site.getSiteType())
                        .shortName(site.getShortName()).ouName(site.getOuName()).ouCode(site.getOuCode()).build())
                .docScheme(GeneralVoucherDocSchemeRef.builder().code(in.docSchemeCode()).build())
                .cashBankLedger(in.header())
                .voucherDate(in.workDate())
                .expenseIncomeDetail(List.of(line))
                .markedForSaveAndRelease(in.release())
                .requestId(requestId)
                .udfDetails(in.udf()).udfValues(in.udf())
                .build();
    }
}
