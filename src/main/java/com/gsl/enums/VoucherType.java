package com.gsl.enums;

import java.math.BigDecimal;

/**
 * Distinguishes Finance voucher types by {@code finvchmain.vchtype}.
 *
 * <p>Only {@link #AP} and {@link #AR} are currently validated (AR/AP Voucher DB validators);
 * {@link #GENERAL} and {@link #CONTRA} are reserved for when those voucher flows are automated.
 */
public enum VoucherType {

    AP("P", "AP Voucher") {
        @Override public String mainPostingEntType() { return "VMP"; }
        // Payment document — header's Cr always carries the total of the detail Dr lines
        // (confirmed live/proven by FinVchDetApValidator's Create-side formula:
        // finvchmain.camount == SUM(finvchdet.damount)).
        @Override public AmountPair expectedHeaderAmount(BigDecimal detailSumDamount, BigDecimal detailSumCamount) {
            return new AmountPair(BigDecimal.ZERO, detailSumDamount);
        }
    },
    AR("R", "AR Voucher") {
        @Override public String mainPostingEntType() { return "VMP"; }
        // Receivable document — header's Dr always carries the total of the detail Cr lines
        // (confirmed live/proven by FinVchDetValidator's Create-side formula:
        // finvchmain.damount == SUM(finvchdet.camount)).
        @Override public AmountPair expectedHeaderAmount(BigDecimal detailSumDamount, BigDecimal detailSumCamount) {
            return new AmountPair(detailSumCamount, BigDecimal.ZERO);
        }
    },
    GENERAL("G", "General Voucher") {
        @Override public String mainPostingEntType() { return "VMG"; }
        // Net of all detail lines determines the header's counter-leg side — net Dr (more Dr
        // than Cr across finvchdet) means the header carries the balancing amount on Cr, and
        // vice versa (per business rule: "General Voucher net of dr and cr amount, if net is cr
        // then main will be dr else the opposite"). Supersedes the old Create-only validator's
        // single-line simplification (GeneralVoucherDbValidator.validateCounterLegPaymentInfo()
        // only ever compared against rows.get(0)) with the true net across every line.
        @Override public AmountPair expectedHeaderAmount(BigDecimal detailSumDamount, BigDecimal detailSumCamount) {
            BigDecimal net = detailSumDamount.subtract(detailSumCamount);
            int cmp = net.signum();
            if (cmp > 0) return new AmountPair(BigDecimal.ZERO, net);            // net Dr -> header Cr
            if (cmp < 0) return new AmountPair(net.negate(), BigDecimal.ZERO);   // net Cr -> header Dr
            return new AmountPair(BigDecimal.ZERO, BigDecimal.ZERO);
        }
    },
    CONTRA("C", "Contra Voucher") {
        @Override public String mainPostingEntType() {
            throw new UnsupportedOperationException(
                    "CONTRA's finpost.enttype not yet confirmed — see class Javadoc");
        }
        @Override public AmountPair expectedHeaderAmount(BigDecimal detailSumDamount, BigDecimal detailSumCamount) {
            throw new UnsupportedOperationException(
                    "CONTRA's header-amount formula not yet confirmed — see class Javadoc");
        }
    };

    /** The expected {@code finvchmain} (and {@code finpost} main-grid row) {@code damount}/
     *  {@code camount} pair for a given domain, computed from the full set of {@code finvchdet}
     *  rows' summed damount/camount. */
    public record AmountPair(BigDecimal damount, BigDecimal camount) {}

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

    /**
     * {@code finpost.enttype} value for this domain's header-level "main grid" posting row —
     * used by {@code VoucherEditChangeSets.headerEdit()} to scope the {@code nartext}/{@code
     * docno} MUST_CHANGE rule to only the header row, not every {@code finpost} row for the
     * document (per-line posting rows carry their own unrelated narration). GV uses {@code VMG}
     * (Voucher Main Grid); AR/AP share {@code VMP} (Voucher Main Posting) — confirmed via
     * {@code SELECT * FROM finenttype WHERE enttype IN ('VMP','VDP')}, both rows labeled
     * "Voucher (AR/AP)". {@code CONTRA} throws — not yet automated (see class Javadoc), matching
     * this codebase's established not-yet-implemented convention (e.g. {@code
     * VoucherDomain.CONTRA}'s other methods).
     */
    public abstract String mainPostingEntType();

    /**
     * Computes the expected {@code finvchmain} {@code damount}/{@code camount} pair for this
     * domain from the (after-edit) sum of every {@code finvchdet} row's own {@code damount}/
     * {@code camount} — used by {@code VoucherEditChangeSets.detailLineEdit()} to assert the
     * header (and {@code finpost}'s main-grid row, which mirrors it) balances against the detail
     * lines after an amount-changing edit, per each domain's own accounting treatment (GV: net of
     * all lines, opposite side; AP: payment document, header Cr = sum of detail Dr; AR:
     * receivable document, header Dr = sum of detail Cr). Does not apply to {@code fincosttag}
     * (site-allocation tagging, structurally separate from Dr/Cr balancing).
     */
    public abstract AmountPair expectedHeaderAmount(BigDecimal detailSumDamount, BigDecimal detailSumCamount);

    /** Derives a {@code VoucherType} from a {@code finvchmain.vchtype} character value. */
    public static VoucherType fromCode(String code) {
        for (VoucherType v : values()) {
            if (v.code.equalsIgnoreCase(code)) return v;
        }
        throw new IllegalArgumentException("Unknown finvchmain.vchtype code: " + code);
    }
}
