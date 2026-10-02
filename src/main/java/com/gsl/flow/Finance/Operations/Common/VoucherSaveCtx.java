package com.gsl.flow.Finance.Operations.Common;

import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.validation.CombinedValidationError;
import org.testng.asserts.SoftAssert;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable hand-off between the common save-flow steps of one voucher test invocation — replaces
 * the per-behavior {@code String[]/DocumentMessageBox[]/DocumentPopup[]} arrays every AR/AP/GV
 * Create and Edit flow used to declare for the same purpose. Not shared across threads: one
 * instance per test invocation.
 *
 * <p>{@link #deferred} holds failures of steps that ran via {@link VoucherSaveFlowSteps#softStep}
 * (they do not abort the flow); {@link VoucherSaveFlowSteps#finalizeAsserts} reports them together.
 */
public final class VoucherSaveCtx {
    public String docNo;
    public DocumentMessageBox msgBox;
    public DocumentPopup popup;
    public final SoftAssert softAssert = new SoftAssert();
    public final List<CombinedValidationError.Area> deferred = new ArrayList<>();
}
