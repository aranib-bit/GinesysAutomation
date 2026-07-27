package com.gsl.validation.db.finance;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinAccClassDao;
import com.gsl.model.finance.AdmCls;
import com.gsl.reports.DatabaseLogger;
import com.gsl.reports.ExtentManager;
import com.gsl.reports.ReportLogger;
import org.testng.Assert;

import java.util.Map;
import java.util.Optional;

/**
 * DB Validation for Account Class — asserts the {@code admcls} table matches what
 * {@code accountClassScenario} just did in the UI. Much simpler than
 * {@link APVoucherDbValidator} (no GL/finpost multi-level chain — admcls is a single flat
 * classification row), so this is one class rather than a level-1..4 orchestrator.
 */
public class FinAccClassDbValidator {

    // ⚠️ UNVERIFIED — best-guess Class Type dropdown label -> admcls.clstype code mapping, per
    // AdmCls's own javadoc table. Confirm the exact pairing (especially "TDS Agency"/"Employee"
    // vs the DB comment's "TDS"/"Staff" naming) on the first live run.
    private static final Map<String, String> CLASS_TYPE_LABEL_TO_CODE = Map.ofEntries(
            Map.entry("Customer", "C"),
            Map.entry("Supplier", "S"),
            Map.entry("Transporter", "T"),
            Map.entry("Jobber", "J"),
            Map.entry("Agent", "A"),
            Map.entry("Others", "O"),
            Map.entry("Channel", "H"),
            Map.entry("TDS Agency", "D"),
            Map.entry("Employee", "E")
    );

    private final FinAccClassDao dao;

    public FinAccClassDbValidator() {
        this.dao = DatabaseManager.getJdbi().onDemand(FinAccClassDao.class);
    }

    public void validate(AccountClassDbContext ctx) {
        Assert.assertNotNull(ctx, "AccountClassDbContext must not be null");
        Assert.assertNotNull(ctx.getResolvedClassName(), "resolvedClassName is null");

        ExtentTest test = ExtentManager.getExtentTest();
        if (test != null) test.assignCategory("DB Validation");

        DatabaseLogger.validationStart("Account Class DB Validation — [" + ctx.getResolvedClassName() + "]");

        if ("REJECT".equalsIgnoreCase(ctx.getExpectedOutcome())) {
            validateRejected(ctx.getResolvedClassName());
        } else {
            validateCreated(ctx.getResolvedClassName(), ctx.getExpectedClassType());
        }

        if (ctx.isEdited()) {
            validateEdit(ctx);
        }

        if (ctx.getDuplicateVariant() != null && !ctx.getDuplicateVariant().isBlank()) {
            validateDuplicateVariantRejected(ctx);
        }

        DatabaseLogger.validationComplete("Account Class DB Validation — [" + ctx.getResolvedClassName() + "]", true);
    }

    private void validateRejected(String resolvedName) {
        int count = dao.countByName(resolvedName);
        if (count == 0) {
            DatabaseLogger.checkPassed("REJECT — no admcls row", "clsname='" + resolvedName + "' correctly absent");
        } else {
            DatabaseLogger.checkFailed("REJECT — no admcls row", 0, count,
                    "SELECT COUNT(*) FROM admcls WHERE clsname = :name", resolvedName, 0, count);
            Assert.fail("Expected no admcls row for rejected Class Name '" + resolvedName
                    + "', but found " + count + ".");
        }
    }

    private void validateCreated(String resolvedName, String expectedClassTypeLabel) {
        Optional<AdmCls> found = dao.findByName(resolvedName);
        if (found.isEmpty()) {
            DatabaseLogger.checkFailed("CREATE — admcls row present", "1 row", "0 rows",
                    "SELECT * FROM admcls WHERE clsname = :name", resolvedName, 0, 0);
            Assert.fail("Expected an admcls row for Class Name '" + resolvedName + "' but none was found.");
            return;
        }
        AdmCls row = found.get();
        String expectedCode = CLASS_TYPE_LABEL_TO_CODE.get(expectedClassTypeLabel);
        DatabaseLogger.checkPassed("CREATE — admcls row present",
                "clscode=" + row.getClscode() + ", clstype=" + row.getClstype());
        if (expectedCode != null) {
            Assert.assertEquals(row.getClstype(), expectedCode,
                    "clstype mismatch for '" + resolvedName + "' — expected Class Type '"
                    + expectedClassTypeLabel + "' (" + expectedCode + ") but DB has '" + row.getClstype() + "'.");
        } else {
            ReportLogger.warn("No known clstype code mapping for Class Type label '"
                    + expectedClassTypeLabel + "' — skipping clstype assertion.");
        }
    }

    private void validateEdit(AccountClassDbContext ctx) {
        if (ctx.getNewResolvedClassName() != null) {
            int oldCount = dao.countByName(ctx.getResolvedClassName());
            Assert.assertEquals(oldCount, 0,
                    "Old Class Name '" + ctx.getResolvedClassName() + "' should be gone after rename.");

            Optional<AdmCls> renamed = dao.findByName(ctx.getNewResolvedClassName());
            Assert.assertTrue(renamed.isPresent(),
                    "New Class Name '" + ctx.getNewResolvedClassName() + "' not found after edit.");

            String expectedLabel = ctx.getNewExpectedClassType() != null
                    ? ctx.getNewExpectedClassType() : ctx.getExpectedClassType();
            String expectedCode = CLASS_TYPE_LABEL_TO_CODE.get(expectedLabel);
            if (expectedCode != null) {
                Assert.assertEquals(renamed.get().getClstype(), expectedCode,
                        "clstype mismatch after edit for '" + ctx.getNewResolvedClassName() + "'.");
            }
            DatabaseLogger.checkPassed("EDIT — rename verified",
                    ctx.getResolvedClassName() + " -> " + ctx.getNewResolvedClassName());
        } else if (ctx.getNewExpectedClassType() != null) {
            Optional<AdmCls> row = dao.findByName(ctx.getResolvedClassName());
            Assert.assertTrue(row.isPresent(),
                    "Class Name '" + ctx.getResolvedClassName() + "' not found after type-only edit.");
            String expectedCode = CLASS_TYPE_LABEL_TO_CODE.get(ctx.getNewExpectedClassType());
            if (expectedCode != null) {
                Assert.assertEquals(row.get().getClstype(), expectedCode,
                        "clstype mismatch after type-only edit for '" + ctx.getResolvedClassName() + "'.");
            }
            DatabaseLogger.checkPassed("EDIT — type change verified", ctx.getResolvedClassName());
        }
    }

    private void validateDuplicateVariantRejected(AccountClassDbContext ctx) {
        int baseCount = dao.countByName(ctx.getResolvedClassName());
        Assert.assertEquals(baseCount, 1,
                "Expected exactly 1 admcls row for base Class Name '" + ctx.getResolvedClassName() + "'.");

        if (ctx.getDuplicateVariantName() != null
                && !ctx.getDuplicateVariantName().equalsIgnoreCase(ctx.getResolvedClassName())) {
            int variantCount = dao.countByName(ctx.getDuplicateVariantName());
            Assert.assertEquals(variantCount, 0,
                    "Duplicate-variant Class Name '" + ctx.getDuplicateVariantName()
                    + "' should have been rejected, but a row exists.");
        }
        DatabaseLogger.checkPassed("Duplicate variant rejected", ctx.getDuplicateVariantName());
    }
}
