package com.gsl.pages.enums.Finance.Operations.APVoucher;

/**
 * Outcome of {@code APVoucherMainGrid#confirmRevert()} — which of the live-confirmed post-Revert
 * dialogs actually appeared, and therefore whether {@code release_status} is expected to change.
 * Mirrors {@code GeneralVoucherRevertOutcome}'s exact 4 constants/Javadoc shape — the underlying
 * dialogs ({@link com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage}'s
 * {@code REVERT_*} constants) are explicitly documented as shared framework-wide across AP/AR/GV,
 * not GV-specific text, so the same 4 outcomes apply here unchanged.
 */
public enum APVoucherRevertOutcome {

    /** Document was Posted; the "will be un-posted, proceed?" dialog appeared and Yes was
     *  clicked — release_status is expected to change. */
    UNPOSTED,

    /** Document was already un-posted; the informational "already in un-posted state" dialog
     *  appeared and was dismissed — release_status is expected to stay unchanged. */
    ALREADY_UNPOSTED,

    /** Document's line(s) are already bank-reconciled (BRS); the "cannot be reverted"
     *  informational dialog appeared and was dismissed — release_status is expected to stay
     *  unchanged, since Revert never proceeded. */
    CANNOT_REVERT_BRS_DONE,

    /** Document's own accounting year or financial month is already closed; the "Finance month
     *  is already closed. Cannot be reverted." informational dialog appeared and was dismissed —
     *  release_status is expected to stay unchanged, since Revert never proceeded. */
    CANNOT_REVERT_MONTH_CLOSED
}
