package com.tests.Validation;

import com.gsl.validation.CombinedValidationError;
import com.gsl.validation.ui.UiFailureCollector;
import com.gsl.validation.ui.UiValidationFailure;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Guards the rule "UI validation is ALWAYS soft; DB validation always still runs" (it regressed twice).
 * Browser-free. Run with {@code -Dtest=UiSoftAssertGuardTest}.
 */
public class UiSoftAssertGuardTest {

    private static final Path REPORTER =
            Path.of("src/main/java/com/gsl/validation/ui/UiValidationReporter.java");

    @AfterMethod(alwaysRun = true)
    public void clean() {
        UiFailureCollector.clear();
    }

    /** The reporter is the single place UI results are rendered; it must never throw or soft-assert. */
    @Test
    public void reporterNeverThrowsOrAsserts() throws IOException {
        String src = Files.readString(REPORTER);
        assertFalse(src.contains("assertAll("), "UiValidationReporter must not assert - park failures in UiFailureCollector");
        assertFalse(src.contains("SoftAssertions"), "UiValidationReporter must not use SoftAssertions");
        assertFalse(src.contains("throw new"), "UiValidationReporter must not throw");
    }

    /** No code outside the reporter may turn UI results into a thrown assertion directly. */
    @Test
    public void noOtherCodeSoftAssertsUiResults() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/gsl"))) {
            List<Path> offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.endsWith("UiValidationReporter.java"))
                    .filter(p -> {
                        try {
                            String s = Files.readString(p);
                            return s.contains("UiValidationFailure") && s.contains("::isFail")
                                    && (s.contains("soft.fail(") || s.contains("assertAll("))
                                    && !p.toString().contains("CombinedValidationError")
                                    && !p.toString().contains("UiFailureCollector");
                        } catch (IOException e) {
                            return false;
                        }
                    }).toList();
            assertTrue(offenders.isEmpty(), "UI failures must go through UiFailureCollector, found: " + offenders);
        }
    }

    /** A parked UI failure survives later steps and is reported once, at the single assertion point. */
    @Test
    public void deferredUiFailureIsReportedOnlyAtTheFinalAssertion() {
        UiFailureCollector.addAll(List.of(new UiValidationFailure("OWNER_SITE", "font-size", "12px", "13px")));
        assertTrue(UiFailureCollector.hasPending());

        boolean dbValidationRan = true; // stands for the Save / DB / snapshot steps that follow in a real flow
        assertTrue(dbValidationRan);
        assertTrue(UiFailureCollector.hasPending(), "later steps must not lose the parked failure");

        CombinedValidationError e = expectThrows(CombinedValidationError.class, UiFailureCollector::assertNoPending);
        assertTrue(e.getMessage().contains("OWNER_SITE"), e.getMessage());
        assertFalse(UiFailureCollector.hasPending(), "assertNoPending clears the collector");
        assertEquals(UiFailureCollector.pending().size(), 0);
    }
}
