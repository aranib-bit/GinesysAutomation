package com.gsl.enums;

/**
 * Accounting classification stored in {@code fingl.type} — Assets, Liability, Income,
 * or Expense. Confirmed (user-provided) to be stored as single-letter codes.
 *
 * <p>Drives the General Voucher combination matrix's amount-side rule
 * (see {@code GeneralVoucherCombinations}): {@link #EXPENSE} rows always post Debit,
 * {@link #INCOME} rows always post Credit, {@link #ASSET}/{@link #LIABILITY} rows are
 * tested on both sides.
 */
public enum GlAccountType {

    ASSET("A", "Assets"),
    LIABILITY("L", "Liability"),
    INCOME("I", "Income"),
    EXPENSE("E", "Expense");

    private final String code;
    private final String label;

    GlAccountType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** Value stored in {@code fingl.type}. */
    public String code() { return code; }

    /** Human-readable label for test case names and log output. */
    public String label() { return label; }

    /**
     * Derives a {@code GlAccountType} from a {@code fingl.type} code.
     *
     * <p>Unlike {@link CashBankType#fromSrctype}'s lenient default, this throws on an
     * unrecognized code — bad/unexpected master data should surface immediately rather
     * than silently misclassify a GL's accounting type.
     */
    public static GlAccountType fromCode(String s) {
        for (GlAccountType t : values()) {
            if (t.code.equalsIgnoreCase(s)) return t;
        }
        throw new IllegalArgumentException(
                "Unknown fingl.type code: '" + s + "' — expected one of A/L/I/E "
                + "(Assets/Liability/Income/Expense).");
    }

    /** {@code true} for {@link #ASSET}/{@link #LIABILITY} — both Debit and Credit are tested. */
    public boolean allowsBothSides() {
        return this == ASSET || this == LIABILITY;
    }

    /** {@code true} for {@link #EXPENSE} — Debit-only per the user-confirmed amount-side rule. */
    public boolean isDebitOnly() { return this == EXPENSE; }

    /** {@code true} for {@link #INCOME} — Credit-only per the user-confirmed amount-side rule. */
    public boolean isCreditOnly() { return this == INCOME; }
}
