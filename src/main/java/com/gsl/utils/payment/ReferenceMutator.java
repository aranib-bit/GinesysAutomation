package com.gsl.utils.payment;

import java.util.ArrayList;
import java.util.List;

/**
 * Derives a set of invalid PaymentReference mutations from a valid one.
 * Each mutation targets a specific validation rule so test failures are
 * unambiguous about which rule was violated.
 */
public final class ReferenceMutator {

    private ReferenceMutator() {}

    public static List<PaymentReference> generateNegatives(PaymentReference valid) {
        List<PaymentReference> list = new ArrayList<>();
        String val = valid.value;
        String sys = valid.system;

        // Short length — drop last character
        if (val != null && val.length() > 1) {
            list.add(neg(sys, val.substring(0, val.length() - 1), "Short length"));
        }

        // Exceeds max length — append extra digit
        list.add(neg(sys, val + "9", "Exceeds max length"));

        // Special character appended
        list.add(neg(sys, val + "@", "Special char appended"));

        // Alpha substituted into a numeric-only system
        if (val != null) {
            list.add(neg(sys, val.replaceAll("[0-9]", "A"), "Alpha-in-numeric substitution"));
        }

        // Lowercase mutation — many systems require uppercase
        if (val != null) {
            list.add(neg(sys, val.toLowerCase(), "Lowercase mutation"));
        }

        // Empty string
        list.add(neg(sys, "", "Empty string"));

        // Null value
        list.add(neg(sys, null, "Null value"));

        // SWIFT-specific: UUID with dashes stripped breaks the format pattern
        if ("SWIFT".equals(sys) && val != null) {
            list.add(neg(sys, val.replace("-", ""), "UUID dashes stripped"));
        }

        return list;
    }

    private static PaymentReference neg(String system, String value, String scenario) {
        return new PaymentReference(system, value, false, scenario);
    }
}
