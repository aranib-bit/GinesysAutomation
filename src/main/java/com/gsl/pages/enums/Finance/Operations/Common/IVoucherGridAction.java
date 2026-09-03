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
     * Locator matching this action's menu item ONLY while the Actions dropdown is genuinely OPEN
     * AND ExtJS currently renders the item enabled.
     *
     * <p><b>History: a previous version of this locator dropped the {@code aria-disabled} check
     * entirely, on a premise that a later run's DOM forensics disproved.</b> That version's
     * reasoning was: five captured DOM snapshots across four runs (including one with the
     * dropdown genuinely open) all showed {@code aria-disabled="false"} on
     * {@code a.x-menu-item-link}, so the attribute looked like a permanent no-op. But every one of
     * those five captures was taken <i>after</i> a selection had already succeeded (the menu was
     * captured post-success, action already enabled by then) — none was a genuine in-progress
     * failure. A later run's {@code ActionsMenu_*.html} captures, taken at the actual moment of
     * an "Actions→Edit/Delete disabled" failure, showed the opposite: {@code aria-disabled="true"}
     * genuinely present on every row-dependent item (Edit/Delete/Release/Revert/Print), with only
     * the row-independent "Export to Excel" enabled — and the voucher grid's own row, in that same
     * capture, explicitly carried {@code aria-describedby="...-cell-description-not-selected"}, no
     * {@code aria-selected}, no {@code x-grid-item-selected}. So the attribute is real and
     * correctly tracks row selection; the root cause of those failures was the grid genuinely
     * losing its selection between the checkbox check and the Actions click, not a broken
     * disabled-state signal. (A stray "selected" row elsewhere in those same captures belonged to
     * an unrelated navigation-tree component — a coincidence worth remembering when reading this
     * app's DOM: always confirm which {@code data-boundview}/component a "selected" match actually
     * belongs to before trusting it.)
     *
     * <p>What stays from that intermediate version: open-vs-closed lives on the menu container,
     * which carries the distinguishing {@code actionmenu} class (exactly one such element in the
     * DOM) and flips {@code aria-hidden}/{@code aria-expanded}/inline {@code visibility} together.
     * Scoping to it excludes the leftover grid column-filter flyouts, which ExtJS keeps in the DOM
     * via {@code visibility:hidden} rather than removing (one of them holds the "Exact match…"
     * field from the row-search step) — the exact ambiguity that made an even earlier generic
     * "last open {@code x-menu}" check worthless.
     *
     * <p>Menu items render as {@code div.x-menu-item > a.x-menu-item-link > span.x-menu-item-text}
     * — there is no {@code <li>} in this markup, and the {@code aria-disabled} attribute lives on
     * the {@code <a>}, not the {@code <span>}.
     *
     * <p><b>Caveat:</b> the {@code actionmenu} container does not exist in the DOM at all until the
     * Actions button is clicked the first time (ExtJS lazy render). This is therefore a valid
     * "wait until the dropdown is open and this item enabled" check, never a pre-existence check.
     */
    default By itemInOpenMenuLocator() {
        return By.xpath("//div[contains(@class,'actionmenu')][@aria-expanded='true']"
                + "//span[normalize-space()='" + label() + "']"
                + "/ancestor::a[1][@aria-disabled='false']");
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
