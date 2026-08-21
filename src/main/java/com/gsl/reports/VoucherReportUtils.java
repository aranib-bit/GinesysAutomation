package com.gsl.reports;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.enums.CashBankType;
import com.gsl.utils.Log;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
     * Formats an amount (damount/camount and similar) for report display — {@code "—"} for
     * null/zero, otherwise 2-decimal digit-grouped per {@code report.amount.numbering.locale}
     * (Indian grouping by default, e.g. {@code 12,34,567.89}; configurable to Western/other
     * styles — no code change needed to add a new style).
     *
     * <p>Single consolidation point for the amount-formatting logic previously duplicated
     * across {@code ActualResultsLogger} (5 {@code String.format("%,.2f", v)} call sites) and
     * {@code GeneralVoucherExtentDescriptionLogger} (raw, ungrouped {@code toPlainString()}).
     *
     * <p>The {@link Locale} is resolved here, in plain code, from {@code ReportConfig}'s
     * String-typed {@code amountNumberingLocaleTag()} accessor — deliberately NOT via a
     * {@code default} method on the {@code Config} interface itself: Owner's dynamic proxy
     * routes every call, {@code default} methods included, through its own property-resolution
     * {@code InvocationHandler} rather than running the real default-method body, so an
     * interface-level {@code default Locale amountNumberingLocale()} silently resolved to
     * {@code null} and NPE'd deep inside {@code NumberFormat.getInstance(null)}.
     *
     * <p><b>India ({@code en-IN}/any {@code *-IN} tag) is hand-formatted</b>, not delegated to
     * {@link NumberFormat} — confirmed empirically (this JDK's {@code en-IN} CLDR data resolves
     * to the plain {@code "#,##0.###"} pattern, groupingSize 3) that {@link java.text.DecimalFormat}
     * supports only ONE uniform grouping size and cannot produce the Indian 3-then-repeating-2
     * pattern (12,34,567.89) at all, even from a pattern string with two different comma
     * spacings — it silently collapses to the rightmost (3-digit) spacing throughout. Every
     * other locale (Western 3-3-3, Dubai/UAE, etc.) uses a single uniform grouping size, which
     * {@code NumberFormat} handles correctly, so only the Indian case needs this workaround.
     */
    public static String fmtAmount(BigDecimal v) {
        if (v == null || v.compareTo(BigDecimal.ZERO) == 0) return "—";
        Locale locale = resolveAmountNumberingLocale();
        if ("IN".equalsIgnoreCase(locale.getCountry())) {
            return formatIndianGrouping(v);
        }
        NumberFormat nf = NumberFormat.getNumberInstance(locale);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(v);
    }

    /** Resolves the configured amount-numbering locale, falling back to {@code en-IN} (and
     *  logging a warning) if the tag is missing/blank — {@link Locale#forLanguageTag} never
     *  throws or returns null itself, but a config-read failure upstream could still hand this
     *  a null/blank tag, and {@link NumberFormat#getInstance(Locale)} NPEs on a null
     *  {@link Locale}, so this never returns null. */
    private static Locale resolveAmountNumberingLocale() {
        String tag = ConfigFactory.getReportConfig().amountNumberingLocaleTag();
        if (tag == null || tag.isBlank()) {
            Log.warn("[VoucherReportUtils] report.amount.numbering.locale missing/blank — defaulting to en-IN");
            return Locale.forLanguageTag("en-IN");
        }
        return Locale.forLanguageTag(tag);
    }

    /**
     * Indian digit grouping — last 3 integer digits form one group, every group before that is
     * 2 digits (e.g. {@code 1234567.89} → {@code "12,34,567.89"}), which {@link NumberFormat}
     * cannot produce (see {@link #fmtAmount}'s Javadoc). Always 2 decimal places, HALF_UP
     * rounding — same convention {@code NumberFormat}'s min/max-fraction-digits(2) enforces on
     * the non-Indian path.
     */
    private static String formatIndianGrouping(BigDecimal v) {
        boolean negative = v.signum() < 0;
        String plain = v.abs().setScale(2, RoundingMode.HALF_UP).toPlainString();
        int dot = plain.indexOf('.');
        String intPart = plain.substring(0, dot);
        String fracPart = plain.substring(dot);

        String grouped;
        int len = intPart.length();
        if (len <= 3) {
            grouped = intPart;
        } else {
            String lastThree = intPart.substring(len - 3);
            String remaining = intPart.substring(0, len - 3);
            StringBuilder sb = new StringBuilder();
            int i = remaining.length();
            while (i > 2) {
                sb.insert(0, "," + remaining.substring(i - 2, i));
                i -= 2;
            }
            sb.insert(0, remaining.substring(0, i));
            grouped = sb + "," + lastThree;
        }
        return (negative ? "-" : "") + grouped + fracPart;
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
