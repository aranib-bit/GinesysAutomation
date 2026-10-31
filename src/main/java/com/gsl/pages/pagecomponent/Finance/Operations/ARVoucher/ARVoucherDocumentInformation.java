package com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherDocumentInformationBase;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.DocumentInformation;

public class ARVoucherDocumentInformation extends VoucherDocumentInformationBase {

    public ARVoucherDocumentInformation(CashBankParent parentPage) {
        super(parentPage, VoucherDomain.AR);
    }

    public ARVoucherDocumentInformation fillDocumentHeader(DocumentInformation docInfo) {
        fillCommonFields(docInfo);
        // The AR Remarks combo stays disabled until a Cash/Bank is chosen, so a deliberately blank
        // Cash/Bank (NEG_BLANK_CASHBANK) cannot fill it — run 20261031_030341 waited 30s on it and
        // timed out before Save could raise "Cash/Bank is required".
        String cashBank = docInfo.getCashbank();
        if (cashBank == null || cashBank.isBlank()) {
            return this;
        }
        setRemarks(docInfo.getRemarks());
        return this;
    }
}
