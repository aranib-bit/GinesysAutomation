package com.gsl.pages.enums.Finance.Operations.APVoucher;

import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherActionsMenu;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridAction;

/**
 * AP Voucher's one Actions-dropdown item beyond the 7 shared in
 * {@link CommonVoucherActionsMenu} — mirrors {@link APVoucherListColumn}'s shape for AP-specific
 * grid columns, but for the Actions menu (plan §9.3).
 *
 * <p>TODO: {@code dbOperationShortcode} ("Workbench") is an unconfirmed guess against
 * {@code mdappoperation} — see plan §9's open items (also unconfirmed: whether Workbench needs
 * its own permission-key handling distinct from the 7 common actions).
 */
public enum APVoucherActionsMenu implements IVoucherGridAction {

    WORKBENCH("Workbench");

    private final String dbOperationShortcode;

    APVoucherActionsMenu(String dbOperationShortcode) {
        this.dbOperationShortcode = dbOperationShortcode;
    }

    @Override
    public String dbOperationShortcode() {
        return dbOperationShortcode;
    }
}
