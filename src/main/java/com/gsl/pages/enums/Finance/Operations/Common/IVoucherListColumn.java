package com.gsl.pages.enums.Finance.Operations.Common;

/**
 * Contract for a voucher list-grid column: its display name, data type, and whether it's
 * one of the small set of fixed/un-hideable columns (as opposed to the addable/removable
 * columns exposed through each voucher list page's "Columns" chooser).
 *
 * <p>Extends {@link IVoucherGridColumnHeader} so list-column enums can also be used
 * anywhere a column header needs to be located/clicked by its {@code data-columnid}.
 */
public interface IVoucherListColumn extends IVoucherGridColumnHeader {

    /** The column's display header text, e.g. {@code "Voucher No."}. */
    String headerText();

    /** The column's data type — drives which filter mechanism the column will need. */
    ColumnDataType dataType();

    /**
     * {@code true} only for columns that are always present and cannot be removed via the
     * "Columns" chooser (e.g. Voucher No., Voucher Date). Every other column — regardless
     * of whether it happens to be checked/visible by default today — is addable/removable,
     * so returns {@code false}.
     */
    boolean mandatory();
}
