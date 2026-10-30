package com.gsl.pages.enums.Finance.Operations.Common;

/**
 * Outcome of a voucher main grid's {@code confirmRevert()} (GV, AR and AP share it) — which of the three live-confirmed
 * post-Revert dialogs actually appeared, and therefore whether {@code release_status} is
 * expected to change.
 */
public enum VoucherRevertOutcome {

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
