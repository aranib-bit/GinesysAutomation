package com.gsl.flow.Finance.Operations.Common;

import com.gsl.driver.HealthChecker;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.utils.Log;
import com.gsl.validation.CombinedValidationError;
import com.gsl.validation.ui.UiFailureCollector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The single home for the save → post-save dialogs → verify → close → logout → DB-validate →
 * finalize tail that every AR/AP/GV Create and Edit flow previously re-inlined (same step names,
 * same order — a behavior-preserving extraction, so reports read the same as before).
 *
 * <p>Each method is one {@link StepExecutor#step} (the step name is the report contract) and is
 * deliberately fine-grained rather than one "run it all" call: GV interleaves extra steps (balance
 * check, snapshot validation) and Edit adds snapshots around the tail. Domain-specific work —
 * which page's {@code clickSave()}/{@code close()} to call, which DB validator to run — is passed
 * in as a lambda, since the three Add pages share no common interface for those calls.
 *
 * <p>The page-close step carries the {@link HealthChecker#isModalWindowOpen()} guard first proven
 * in {@code EditApVoucher}: whether the Add/Edit window auto-closes after the post-save release
 * flow is role-dependent, so an unconditional close click can find nothing to click.
 */
public final class VoucherSaveFlowSteps {

    private VoucherSaveFlowSteps() {}

    /** Step "Click Save". {@code saveAction} is the page's own {@code clickSave()}; {@code beforeSave}
     *  (optional) runs immediately before it, e.g. {@code VoucherDbContextBuilder.markSaveStart()}. */
    public static void clickSave(VoucherSaveCtx ctx, Runnable beforeSave, Supplier<DocumentPopup> saveAction) {
        StepExecutor.step("Click Save", () -> {
            if (beforeSave != null) beforeSave.run();
            ctx.popup = saveAction.get();
        });
    }

    /** Step "Handle Post-Save Dialogs" — {@link PostSaveDialogHandler} keyed by domain. */
    public static void handlePostSaveDialogs(VoucherSaveCtx ctx, VoucherDomain domain,
                                             PostSaveDialogHandler.PostSaveSpec spec) {
        handlePostSaveDialogs(ctx, domain, spec, null);
    }

    /** As above; {@code afterHandled} (optional) runs inside the same step, e.g. GV's step-summary line. */
    public static void handlePostSaveDialogs(VoucherSaveCtx ctx, VoucherDomain domain,
                                             PostSaveDialogHandler.PostSaveSpec spec, Runnable afterHandled) {
        StepExecutor.step("Handle Post-Save Dialogs", () -> {
            ctx.msgBox = PostSaveDialogHandler.forDomain(domain).handle(ctx.popup, spec);
            if (afterHandled != null) afterHandled.run();
        });
    }

    /**
     * Step "Verify Save Result" — records the document number on {@code ctx}. Header mismatches go
     * to {@code ctx.softAssert} (asserted later by {@link #finalizeAsserts}); Edit flows that want
     * a hard failure here call {@code ctx.softAssert.assertAll()} themselves right after.
     */
    public static void verifySaveResult(VoucherSaveCtx ctx) {
        StepExecutor.step("Verify Save Result", () -> {
            ctx.docNo = ctx.msgBox.verifyDocumentSave(ctx.softAssert).getDocumentNo();
            Log.info("Extracted Document No: {}", ctx.docNo);
            ReportLogger.pass("Voucher saved. Document No: " + ctx.docNo);
        });
    }

    /**
     * Step "Close Voucher Page": dismisses the save Result OK, then closes the Add/Edit window via
     * {@code closeViaUi} (the page's own {@code close().clickYes(XListPage::new)}) — or, when the
     * window already closed itself, builds the list page via {@code alreadyClosed}. Returns
     * whichever list page resulted.
     */
    public static <L> L closeVoucherPage(VoucherSaveCtx ctx, Supplier<L> closeViaUi, Supplier<L> alreadyClosed) {
        return closeVoucherPage(ctx, closeViaUi, alreadyClosed, null);
    }

    /** As above; {@code afterClosed} (optional) runs inside the same step. */
    public static <L> L closeVoucherPage(VoucherSaveCtx ctx, Supplier<L> closeViaUi, Supplier<L> alreadyClosed,
                                         Runnable afterClosed) {
        final Object[] result = new Object[1];
        StepExecutor.step("Close Voucher Page", () -> {
            if (ctx.msgBox != null) {
                ctx.msgBox.clickOk();
            }
            if (HealthChecker.isModalWindowOpen()) {
                result[0] = closeViaUi.get();
            } else {
                ReportLogger.business("Add/Edit window already closed itself after the post-save "
                        + "release flow — skipping the explicit close click.");
                result[0] = alreadyClosed.get();
            }
            ReportLogger.business("Voucher page closed, returned to list");
            if (afterClosed != null) afterClosed.run();
        });
        @SuppressWarnings("unchecked") L list = (L) result[0];
        return list;
    }

    /** Step "Logout (deferred)" — the session is returned to the pool, not logged out. */
    public static void deferLogout() {
        StepExecutor.step("Logout (deferred)", () ->
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread."));
    }

    /** Step "Database Validation" — {@code validator} receives the saved document number. A failure
     *  does not abort the flow (the node still turns red): it is recorded on {@code ctx} and reported
     *  by {@link #finalizeAsserts}, so snapshot validation and the UI result are still produced. */
    public static void databaseValidation(VoucherSaveCtx ctx, Consumer<String> validator) {
        softStep(ctx, "Database Validation", CombinedValidationError.Kind.DATABASE, () -> {
            validator.accept(ctx.docNo);
            ReportLogger.pass("DB validated. Document No: " + ctx.docNo);
        });
    }

    /**
     * Runs {@code action} as a report step that does NOT abort the flow when it fails: the node is
     * marked failed as usual, and the failure is recorded on {@code ctx} as one {@code kind} area
     * titled {@code stepName}. {@link #finalizeAsserts} MUST be called later in the same flow — it is
     * what throws.
     */
    public static void softStep(VoucherSaveCtx ctx, String stepName, CombinedValidationError.Kind kind,
                                StepExecutor.StepAction action) {
        Throwable failure = StepExecutor.stepSoft(stepName, action);
        if (failure != null) {
            // StepExecutor wraps checked exceptions as "Step failed: <name>" with the real cause attached.
            Throwable real = (failure.getMessage() != null && failure.getMessage().startsWith("Step failed: ")
                    && failure.getCause() != null) ? failure.getCause() : failure;
            ctx.deferred.add(CombinedValidationError.areaFrom(kind, stepName, real));
        }
    }

    /** Step "Finalize save assertions" — surfaces any deferred soft-assert failure. */
    public static void finalizeAsserts(VoucherSaveCtx ctx) {
        finalizeAsserts(ctx, null);
    }

    /** As above; {@code afterAsserted} (optional) runs once nothing failed. Throws ONE
     *  {@link CombinedValidationError} naming every failed area: deferred UI-contract failures
     *  ({@link UiFailureCollector}), failed soft steps ({@link #softStep}, e.g. Database Validation,
     *  Database Snapshot Validation) and the save-result soft assertions. */
    public static void finalizeAsserts(VoucherSaveCtx ctx, Runnable afterAsserted) {
        StepExecutor.step("Finalize save assertions", () -> {
            List<CombinedValidationError.Area> areas = new ArrayList<>();
            com.gsl.validation.ui.UiValidationReporter.flushHeld();
            if (UiFailureCollector.hasPending()) {
                areas.add(CombinedValidationError.uiArea(UiFailureCollector.pending()));
                UiFailureCollector.clear();
            }
            areas.addAll(ctx.deferred);
            ctx.deferred.clear();
            try {
                ctx.softAssert.assertAll();
            } catch (AssertionError e) {
                areas.add(CombinedValidationError.areaFrom(
                        CombinedValidationError.Kind.OTHER, "Save-result assertions", e));
            }
            if (!areas.isEmpty()) throw new CombinedValidationError(areas);
            if (afterAsserted != null) afterAsserted.run();
        });
    }
}
