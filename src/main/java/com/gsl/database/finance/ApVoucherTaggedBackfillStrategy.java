package com.gsl.database.finance;

import com.gsl.database.BackfillStrategy;
import com.gsl.enums.ApiPlatform;
import org.testng.SkipException;

/**
 * {@code DbApVoucherRevertDataSource}/{@code DbApVoucherDeleteDataSource}/
 * {@code DbApVoucherEditDataSource}'s {@code TAGGED}-cell shortfall backfill.
 *
 * <p><b>Currently non-functional by design</b> — this repo has no confirmed "create an AP Voucher
 * AND tag it against a counter-document" API capture; the only Document Adjustment HAR captures
 * supplied (two, re-checked independently) cover only the opposite direction (<i>untagging</i> an
 * already-tagged document — see {@code DocumentAdjustmentApiClient}'s Javadoc). Fabricating a
 * "tagged" row by creating a plain untagged voucher and mislabeling it {@code TAGGED} would
 * silently corrupt the very scenario this combination cell exists to exercise (the
 * untag-before-Revert/Delete/Edit precondition would never actually engage).
 *
 * <p><b>Throws {@link SkipException}, not {@code UnsupportedOperationException}</b> — a deliberate
 * choice, not the same one {@link GeneralVoucherApiBackfillStrategy}/{@code GeneralVoucherApiClient
 * .createGeneralVoucherWithDefaults} make for their own not-yet-captured Create flows. Those guard
 * core, always-buildable functionality, so a loud hard failure is the right prompt to eventually
 * build them. This case is different: untagging a live document via Revert/Delete/Edit is
 * one-way and irreversible, and the currently-known live {@code TAGGED} candidates are a finite,
 * manually-curated resource that does not regenerate — once early test runs consume them, EVERY
 * later run of this cell would hit whatever this throws. {@code SkipException} is exactly what
 * {@code DataAvailabilityGuard.selectOrBackfill} is already built around for a genuine, unfixable
 * data-availability shortfall (its own Javadoc: "If still short after backfill, the shortfall is
 * real and the original SkipException propagates") — it's still a {@code RuntimeException}, so it
 * propagates through {@code selectOrBackfill} exactly the same way an unchecked exception would,
 * but TestNG reports the {@code TAGGED} cell as SKIPPED once the live supply runs out, not FAILED —
 * the correct, expected long-run signal for a resource that's meant to run out, not a bug to chase.
 *
 * <p>If a real backfill is ever wanted instead of this stub, the concrete next step is a fresh HAR
 * of the UI actually ADDING an adjustment (Document Adjustment screen → select a base document →
 * pick a document to adjust against → Save) — not another untag capture.
 */
public final class ApVoucherTaggedBackfillStrategy implements BackfillStrategy {

    @Override
    public void backfill(ApiPlatform platform, int neededQuantity) {
        throw new SkipException(
                "ApVoucherTaggedBackfillStrategy.backfill: no confirmed 'create AP Voucher AND tag it against "
                + "a counter-document' API capture exists in this codebase (only the opposite 'untag' "
                + "direction was captured — see DocumentAdjustmentApiClient's Javadoc). No live unclaimed "
                + "TAGGED AP Voucher was found either, so this combination cell is skipped for this run "
                + "rather than fabricating an untagged voucher and mislabeling it TAGGED. "
                + "neededQuantity=" + neededQuantity + ", platform=" + platform);
    }

    /** {@inheritDoc} Never actually reached — {@link #backfill} always throws before any platform
     *  routing decision would consult this. */
    @Override
    public String k6ScriptKey() {
        return "apVoucherTaggedBackfill";
    }
}
