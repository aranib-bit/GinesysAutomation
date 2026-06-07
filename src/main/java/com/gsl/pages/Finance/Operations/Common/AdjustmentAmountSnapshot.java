package com.gsl.pages.Finance.Operations.Common;

import lombok.Value;

/** Immutable snapshot of the three footer amounts read from Adjustment Entry after Auto Adjust. */
@Value
public class AdjustmentAmountSnapshot {
    String transactionAmount;
    String adjustedAmount;
    String pendingToAdjust;
}
