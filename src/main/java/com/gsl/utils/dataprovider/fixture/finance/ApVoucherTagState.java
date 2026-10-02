package com.gsl.utils.dataprovider.fixture.finance;

/**
 * The combination dimension {@code DbApVoucherRevertDataSource}/{@code DbApVoucherDeleteDataSource}/
 * {@code DbApVoucherEditDataSource} claim one live document per cell of — whether the claimed
 * document has a live {@code fintag} row (on either {@code entcode1}/{@code entcode2}) or not.
 * This is deliberately a SEPARATE, distinctly-named enum from the existing {@link ApVoucherTag}
 * (AP Create's own dimensional/feature/path tag taxonomy, used by the {@code apv.tag} filter) —
 * the two are unrelated concepts that happen to share a naming prefix: {@link ApVoucherTag} tags
 * a Create-mode combination row with WHAT is being tested (advance vs. document payment, GL type,
 * etc.); this enum instead selects WHICH pre-existing live document a Revert/Delete/Edit test
 * claims, based purely on whether the Document Adjustment "untag" precondition applies to it.
 */
public enum ApVoucherTagState {

    /** The claimed document has at least one live {@code fintag} row (either {@code entcode1} or
     *  {@code entcode2} matches its {@code vchcode}) — exercises the untag-before-Revert/Delete/
     *  Edit precondition ({@code DocumentAdjustmentFlow#untagIfNeeded}). */
    TAGGED,

    /** The claimed document has no {@code fintag} row on either side — the untag step is a
     *  confirmed no-op for this row. */
    UNTAGGED
}
