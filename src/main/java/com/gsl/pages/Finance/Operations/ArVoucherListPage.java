package com.gsl.pages.Finance.Operations;

import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherListFooter;
import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherListGridHeader;
import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherMainGrid;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherListPageBase;

/**
 * AR Voucher list page — plan §17: thin domain subclass of the common
 * {@link VoucherListPageBase}, mirroring {@code GeneralVoucherListPage}. Public API is
 * unchanged from before this split — {@code NavigationUtils} still instantiates this class
 * reflectively via its public no-arg constructor, and every method signature tests already call
 * is preserved (inherited from the base rather than declared here, but the same signatures/
 * return types) — including {@code selectDocumentInListPage(String)}, which previously existed
 * only on {@code GeneralVoucherListPage} and is now available here too, closing that gap.
 * Locators remain the same unresolved {@code //TODO-} placeholders as before (living in
 * {@code VoucherMainGridBase}, not duplicated locally).
 */
public class ArVoucherListPage extends VoucherListPageBase<
        ARVoucherListGridHeader, ARVoucherMainGrid, ARVoucherListFooter,
        ArVoucherAdd, ArVoucherListPage> {

    public ArVoucherListPage() {
        super(new ARVoucherListGridHeader(), new ARVoucherMainGrid(), new ARVoucherListFooter());
    }

    @Override
    protected ArVoucherAdd newAddPage() {
        return new ArVoucherAdd();
    }
}
