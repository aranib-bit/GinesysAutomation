package com.gsl.pages.enums.Finance.Operations.Common;

import com.gsl.database.MenuOperationKey;
import com.gsl.enums.VoucherDomain;
import org.openqa.selenium.By;

/**
 * Contract for one item in a voucher list page's "Actions" dropdown (Add/Edit/Revert/Release/
 * Delete/Print/Export to Excel, plus any domain-specific extra like AP's "Workbench") — mirrors
 * {@link IVoucherListColumn}'s shape for grid columns, but for menu actions (plan §9.2).
 */
public interface IVoucherGridAction {

    /** The DB {@code mdappoperation.sys_shortcode} value for this action, e.g. {@code "Modify"}. */
    String dbOperationShortcode();

    /**
     * The UI-facing label, e.g. {@code "Edit"} — translated from {@link #dbOperationShortcode()}
     * via {@link MenuOperationKey#toUiLabel(String)} ({@code "Modify"->"Edit"}, everything else
     * passes through unchanged).
     */
    default String label() {
        return MenuOperationKey.toUiLabel(dbOperationShortcode());
    }

    /** Locator for this action's menu item in the open Actions dropdown. */
    default By locator() {
        return By.xpath("//span[text()='" + label() + "']");
    }

    /**
     * Locator matching this action's menu item ONLY when ExtJS currently renders it enabled —
     * mirrors {@link com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherListFooterBase}'s
     * proven {@code aria-disabled} pattern for the pagination toolbar's Next/Last/First/Previous
     * controls, applied here to the Actions dropdown. Selenium's native {@code isEnabled()}/
     * {@code elementToBeClickable} can't see ExtJS's own disabled styling (no HTML
     * {@code disabled} attribute on a {@code <span>}/{@code <a>}), which is how a disabled "Edit"
     * can otherwise be "clicked" as a silent no-op — see
     * {@link com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherMainGridBase#clickGridAction}.
     *
     * <p>TODO: inferred, not yet explicitly confirmed against the live DOM (same caveat as
     * {@code VoucherListFooterBase#firstPageEnabledLocator} carries for its own inferred
     * controls) — confirm the menu item's wrapping {@code <a>} actually carries
     * {@code aria-disabled} the same way the pagination toolbar does.
     */
    default By enabledLocator() {
        return By.xpath("//span[text()='" + label() + "']/ancestor::a[1][@aria-disabled='false']");
    }

    /** Locator matching this action's menu item ONLY when ExtJS currently renders it disabled —
     *  see {@link #enabledLocator()}. */
    default By disabledLocator() {
        return By.xpath("//span[text()='" + label() + "']/ancestor::a[1][@aria-disabled='true']");
    }

    /**
     * Builds this action's {@code DbIdQueue}-style permission key for a given domain, via
     * {@link MenuOperationKey#build(String, String)}. Prepared but unconsumed for this pass — no
     * resolver, role/grant lookup, or visibility assertion reads it yet; it exists so a future
     * role-based-visibility feature is a new class reading grants keyed by a value every action
     * already produces, not a rework of every enum (plan §9.2).
     */
    default String permissionKey(VoucherDomain domain) {
        return MenuOperationKey.build(domain.getMenuShortcode(), dbOperationShortcode());
    }
}
