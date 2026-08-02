package com.gsl.reports;

import com.gsl.database.DatabaseManager;
import com.gsl.enums.CashBankType;

import java.time.format.DateTimeFormatter;

/**
 * Shared static utilities for all Finance Voucher report loggers.
 *
 * Extracted from {@code ApVoucherExtentDescriptionLogger} and
 * {@code ArVoucherExtentDescriptionLogger} where identical copies previously existed.
 */
public final class VoucherReportUtils {

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private VoucherReportUtils() {}

    /** Returns {@code value.toString()} or {@code ""} if null. */
    public static String nullSafe(Object value) {
        return value != null ? value.toString() : "";
    }

    /**
     * Looks up the Cash/Bank type label for a GL name and returns it as " [Cash]" / " [Bank]".
     * Returns {@code ""} when the name is blank or the lookup fails.
     */
    public static String resolveCashBankType(String cashBankName) {
        if (cashBankName == null || cashBankName.isBlank()) return "";
        try {
            String srctype = DatabaseManager.getJdbi().withHandle(handle ->
                    handle.createQuery("SELECT srctype FROM fingl WHERE glname = :name")
                          .bind("name", cashBankName)
                          .mapTo(String.class)
                          .findFirst()
                          .orElse(null));
            if (srctype == null) return "";
            return " [" + CashBankType.fromSrctype(srctype).label() + "]";
        } catch (Exception e) {
            return "";
        }
    }
}
