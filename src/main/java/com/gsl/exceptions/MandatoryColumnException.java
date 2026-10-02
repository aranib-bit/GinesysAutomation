package com.gsl.exceptions;

import com.gsl.pages.enums.Finance.Operations.Common.IVoucherListColumn;

/**
 * Thrown by {@code GridColumnMenu.hideColumn(...)} when the caller asks to hide a column whose
 * {@link IVoucherListColumn#mandatory()} is {@code true} (e.g. Voucher No., Voucher Date) —
 * fails fast, before any DOM interaction, rather than letting the app silently refuse (or
 * worse, appear to succeed) the request.
 */
public class MandatoryColumnException extends RuntimeException {

    private final IVoucherListColumn column;

    /** @param column the mandatory column the caller attempted to hide */
    public MandatoryColumnException(IVoucherListColumn column) {
        super(String.format(
                "Cannot hide column '%s' (declared in %s) — it is marked mandatory() = true, "
                + "meaning it is always present and cannot be removed via the app's \"Columns\" "
                + "chooser. If the live app's behavior has genuinely changed, fix mandatory() "
                + "on that enum constant rather than working around this exception.",
                column.headerText(), declaringClassName(column)));
        this.column = column;
    }

    /** The mandatory column the caller attempted to hide. */
    public IVoucherListColumn getColumn() { return column; }

    /** Uses {@code Enum.getDeclaringClass()} where applicable so a constant-specific enum body
     *  (an anonymous subclass) still reports the real enum type, not the anonymous class name. */
    private static String declaringClassName(IVoucherListColumn column) {
        return column instanceof Enum<?> e ? e.getDeclaringClass().getSimpleName() : column.getClass().getSimpleName();
    }
}
