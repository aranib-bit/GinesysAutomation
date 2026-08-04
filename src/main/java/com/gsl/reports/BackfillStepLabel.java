package com.gsl.reports;

/**
 * Canonical step names for DataBackFill's nested Extent report entries, shared across the
 * WebAPI/PublicAPI strategies ({@link StepExecutor#stepUnder}) and the k6 bulk-path output
 * parser ({@code K6ProcessBulkDataProvisioner}), so the same label always means the same thing
 * regardless of which platform actually created the record.
 */
public enum BackfillStepLabel {
    CREATE_TOKEN("Create Token"),
    KILL_TOKEN("Kill Token"),
    CREATE_ITEM("Create Item"),
    CREATE_GRC("Create GRC"),
    CREATE_DC("Create DC"),
    CREATE_AR_VOUCHER("Create AR Voucher"),
    CREATE_SERVICE_INVOICE_ADHOC("Create Service Invoice Adhoc"),
    VERIFY_POSTED("Verify Posted (release_status)");

    private final String label;

    BackfillStepLabel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
