package com.gsl.utils.payment;

import java.util.List;

/**
 * Produces one valid {@link PaymentReference} per payment system, sourced from {@link
 * PaymentReferenceGenerator}. Every call to {@link #validReferences()} invokes the generators
 * fresh, so values are unique across test runs. Consumed by {@link PaymentReferenceFixture}
 * as the "valid" half of each system's scenario set.
 */
public final class PaymentReferenceFactory {

    private PaymentReferenceFactory() {}

    /** @return one valid, freshly-generated {@link PaymentReference} for each supported payment system (IMPS, UPI, NEFT, RTGS, SWIFT, ACH, SEPA). */
    public static List<PaymentReference> validReferences() {
        return List.of(
            new PaymentReference("IMPS",  PaymentReferenceGenerator.generateIMPS(),  true, "Valid IMPS"),
            new PaymentReference("UPI",   PaymentReferenceGenerator.generateUPI(),   true, "Valid UPI"),
            new PaymentReference("NEFT",  PaymentReferenceGenerator.generateNEFT(),  true, "Valid NEFT"),
            new PaymentReference("RTGS",  PaymentReferenceGenerator.generateRTGS(),  true, "Valid RTGS"),
            new PaymentReference("SWIFT", PaymentReferenceGenerator.generateSWIFT(), true, "Valid SWIFT"),
            new PaymentReference("ACH",   PaymentReferenceGenerator.generateACH(),   true, "Valid ACH"),
            new PaymentReference("SEPA",  PaymentReferenceGenerator.generateSEPA(),  true, "Valid SEPA")
        );
    }
}
