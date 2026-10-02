package com.gsl.validation.ui;

import com.gsl.enums.VoucherDomain;

/**
 * Thread-local "which voucher am I validating" for the style baseline. A voucher's Add/Edit page
 * sets it when it is created ({@link #set}); {@link UiStyleBaseline} then resolves expected values
 * from that voucher's sheet first and the {@code Common} sheet second. Outside any voucher (no
 * scope set) only {@code Common} applies. Reset at the start of every test by {@code TestListener}.
 */
public final class UiStyleScope {

    private UiStyleScope() {}

    private static final ThreadLocal<VoucherDomain> CURRENT = new ThreadLocal<>();

    public static void set(VoucherDomain domain) {
        CURRENT.set(domain);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** The current voucher's baseline sheet name ({@code GV}/{@code AR}/{@code AP}/...), or null. */
    static String sheetName() {
        VoucherDomain d = CURRENT.get();
        return d == null || d == VoucherDomain.COMMON ? null : d.name();
    }
}
