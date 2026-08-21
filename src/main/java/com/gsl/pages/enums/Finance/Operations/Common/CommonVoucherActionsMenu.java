package com.gsl.pages.enums.Finance.Operations.Common;

import com.gsl.enums.VoucherDomain;

import java.util.Optional;

/**
 * Actions-dropdown items identical across General Voucher, AR Voucher, and AP Voucher's list
 * pages (Add/Edit/Revert/Release/Delete/Print/Export to Excel) — mirrors
 * {@link CommonVoucherListColumn}'s shape for grid columns, but for the Actions menu (plan §9.3).
 *
 * <p>AP's one extra action ("Workbench") is NOT here — see
 * {@code com.gsl.pages.enums.Finance.Operations.APVoucher.APVoucherActionsMenu}.
 *
 * <p>TODO: only {@link #EDIT}'s DB shortcode ({@code "Modify"}) is confirmed to diverge from its
 * UI label today; the other constants' {@code dbOperationShortcode}s (including
 * {@link #EXPORT_TO_EXCEL}'s {@code "Export"}) are unconfirmed guesses against
 * {@code mdappoperation} — see plan §9's open items.
 */
public enum CommonVoucherActionsMenu implements IVoucherGridAction {

    ADD("Add"),
    EDIT("Modify"),
    REVERT("Revert"),
    RELEASE("Release"),
    DELETE("Delete"),
    PRINT("Print"),
    EXPORT_TO_EXCEL("Export");

    private final String dbOperationShortcode;

    CommonVoucherActionsMenu(String dbOperationShortcode) {
        this.dbOperationShortcode = dbOperationShortcode;
    }

    @Override
    public String dbOperationShortcode() {
        return dbOperationShortcode;
    }

    /**
     * Resolves this common action for a specific domain: {@code Optional.empty()} if the domain
     * excludes this action entirely ({@link VoucherDomain#excludesCommonAction}), otherwise the
     * domain's override if one exists ({@link VoucherDomain#overrideCommonAction}), otherwise
     * this action as-is. Mirrors {@link CommonVoucherListColumn#resolveFor(VoucherDomain)}
     * exactly — call sites use {@code action.resolveFor(domain)} and never need to change when a
     * future domain overrides or drops a specific action.
     */
    public Optional<IVoucherGridAction> resolveFor(VoucherDomain domain) {
        if (domain.excludesCommonAction(this)) {
            return Optional.empty();
        }
        return Optional.of(domain.overrideCommonAction(this).orElse(this));
    }
}
