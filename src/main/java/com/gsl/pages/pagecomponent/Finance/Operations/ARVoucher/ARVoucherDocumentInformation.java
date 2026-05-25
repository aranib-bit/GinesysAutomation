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
        setRemarks(docInfo.getRemarks());
        return this;
    }
}
