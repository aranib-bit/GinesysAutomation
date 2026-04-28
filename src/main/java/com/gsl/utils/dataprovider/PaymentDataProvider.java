package com.gsl.utils.dataprovider;

import com.gsl.utils.payment.PaymentReferenceFixture;
import org.testng.annotations.DataProvider;

/**
 * TestNG DataProvider for payment reference validation tests.
 * Streams all valid + negative scenarios from PaymentReferenceFixture
 * so each row gets its own test invocation with a clear scenario label.
 */
public final class PaymentDataProvider {

    private PaymentDataProvider() {}

    @DataProvider(name = "paymentRefs")
    public static Object[][] getData() {
        return PaymentReferenceFixture.allScenarios()
                .stream()
                .map(ref -> new Object[]{ref})
                .toArray(Object[][]::new);
    }
}
