package com.gsl.pages.enums.Finance.Operations.Common;

import org.openqa.selenium.By;

/** Contract for a voucher grid column header.
 *  Returns the XPath expression that resolves to the column's data-columnid attribute value. */
public interface IVoucherGridColumnHeader {
    String columnIdXPath();

    /** The raw column header text (e.g. "Reference Site", "Sub Ledger") — added for the UI-contract
     *  validation framework ({@code com.gsl.validation.ui}), which needs the literal text/locator
     *  for the header cell itself, not just the derived {@code data-columnid} xpath. */
    String headerText();

    /** The header cell's own {@code <span>} — same xpath shape {@link #columnIdXPath()} is built
     *  from, minus its {@code ancestor::.../@id} suffix. */
    default By headerLocator() {
        return By.xpath("//span[normalize-space()='" + headerText() + "']");
    }

    /** Always-shown vs user-optional column — see {@link GridColumnKind}. Default: always shown. */
    default GridColumnKind kind() {
        return GridColumnKind.DEFAULT;
    }

    /** The column's configured ExtJS {@code itemId} when it has one (its cells then carry it as
     *  {@code data-columnid} instead of the header id), else {@code null}. See {@code GridCellLocator}. */
    default String itemId() {
        return null;
    }

    /** Whether body cells of this column hold numbers (amount-style) rather than text. */
    default boolean numeric() {
        return false;
    }
}
