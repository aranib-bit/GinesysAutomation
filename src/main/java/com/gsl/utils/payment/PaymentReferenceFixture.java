package com.gsl.utils.payment;

import java.util.ArrayList;
import java.util.List;

/**
 * Combines valid ({@link PaymentReferenceFactory}) and negative ({@link ReferenceMutator})
 * {@link PaymentReference} scenarios for all payment systems into a single flat list consumed
 * by {@code PaymentDataProvider}.
 *
 * Order per system: valid first, then all negatives — keeps failures
 * clearly associated with their parent valid case in test reports.
 */
public final class PaymentReferenceFixture {

    private PaymentReferenceFixture() {}

    /** @return every system's valid {@link PaymentReference} immediately followed by its {@link ReferenceMutator}-derived negatives, in {@link PaymentReferenceFactory} system order. */
    public static List<PaymentReference> allScenarios() {
        List<PaymentReference> all = new ArrayList<>();
        for (PaymentReference valid : PaymentReferenceFactory.validReferences()) {
            all.add(valid);
            all.addAll(ReferenceMutator.generateNegatives(valid));
        }
        return all;
    }
}
