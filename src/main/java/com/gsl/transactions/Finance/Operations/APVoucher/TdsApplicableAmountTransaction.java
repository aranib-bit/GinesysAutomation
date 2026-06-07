package com.gsl.transactions.Finance.Operations.APVoucher;

import com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherTdsAndNetPayable;

import java.math.BigDecimal;

/**
 * Transaction layer for the TDS Applicable Amount field in the
 * "Calculate TDS &amp; Net Payable" popup on AP Voucher.
 *
 * <p>Each static method is an <b>atomic step sequence</b> for one named scenario.
 * This layer contains:
 * <ul>
 *   <li><b>No business decisions</b> — which scenario to run is decided by
 *       {@link com.gsl.businesslogic.Finance.Operations.APVoucher.TdsApplicableAmountBusinessLogic}.</li>
 *   <li><b>No UI locators or waits</b> — all UI interactions are delegated to the
 *       page object primitives in
 *       {@link APVoucherTdsAndNetPayable}.</li>
 *   <li><b>No data loading</b> — values are passed in as parameters by the flow layer.</li>
 * </ul>
 *
 * <h3>Dispatch</h3>
 * Called exclusively by
 * {@link com.gsl.flow.Finance.Operations.APVoucher.TdsApplicableAmountFlow#execute},
 * which receives the resolved
 * {@link com.gsl.enums.Finance.Operations.APVoucher.TdsApplicableAmountBehaviour}
 * from the business logic layer and routes to the matching method here.
 */
public final class TdsApplicableAmountTransaction {

    /**
     * Scenario 1 — {@code GROSS_EQUALS_TDS}:
     * Type Gross Payable; TDS Applicable Amount auto-fills to the same value and is accepted as-is.
     *
     * @param page  the TDS popup page component (UI primitives)
     * @param gross the Gross Payable value to type
     */
    public static void applyGrossEqualsTds(APVoucherTdsAndNetPayable page, BigDecimal gross) {
        page.typeGrossPayable(gross);
        // TDS Applicable Amount auto-fills to gross value — no override needed
    }

    /**
     * Scenario 2 — {@code PARTIAL_EDIT}:
     * Type Gross Payable; TDS Applicable Amount auto-fills; override to a different valid value.
     *
     * @param page      the TDS popup page component (UI primitives)
     * @param gross     the Gross Payable value to type
     * @param tdsAmount the target TDS Applicable Amount (must differ from gross)
     */
    public static void applyPartialEdit(APVoucherTdsAndNetPayable page,
                                        BigDecimal gross, BigDecimal tdsAmount) {
        page.typeGrossPayable(gross);
        page.adjustTdsApplicableAmountViaSpinner(gross, tdsAmount);
    }

    /**
     * Scenario 3 — {@code CLEAR_ERROR_THEN_PARTIAL_EDIT}:
     * Type Gross Payable → TDS auto-fills → clear TDS field (triggers validation error popup)
     * → dismiss error → retype gross value → override to target value.
     *
     * <p>This sequence tests that:
     * <ol>
     *   <li>Clearing the field below the minimum value fires the validation error popup.</li>
     *   <li>The error can be dismissed and the field corrected in the same dialog session.</li>
     *   <li>A partial override is still accepted after recovery.</li>
     * </ol>
     *
     * @param page      the TDS popup page component (UI primitives)
     * @param gross     the Gross Payable value to type
     * @param tdsAmount the final TDS Applicable Amount after partial edit
     */
    public static void applyClearErrorThenPartialEdit(APVoucherTdsAndNetPayable page,
                                                       BigDecimal gross, BigDecimal tdsAmount) {
        page.typeGrossPayable(gross);
        page.clearTdsApplicableAmount();                          // clears field → triggers validation error popup
        page.dismissValidationError();                            // clicks OK; ExtJS restores field to gross
        page.adjustTdsApplicableAmountViaSpinner(gross, tdsAmount); // spinner-down to target
    }

    private TdsApplicableAmountTransaction() {}
}
