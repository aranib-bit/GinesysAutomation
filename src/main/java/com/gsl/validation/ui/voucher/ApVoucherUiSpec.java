package com.gsl.validation.ui.voucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.enums.Finance.Operations.APVoucher.APVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.FormSection;
import com.gsl.pages.enums.Finance.Operations.Common.GridColumnKind;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridColumnHeader;
import com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherDocumentInformation;
import com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherFooter;
import com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherSubLedgerInformation;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherDocumentInformationBase;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherFooterBase;
import com.gsl.utils.ui.UiFieldState;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;

import java.util.List;

/** AP Voucher as data for {@link VoucherUiValidationStage}: columns, extra fields (On Account), components. */
public final class ApVoucherUiSpec implements VoucherUiSpec {

    private final APVoucherDocumentInformation documentInformation;
    private final APVoucherFooter footer;
    private final APVoucherSubLedgerInformation subLedger;

    public ApVoucherUiSpec(APVoucherDocumentInformation documentInformation, APVoucherFooter footer,
            APVoucherSubLedgerInformation subLedger) {
        this.documentInformation = documentInformation;
        this.footer = footer;
        this.subLedger = subLedger;
    }

    @Override public String rowIdentity(int rowNumber) { return subLedger.rowIdentityLabel(rowNumber); }
    @Override public com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherSubLedgerInformationBase subLedger() { return subLedger; }

    @Override public VoucherDomain domain() { return VoucherDomain.AP; }
    @Override public VoucherDocumentInformationBase documentInformation() { return documentInformation; }
    @Override public VoucherFooterBase footer() { return footer; }
    @Override public FormSection gridSection() { return FormSection.SUB_LEDGER_INFORMATION; }

    @Override
    public List<IVoucherGridColumnHeader> gridColumns() {
        return List.of(
                APVoucherGridColumnHeader.SUB_LEDGER,
                APVoucherGridColumnHeader.GENERAL_LEDGER,
                CommonVoucherGridColumnHeader.REFERENCE_SITE.resolveFor(VoucherDomain.AP),
                new UserColumn(CommonVoucherGridColumnHeader.SUB_LEDGER_ID.resolveFor(VoucherDomain.AP)),
                APVoucherGridColumnHeader.AMOUNT_DR,
                APVoucherGridColumnHeader.LEDGER_NARRATION,
                APVoucherGridColumnHeader.DEDUCT_TDS);
    }

    @Override
    public List<UiValidationFailure> extraDocumentInformation(UiValidationLevel level, UiFieldState state, boolean isEditMode) {
        return documentInformation.validateOnAccountUi(level);
    }

    /** Marks a shared column as user-optional for this voucher (the column menu can hide it). */
    record UserColumn(IVoucherGridColumnHeader delegate) implements IVoucherGridColumnHeader {
        @Override public String columnIdXPath() { return delegate.columnIdXPath(); }
        @Override public String headerText() { return delegate.headerText(); }
        @Override public GridColumnKind kind() { return GridColumnKind.USER; }
        @Override public boolean numeric() { return delegate.numeric(); }
    }
}
