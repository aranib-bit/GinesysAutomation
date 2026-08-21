package com.gsl.pages.enums.Finance.Operations.Common;

import com.gsl.enums.VoucherDomain;

import java.util.Optional;

/**
 * List-grid columns identical (same header text and data type) across General Voucher,
 * AR Voucher, and AP Voucher's list pages — confirmed against each voucher type's
 * "Columns" chooser flyout.
 *
 * <p>Only {@link #VOUCHER_NO} and {@link #VOUCHER_DATE} are {@code mandatory} — they never
 * appear in the "Columns" chooser at all (fixed, un-hideable). Every other column here does
 * appear in the chooser and is addable/removable, so {@code mandatory=false} even though
 * some of them are checked (visible) by default today.
 *
 * <p>Type-specific columns (e.g. GV/AP's "Payment Amt (Cr)", AR/AP's "SLID"/"SL Name"/
 * "BRS Date", AP's "On Account") are NOT here — see {@code GeneralVoucherListColumn},
 * {@code ARVoucherListColumn}, {@code APVoucherListColumn}.
 *
 * <p>TODO: both AR's and AP's column-chooser screenshots showed "Reference" listed twice
 * (once unchecked, once checked) — modelled here as a single {@link #REFERENCE} column;
 * confirm against live DOM whether that's actually two distinct fields.
 */
public enum CommonVoucherListColumn implements IVoucherListColumn {

    VOUCHER_NO("Voucher No.", ColumnDataType.STRING, true),
    VOUCHER_DATE("Voucher Date", ColumnDataType.DATE, true),
    OWNER_SITE("Owner Site", ColumnDataType.STRING, false),
    OWNER_SITE_SHORT_NAME("Owner Site Short Name", ColumnDataType.STRING, false),
    CASH_BANK_NAME("Cash/Bank Name", ColumnDataType.STRING, false),
    REFERENCE("Reference", ColumnDataType.STRING, false),
    REMARKS("Remarks", ColumnDataType.STRING, false),
    RELEASE_STATUS("Release Status", ColumnDataType.ENUM_STATUS, false),
    PAYMENT_MODE("Payment Mode", ColumnDataType.ENUM_STATUS, false),
    INSTRUMENT_NO("Instrument No.", ColumnDataType.STRING, false),
    INSTRUMENT_DATE("Instrument Date", ColumnDataType.DATE, false),
    CHEQUE_LABEL("Cheque Label", ColumnDataType.STRING, false),
    RELEASED_ON("Released On", ColumnDataType.DATE, false),
    RELEASED_BY("Released By", ColumnDataType.STRING, false),
    CREATED_ON("Created On", ColumnDataType.DATE, false),
    CREATED_BY("Created By", ColumnDataType.STRING, false),
    LAST_MODIFIED_ON("Last Modified On", ColumnDataType.DATE, false),
    LAST_MODIFIED_BY("Last Modified By", ColumnDataType.STRING, false),
    YEAR_NAME("Year Name", ColumnDataType.STRING, false),
    NO_OF_ATTACHMENTS("No. of Attachments", ColumnDataType.INTEGER, false),
    ENTRY_SOURCE("Entry Source", ColumnDataType.STRING, false);

    private final String headerText;
    private final ColumnDataType dataType;
    private final boolean mandatory;

    CommonVoucherListColumn(String headerText, ColumnDataType dataType, boolean mandatory) {
        this.headerText = headerText;
        this.dataType = dataType;
        this.mandatory = mandatory;
    }

    @Override
    public String headerText() {
        return headerText;
    }

    @Override
    public ColumnDataType dataType() {
        return dataType;
    }

    @Override
    public boolean mandatory() {
        return mandatory;
    }

    @Override
    public String columnIdXPath() {
        return "//span[normalize-space()='" + headerText
                + "']/ancestor::div[contains(@id,'gridcolumn')]/@id";
    }

    /**
     * Resolves this common column for a specific domain: {@code Optional.empty()} if the
     * domain excludes this column entirely ({@link VoucherDomain#excludesCommonListColumn}),
     * otherwise the domain's override if one exists ({@link VoucherDomain#overrideCommonListColumn}),
     * otherwise this column as-is. Call sites use {@code col.resolveFor(domain)} — they never
     * need to change when a future domain overrides or drops a specific column.
     */
    public Optional<IVoucherListColumn> resolveFor(VoucherDomain domain) {
        if (domain.excludesCommonListColumn(this)) {
            return Optional.empty();
        }
        return Optional.of(domain.overrideCommonListColumn(this).orElse(this));
    }
}
