package com.gsl.utils.payment;

/**
 * Validates a PaymentReference value against the system-specific format rules.
 *
 * Rules:
 *   IMPS  → exactly 12 digits
 *   UPI   → 12 to 16 digits
 *   NEFT  → exactly 16 uppercase alphanumeric chars
 *   RTGS  → exactly 22 uppercase alphanumeric chars
 *   SWIFT → UUID/UETR format: 8-4-4-4-12 hex groups separated by dashes (36 chars)
 *   ACH   → exactly 15 digits
 *   SEPA  → 1 to 35 characters (length guard only; any printable char accepted)
 */
public final class PaymentReferenceValidator {

    private PaymentReferenceValidator() {}

    /** @return whether {@code ref.value} matches its {@code ref.system}'s format rule above; false if the value is null or the system is unrecognized. */
    public static boolean validate(PaymentReference ref) {
        if (ref.value == null) return false;

        return switch (ref.system) {
            case "IMPS"  -> ref.value.matches("\\d{12}");
            case "UPI"   -> ref.value.matches("\\d{12,16}");
            case "NEFT"  -> ref.value.matches("[A-Z0-9]{16}");
            case "RTGS"  -> ref.value.matches("[A-Z0-9]{22}");
            case "SWIFT" -> ref.value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
            case "ACH"   -> ref.value.matches("\\d{15}");
            case "SEPA"  -> !ref.value.isEmpty() && ref.value.length() <= 35;
            default      -> false;
        };
    }
}
