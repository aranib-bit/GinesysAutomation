package com.gsl.reports;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.gsl.config.ConfigFactory;
import com.gsl.utils.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides which failure screenshots actually reach the Extent report ({@code report.screenshots.mode},
 * see {@link com.gsl.config.ReportConfig#screenshotsMode()}). Failure paths still CAPTURE a
 * screenshot to disk at the moment of failure (the page state is only available then), but hand it
 * here instead of attaching it; {@link #flush()} runs once at test end and attaches only the last
 * one ({@code LAST}, default) or every one up to the cap ({@code ALL}), deleting the files it did
 * not attach. A multi-failure test therefore embeds one screenshot instead of one per failing step.
 */
public final class ScreenshotPolicy {

    private ScreenshotPolicy() {}

    private record Pending(List<ExtentTest> targets, String label, String path, String annotation) {}

    private static final ThreadLocal<List<Pending>> PENDING = ThreadLocal.withInitial(ArrayList::new);

    /** Records a captured failure screenshot for end-of-test attachment to each of {@code targets}. */
    public static void defer(List<ExtentTest> targets, String label, String path, String annotation) {
        PENDING.get().add(new Pending(List.copyOf(targets), label, path, annotation));
    }

    /** File path of the most recent pending (not yet flushed) failure screenshot on this thread, or
     *  {@code null}. Read-only peek for the coverage workbook; call BEFORE {@link #flush()}. */
    public static String lastPendingPath() {
        List<Pending> all = PENDING.get();
        return all.isEmpty() ? null : all.get(all.size() - 1).path();
    }

    /** Attaches the screenshots the configured mode keeps and deletes the rest. Safe to call when
     *  nothing is pending; always leaves the pending list empty. */
    public static void flush() {
        List<Pending> all = PENDING.get();
        PENDING.remove();
        if (all.isEmpty()) return;

        boolean allMode = "ALL".equalsIgnoreCase(String.valueOf(
                ConfigFactory.getReportConfig().screenshotsMode()).trim());
        int cap = ConfigFactory.getReportConfig().screenshotMaxPerTest();
        int keepFrom = allMode ? (cap > 0 ? Math.max(0, all.size() - cap) : 0) : all.size() - 1;

        for (int i = 0; i < all.size(); i++) {
            Pending p = all.get(i);
            if (i >= keepFrom) {
                attach(p, allMode ? "Screenshot at failure" : "Screenshot at failure (last of " + all.size() + ")");
            } else {
                try { Files.deleteIfExists(Path.of(p.path())); } catch (IOException ignored) { }
            }
        }
    }

    /** Drops pending entries without attaching (and deletes their files) — for tests that end
     *  without a failure report. */
    public static void discard() {
        List<Pending> all = PENDING.get();
        PENDING.remove();
        for (Pending p : all) {
            try { Files.deleteIfExists(Path.of(p.path())); } catch (IOException ignored) { }
        }
    }

    private static void attach(Pending p, String title) {
        for (ExtentTest target : p.targets()) {
            try {
                target.fail(title + " — " + p.label(),
                        MediaEntityBuilder.createScreenCaptureFromPath(p.path()).build());
                if (p.annotation() != null && !p.annotation().isBlank()) {
                    target.info("Page context: " + p.annotation());
                }
            } catch (Exception e) {
                Log.warn("[ScreenshotPolicy] could not attach {}: {}", p.path(), e.getMessage());
            }
        }
    }
}
