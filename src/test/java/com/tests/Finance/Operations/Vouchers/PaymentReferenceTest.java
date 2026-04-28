package com.tests.Finance.Operations.Vouchers;

import com.gsl.utils.dataprovider.PaymentDataProvider;
import com.gsl.utils.payment.PaymentReference;
import com.gsl.utils.payment.PaymentReferenceValidator;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Validates the PaymentReferenceGenerator + PaymentReferenceValidator pipeline.
 *
 * Each DataProvider row is either a valid fixture (isValid=true, must pass)
 * or a negative mutation (isValid=false, must fail). The assertion message
 * surfaces system + scenario + value for fast triage without re-running.
 */
public class PaymentReferenceTest {

    @Test(dataProvider = "paymentRefs", dataProviderClass = PaymentDataProvider.class)
    public void testReference(PaymentReference ref) {
        boolean actual = PaymentReferenceValidator.validate(ref);
        Assert.assertEquals(
                actual,
                ref.isValid,
                "Mismatch — system=" + ref.system
                        + " | scenario=" + ref.scenario
                        + " | value=" + ref.value
        );
    }
}
