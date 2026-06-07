package com.gsl.flow.Finance.Operations.APVoucher;

import com.gsl.businesslogic.Finance.Operations.APVoucher.TdsApplicableAmountBusinessLogic;
import com.gsl.enums.Finance.Operations.APVoucher.TdsApplicableAmountBehaviour;
import com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherTdsAndNetPayable;
import com.gsl.transactions.Finance.Operations.APVoucher.TdsApplicableAmountTransaction;

import java.math.BigDecimal;

/**
 * Flow layer for the TDS Applicable Amount field in the
 * "Calculate TDS &amp; Net Payable" popup on AP Voucher.
 *
 * <p>This layer:
 * <ol>
 *   <li>Asks the <b>business logic layer</b> to resolve WHAT to do
 *       ({@link TdsApplicableAmountBusinessLogic#resolve}) — no conditions here.</li>
 *   <li>Dispatches to the <b>transaction layer</b> which knows HOW to do it
 *       ({@link TdsApplicableAmountTransaction}) — no UI knowledge here.</li>
 * </ol>
 *
 * <p>This class contains <b>no if/else conditions</b>, no data comparisons,
 * and no UI locators. It is purely an orchestration dispatch.
 *
 * <h3>Call chain</h3>
 * <pre>
 * APVoucherTdsAndNetPayable.fillTdsRows()   [Page Object — passes raw string]
 *     → TdsApplicableAmountFlow.execute()   [Flow — resolves + dispatches]
 *         → TdsApplicableAmountBusinessLogic.resolve()   [Business Logic — WHAT]
 *         → TdsApplicableAmountTransaction.apply*()      [Transaction — HOW]
 *             → APVoucherTdsAndNetPayable.*()             [Page Object — UI primitives]
 * </pre>
 */
public final class TdsApplicableAmountFlow {

    /**
     * Resolves the TDS behaviour from the raw data string and dispatches to the
     * matching transaction method.
     *
     * @param behaviourString       raw string from {@code TdsAndNetPayable.tdsApplicableAmountBehaviour}
     *                              (may be null or blank — defaults to GROSS_EQUALS_TDS)
     * @param page                  the TDS popup page component (UI primitives)
     * @param gross                 Gross Payable value from test data
     * @param tdsApplicableAmount   TDS Applicable Amount value from test data (may be null)
     */
    public static void execute(String behaviourString,
                               APVoucherTdsAndNetPayable page,
                               BigDecimal gross,
                               BigDecimal tdsApplicableAmount) {

        // Step 1: Business logic decides WHAT to do (all conditions live in BusinessLogic)
        TdsApplicableAmountBehaviour behaviour =
                TdsApplicableAmountBusinessLogic.resolve(behaviourString, gross, tdsApplicableAmount);

        // Step 2: Transaction decides HOW to do it (no conditions here — pure dispatch)
        switch (behaviour) {
            case GROSS_EQUALS_TDS ->
                TdsApplicableAmountTransaction.applyGrossEqualsTds(page, gross);
            case PARTIAL_EDIT ->
                TdsApplicableAmountTransaction.applyPartialEdit(page, gross, tdsApplicableAmount);
            case CLEAR_ERROR_THEN_PARTIAL_EDIT ->
                TdsApplicableAmountTransaction.applyClearErrorThenPartialEdit(
                        page, gross, tdsApplicableAmount);
        }
    }

    private TdsApplicableAmountFlow() {}
}
