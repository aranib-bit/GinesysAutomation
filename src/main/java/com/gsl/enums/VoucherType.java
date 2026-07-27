package com.gsl.enums;

/**
 * Distinguishes Finance voucher types by {@code finvchmain.vchtype}.
 *
 * <p>Only {@link #AP} and {@link #AR} are currently validated (AR/AP Voucher DB validators);
 * {@link #GENERAL} and {@link #CONTRA} are reserved for when those voucher flows are automated.
 */
public enum VoucherType {

    AP("P", "AP Voucher"),
    AR("R", "AR Voucher"),
    GENERAL("G", "General Voucher"),
    CONTRA("C", "Contra Voucher");

    private final String code;
    private final String label;

    VoucherType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** Value stored in {@code finvchmain.vchtype}. */
    public String code() { return code; }

    /** Human-readable label for report output. */
    public String label() { return label; }

    /** Derives a {@code VoucherType} from a {@code finvchmain.vchtype} character value. */
    public static VoucherType fromCode(String code) {
        for (VoucherType v : values()) {
            if (v.code.equalsIgnoreCase(code)) return v;
        }
        throw new IllegalArgumentException("Unknown finvchmain.vchtype code: " + code);
    }
}
