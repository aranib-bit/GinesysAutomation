package com.gsl.validation.ui.voucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.enums.Finance.Operations.ARVoucher.ARVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.FormSection;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridColumnHeader;
import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherDocumentInformation;
import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherFooter;
import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherSubLedgerInformation;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherDocumentInformationBase;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherFooterBase;

import java.util.List;

/** AR Voucher as data for {@link VoucherUiValidationStage} (AR's grid has no Reference Site column). */
public final class ArVoucherUiSpec implements VoucherUiSpec {

    private final ARVoucherDocumentInformation documentInformation;
    private final ARVoucherFooter footer;
    private final ARVoucherSubLedgerInformation subLedger;

    public ArVoucherUiSpec(ARVoucherDocumentInformation documentInformation, ARVoucherFooter footer,
            ARVoucherSubLedgerInformation subLedger) {
        this.documentInformation = documentInformation;
        this.footer = footer;
        this.subLedger = subLedger;
    }

    @Override public String rowIdentity(int rowNumber) { return subLedger.rowIdentityLabel(rowNumber); }
    @Override public com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherSubLedgerInformationBase subLedger() { return subLedger; }

    @Override public VoucherDomain domain() { return VoucherDomain.AR; }
    @Override public VoucherDocumentInformationBase documentInformation() { return documentInformation; }
    @Override public VoucherFooterBase footer() { return footer; }
    @Override public FormSection gridSection() { return FormSection.SUB_LEDGER_INFORMATION; }

    @Override
    public List<IVoucherGridColumnHeader> gridColumns() {
        return List.of(ARVoucherGridColumnHeader.SUB_LEDGER, ARVoucherGridColumnHeader.GENERAL_LEDGER,
                ARVoucherGridColumnHeader.AMOUNT_CR, ARVoucherGridColumnHeader.LEDGER_NARRATION);
    }
}
