package com.gsl.enums;

/**
 * Labels for {@code finvchmain.release_status} / {@code finpost.release_status} — documented
 * inline on {@code FinPost.releaseStatus}'s own Javadoc: {@code 'N'} = never posted, {@code 'U'}
 * = unposted (draft), {@code 'P'} = posted (cross-referenced against {@code main.v_pending_adj}).
 * Same shape as {@link PaymentSelectionType}, added purely for report display — resolving a bare
 * status letter to a human-readable label in {@code SnapshotValidationReporter}.
 */
public enum VoucherReleaseStatus {

    NEVER_POSTED("N", "Never posted"),
    UNPOSTED("U", "Unposted"),
    POSTED("P", "Posted");

    private final String dbCode;
    private final String label;

    VoucherReleaseStatus(String dbCode, String label) {
        this.dbCode = dbCode;
        this.label = label;
    }

    /** Single-character code stored in {@code release_status}. */
    public String dbCode() {
        return dbCode;
    }

    /** Human-readable label for report output. */
    public String label() {
        return label;
    }

    /** Reverse lookup — resolves a raw {@code release_status} DB value ('N'/'U'/'P') back to its
     *  enum/label, for report display. {@code null} for a blank/unrecognized code (never throws —
     *  mirrors {@link PaymentSelectionType#fromDbCode}). */
    public static VoucherReleaseStatus fromCode(String dbCode) {
        if (dbCode == null || dbCode.isBlank()) return null;
        String trimmed = dbCode.trim();
        for (VoucherReleaseStatus status : values()) {
            if (status.dbCode.equalsIgnoreCase(trimmed)) return status;
        }
        return null;
    }

    @Override
    public String toString() {
        return label;
    }
}
