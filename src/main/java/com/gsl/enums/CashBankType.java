package com.gsl.enums;

/**
 * Distinguishes Cash GLs (fingl.srctype='C') from Bank GLs (fingl.srctype='B').
 *
 * <p>Used throughout the AR Voucher fixture chain to drive GL selection and enforce
 * payment-mode rules per GL type:
 * <ul>
 *   <li>{@link #CASH} — all SL lines must carry {@link PaymentSelectionType#CASH} ('H')</li>
 *   <li>{@link #BANK} — payment mode may be NONE ('C' default), CHEQUE ('C'), or NEFT/RTGS ('R')</li>
 * </ul>
 */
public enum CashBankType {

    CASH("C", "Cash"),
    BANK("B", "Bank");

    private final String srcType;
    private final String label;

    CashBankType(String srcType, String label) {
        this.srcType = srcType;
        this.label   = label;
    }

    /** Value stored in {@code fingl.srctype}. */
    public String srcType() { return srcType; }

    /** Human-readable label for test case names and log output. */
    public String label() { return label; }

    /** Derives a {@code CashBankType} from a {@code fingl.srctype} character value. */
    public static CashBankType fromSrctype(String s) {
        return "C".equalsIgnoreCase(s) ? CASH : BANK;
    }
}
