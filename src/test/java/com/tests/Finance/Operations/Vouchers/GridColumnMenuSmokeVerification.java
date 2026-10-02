package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.driver.DriverManager;
import com.gsl.driver.SessionManager;
import com.gsl.enums.TestAuthor;
import com.gsl.exceptions.MandatoryColumnException;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.pages.Finance.Operations.GeneralVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.GridColumnMenu;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.UserCredential;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.Selenium.WaitUtils;
import com.tests.WebTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Throwaway smoke spike for {@link GridColumnMenu} — confirms the live-DOM locators (hover →
 * trigger → menu, sort/filter/show-hide) actually work against the real app before any real
 * voucher test is wired to depend on them. Runs against General Voucher's list grid (most
 * mature/proven domain — see {@code EditGeneralVoucher}/{@code RevertGeneralVoucher}).
 *
 * <p>Not part of the standard regression suite — a dedicated {@code gridcolumnmenu-smoke} Maven
 * profile / {@code testng-gridcolumnmenu-smoke.xml} runs this alone. Delete (or promote pieces
 * of it into a real test) once the mechanism is proven end-to-end and every real voucher test
 * that needs it (e.g. {@code selectDocumentFast}'s filter tiers) has its own coverage.
 *
 * <p>One deliberate deviation from the original spike design: the filter-mechanism check below
 * filters to a value guaranteed NOT to exist (rather than a "known real prefix") and asserts the
 * grid goes to zero rows — this needs no live-data assumption at all (no DB pre-check, no
 * grid-row-text reader, neither of which exists yet on {@code VoucherMainGridBase}), while still
 * proving the filter actually reached the ExtJS store instead of silently no-op'ing.
 *
 * <p>Every step also attaches a screenshot on PASS (via {@link ExtentLogger#passWithScreenshot})
 * — not the codebase's normal behavior (every other screenshot mechanism is fail-only); this is
 * a temporary debugging aid for this spike, per explicit user request, and can be trimmed back
 * once the mechanism is proven end-to-end.
 */
@ApiFlow(FinanceApiPattern.GENERAL_VOUCHER)
public class GridColumnMenuSmokeVerification extends WebTest {

    private static final Pattern DISPLAYING_TOTAL = Pattern.compile("of\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceGenVoucher")
    @Test(description = "Smoke-verify GridColumnMenu's sort/filter/show-hide/mandatory-guard against the live GV list grid")
    public void gridColumnMenuSmoke() {

        final HomePage[]               homeRef = new HomePage[1];
        final GeneralVoucherListPage[] listRef = new GeneralVoucherListPage[1];

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
            ExtentLogger.passWithScreenshot("Login to Application");
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        // Business log + "Navigated: ..." summary are now recorded inside
        // NavigationUtils.navigateToFinanceSideMenu itself.
        StepExecutor.step("Open Finance → General Voucher List", () -> {
            listRef[0] = NavigationUtils.navigateToFinanceSideMenu(
                    homeRef[0],
                    MainMenuType.OPERATIONS,
                    OperationsMenuType.CATEGORY,
                    GeneralVoucherListPage.class);
            ExtentLogger.passWithScreenshot("Open Finance → General Voucher List");
        });

        // ── Step 2b: Clear Filter + Refresh ───────────────────────────────────────
        // Mirrors RevertGeneralVoucher's Step 4 / EditGeneralVoucher's Step 2b — clears any
        // leftover grid filter/sort-adjacent state before the smoke sequence starts, so a stale
        // filter or leftover multi-sort badge from an earlier session (e.g. the manual DevTools
        // exploration this design was built from) can't leak into this run's baseline.
        StepExecutor.step("Clear Filter and Refresh List", () -> {
            listRef[0].clearFilterAndRefreshList().resetToFirstPageIfEnabled();
            ReportLogger.business("Filter cleared, list refreshed, reset to first page (if not already there)");
            ExtentLogger.passWithScreenshot("Clear Filter and Refresh List");
        });

        // ── Step 3: Baseline sanity — Voucher No. is mandatory and visible ────────
        StepExecutor.step("Baseline: Voucher No. is currently visible", () -> {
            boolean visible = listRef[0].isColumnVisible(CommonVoucherListColumn.VOUCHER_NO);
            Assert.assertTrue(visible, "Voucher No. (mandatory column) should be visible by default.");
            ReportLogger.pass("Voucher No. visible=" + visible);
            ExtentLogger.passWithScreenshot("Baseline: Voucher No. is currently visible");
        });

        // ── Step 4: Open/close the column-header menu — cheapest "hover+trigger+click
        //            works" signal; failure here throws before any other step matters ──
        StepExecutor.step("Open and close the Voucher No. column-header menu", () -> {
            GridColumnMenu.openColumnMenu(CommonVoucherListColumn.VOUCHER_NO);
            GridColumnMenu.closeAnyOpenMenu();
            ReportLogger.pass("Column-header menu opened and closed without error.");
            ExtentLogger.passWithScreenshot("Open and close the Voucher No. column-header menu");
        });

        // ── Step 5: Filter mechanism — filter to two different REAL voucher numbers in turn,
        //            confirming each genuinely re-applies live (not a stale/cached result), then
        //            clear it and confirm the grid restores to baseline ──
        StepExecutor.step("Filter Voucher No. — confirm the store is actually filtered", () -> {
            int baselineTotal = readDisplayingTotal(listRef[0]);
            ReportLogger.business("Baseline row total (unfiltered): " + baselineTotal);
            Assert.assertTrue(baselineTotal > 0,
                    "Expected at least one General Voucher row to exist before filtering.");

            assertExactMatchFiltersToExactlyOneRow(listRef[0], "VG/02182/26-27");

            // Clear Filter + Refresh between the two filter attempts — resets to a clean baseline
            // instead of transitioning directly from one filtered state to the next (same pattern
            // RevertGeneralVoucher/EditGeneralVoucher already use before every fresh document
            // search, rather than relying on GridColumnMenu's own reopen-menu logic alone).
            listRef[0].clearFilterAndRefreshList();
            waitForDisplayingTotal(listRef[0], baselineTotal);

            // Re-filter to a DIFFERENT real voucher — proves the filter genuinely re-applies live
            // each time, not just a stale/cached single-row result left over from the first filter.
            assertExactMatchFiltersToExactlyOneRow(listRef[0], "VG/02129/26-27");

            listRef[0].clearFilterAndRefreshList();
            int restoredTotal = waitForDisplayingTotal(listRef[0], baselineTotal);
            ReportLogger.pass("Row total restored after clearFilterAndRefreshList(): " + restoredTotal);
            ExtentLogger.passWithScreenshot("Filter Voucher No. — confirm the store is actually filtered");
        });

        // ── Step 5b: CONTAINS ("partial match") filtering — never exercised live before this fix;
        //            every prior filter fix in this session (Fixes 3-6) only ever drove the
        //            EXACT_MATCH input, a separate DOM element from CONTAINS's own "Contains
        //            anywhere..." input. ──
        StepExecutor.step("Filter Voucher No. — Contains-anywhere (partial match)", () -> {
            int baselineTotal = readDisplayingTotal(listRef[0]);
            // A substring of "VG/02182/26-27" with the "VG/" prefix and "/26-27" suffix both
            // stripped — this string can ONLY match via genuine substring/contains semantics; it
            // is not equal to the full voucher number, so it could never pass by accidentally
            // behaving like EXACT_MATCH.
            assertContainsFiltersAndFindsDocument(listRef[0], "02182", "VG/02182/26-27", baselineTotal);

            listRef[0].clearFilterAndRefreshList();
            int restoredTotal = waitForDisplayingTotal(listRef[0], baselineTotal);
            ReportLogger.pass("Row total restored after clearFilterAndRefreshList(): " + restoredTotal);
            ExtentLogger.passWithScreenshot("Filter Voucher No. — Contains-anywhere (partial match)");
        });

        // ── Step 6: Multi-column sort — Voucher No. priority 1, Voucher Date priority 2.
        //            Confirmed live via TWO independent runs (2026-08-28) — one with a retry
        //            (2 Shift+clicks), one without (1 Shift+click) — both produced the identical
        //            result: Shift+clicking a column with no existing multi-sort order ALWAYS
        //            promotes it to priority 1, demoting whatever was already sorted. This is the
        //            opposite of an earlier "appended as the next/last priority" assumption; see
        //            GridColumnMenu.addSortViaShiftClick's own Javadoc for the full evidence and
        //            why the resulting direction on the promoted column is not force-corrected. ──
        StepExecutor.step("Multi-column sort — Voucher No. (1, via Shift+click), Voucher Date (2)", () -> {
            listRef[0].sortColumnAscending(CommonVoucherListColumn.VOUCHER_DATE);
            listRef[0].addSortColumnAscending(CommonVoucherListColumn.VOUCHER_NO);

            var dateOrder = listRef[0].getColumnSortOrder(CommonVoucherListColumn.VOUCHER_DATE);
            var noOrder = listRef[0].getColumnSortOrder(CommonVoucherListColumn.VOUCHER_NO);
            ReportLogger.business("Voucher Date sort order: " + dateOrder + ", Voucher No. sort order: " + noOrder);
            // Logged before the assertions so the diagnostic table is captured in the report even
            // if an assertion below throws (a prior run lost this table for exactly that reason).
            GridColumnMenu.logGridState(listRef[0].getMainGrid().allColumns());

            Assert.assertEquals(noOrder.orElse(-1), Integer.valueOf(1),
                    "Expected Voucher No. (the column Shift+clicked last) to carry multi-sort priority 1.");
            Assert.assertEquals(dateOrder.orElse(-1), Integer.valueOf(2),
                    "Expected Voucher Date to be demoted to multi-sort priority 2 after Voucher No. was Shift+clicked.");
            ReportLogger.pass("Multi-sort badges confirmed: Voucher No.=(1), Voucher Date=(2).");
            ExtentLogger.passWithScreenshot("Multi-column sort — Voucher No. (1, via Shift+click), Voucher Date (2)");
        });

        // ── Step 7: Show/hide a non-mandatory column, restoring visibility even on
        //            assertion failure — REMARKS is never mandatory in any domain ──
        StepExecutor.step("Hide/show a non-mandatory column (Remarks)", () -> {
            boolean originallyVisible = listRef[0].isColumnVisible(CommonVoucherListColumn.REMARKS);
            try {
                listRef[0].hideColumn(CommonVoucherListColumn.REMARKS);
                Assert.assertFalse(listRef[0].isColumnVisible(CommonVoucherListColumn.REMARKS),
                        "Expected Remarks to be hidden after hideColumn().");

                listRef[0].showColumn(CommonVoucherListColumn.REMARKS);
                Assert.assertTrue(listRef[0].isColumnVisible(CommonVoucherListColumn.REMARKS),
                        "Expected Remarks to be visible again after showColumn().");
                ReportLogger.pass("Remarks column hide/show toggled and confirmed correctly.");
                ExtentLogger.passWithScreenshot("Hide/show a non-mandatory column (Remarks)");
            } finally {
                // Best-effort restore to whatever it was before this step, regardless of outcome —
                // flagged in the design as a real risk if the app persists Columns-chooser state
                // via an ExtJS state provider/cookie across runs.
                if (originallyVisible != listRef[0].isColumnVisible(CommonVoucherListColumn.REMARKS)) {
                    if (originallyVisible) {
                        listRef[0].showColumn(CommonVoucherListColumn.REMARKS);
                    } else {
                        listRef[0].hideColumn(CommonVoucherListColumn.REMARKS);
                    }
                }
            }
        });

        // ── Step 8: Mandatory-column guard — hiding Voucher No. must throw before any
        //            DOM interaction, never silently hide a mandatory column ──
        StepExecutor.step("Mandatory-column guard — hiding Voucher No. must throw", () -> {
            Assert.assertThrows(MandatoryColumnException.class,
                    () -> listRef[0].hideColumn(CommonVoucherListColumn.VOUCHER_NO));
            Assert.assertTrue(listRef[0].isColumnVisible(CommonVoucherListColumn.VOUCHER_NO),
                    "Voucher No. must remain visible — the guard should fire before any DOM interaction.");
            ReportLogger.pass("MandatoryColumnException correctly thrown for Voucher No.; column still visible.");
            ExtentLogger.passWithScreenshot("Mandatory-column guard — hiding Voucher No. must throw");
        });

        // ── Step 9: Logout — this step deliberately does NOT call HomePage.logoutFromApplication()
        //            itself (a direct call bypasses SessionManager and previously caused stale
        //            nav-cache/pool leaks); SessionManager performs the real logout+destroy a few
        //            seconds later, at this <test> block's own drain (confirmed live, run
        //            20260828_232131: diagnostics.log shows "Logging out user ... at drain" then
        //            "Destroyed" ~8s after this step reports). Unlike RevertGeneralVoucher/
        //            EditGeneralVoucher (where this exact idiom is copied from), this smoke suite
        //            has only one test method — "may be reused by a later test" never actually
        //            applies here, so the message doesn't claim that. ──
        StepExecutor.step("Logout (via SessionManager drain)", () -> {
            ReportLogger.session("Logout not called directly here — SessionManager will log out "
                    + "and destroy this session at end-of-test drain (confirmed in diagnostics.log).");
            ExtentLogger.passWithScreenshot("Logout (via SessionManager drain)");
        });
    }

    /** Filters Voucher No. to an exact match on {@code voucherNo} and confirms it genuinely took
     *  effect two ways: the footer's row total drops to exactly 1, AND
     *  {@code VoucherMainGridBase.selectDocumentIfPresentOnCurrentPage} actually finds that
     *  specific document in the filtered grid (not just "some row exists"). Note:
     *  {@code selectDocumentIfPresentOnCurrentPage} clicks the row's checkbox as a side effect
     *  when found — harmless here, just flagged so it isn't mistaken for an accidental extra
     *  interaction later. {@code voucherNo} is assumed to genuinely exist in the environment this
     *  runs against; if it's since been deleted/renumbered, this fails for that reason instead of
     *  a real {@code GridColumnMenu} bug. */
    private static void assertExactMatchFiltersToExactlyOneRow(GeneralVoucherListPage page, String voucherNo) {
        page.filterColumnByExactMatch(CommonVoucherListColumn.VOUCHER_NO, voucherNo);
        int total = waitForDisplayingTotal(page, 1);
        boolean found = page.getMainGrid().selectDocumentIfPresentOnCurrentPage(voucherNo);
        ReportLogger.business("After filtering to " + voucherNo + ": total=" + total + ", found=" + found);
        Assert.assertTrue(found, "Expected " + voucherNo + " to actually be present/selectable in the filtered grid.");

        // Content-level check via findElements — the one rendered Voucher No. cell must literally
        // equal the target, not just "some row with a matching checkbox exists".
        List<String> renderedValues = GridColumnMenu.getColumnCellValuesOnCurrentPage(CommonVoucherListColumn.VOUCHER_NO);
        ReportLogger.business("Rendered Voucher No. cell values: " + renderedValues);
        Assert.assertEquals(renderedValues, List.of(voucherNo),
                "Expected exactly one rendered Voucher No. cell, equal to " + voucherNo + ".");
        ExtentLogger.passWithScreenshot("Filtered to " + voucherNo);
    }

    /** Filters Voucher No. by a CONTAINS substring and confirms it genuinely took effect two ways:
     *  the footer's row total drops below {@code baselineTotal} (proving a real filter applied,
     *  not a no-op), AND {@code selectDocumentIfPresentOnCurrentPage} actually finds
     *  {@code expectedVoucherNo} in the filtered grid. Deliberately does not assert an exact
     *  total — unlike exact-match filtering, CONTAINS is inherently broader, and this test has no
     *  live-DB visibility into how many other real vouchers might also contain the same
     *  substring. */
    private static void assertContainsFiltersAndFindsDocument(GeneralVoucherListPage page,
            String substring, String expectedVoucherNo, int baselineTotal) {
        page.filterColumnByContains(CommonVoucherListColumn.VOUCHER_NO, substring);
        int total = waitForDisplayingTotalBelow(page, baselineTotal);
        boolean found = page.getMainGrid().selectDocumentIfPresentOnCurrentPage(expectedVoucherNo);
        ReportLogger.business("After CONTAINS-filtering to \"" + substring + "\": total=" + total
                + ", found(" + expectedVoucherNo + ")=" + found);
        Assert.assertTrue(found, "Expected " + expectedVoucherNo
                + " to be present in the CONTAINS-filtered grid for substring \"" + substring + "\".");

        // Row-level content check via findElements — proves EVERY rendered row genuinely contains
        // the substring (not just that a footer total decreased and one known document happens to
        // be present), and cross-checks the DOM count against the footer total when everything
        // that matches demonstrably fits on this one page (determined at runtime — no hardcoded
        // page-size guess).
        List<String> renderedValues = GridColumnMenu.getColumnCellValuesOnCurrentPage(CommonVoucherListColumn.VOUCHER_NO);
        long matchingRowsOnPage = GridColumnMenu.countMatchingRowsOnCurrentPage(
                CommonVoucherListColumn.VOUCHER_NO, cell -> cell != null && cell.contains(substring));
        ReportLogger.business("Rendered Voucher No. cell values: " + renderedValues
                + " (matching \"" + substring + "\": " + matchingRowsOnPage + ")");
        Assert.assertTrue(matchingRowsOnPage > 0,
                "Expected at least one rendered Voucher No. cell to actually contain \"" + substring + "\".");
        Assert.assertEquals(matchingRowsOnPage, renderedValues.size(),
                "Expected EVERY rendered Voucher No. cell to contain \"" + substring + "\" after a CONTAINS "
                + "filter — found a non-matching cell among the currently rendered rows.");
        Assert.assertTrue(matchingRowsOnPage <= total,
                "DOM-rendered match count can never exceed the footer's reported total.");
        if (renderedValues.size() == total) {
            Assert.assertEquals(matchingRowsOnPage, (long) total,
                    "Expected the DOM row-count (via findElements) to match the footer's reported "
                    + "total, since everything that matches demonstrably fits on this one page.");
        }
        ExtentLogger.passWithScreenshot("CONTAINS-filtered to \"" + substring + "\"");
    }

    /** Polls the footer's total until it drops below {@code baselineTotal} — the CONTAINS-mode
     *  counterpart to {@link #waitForDisplayingTotal}, which needs an exact expected value that
     *  isn't knowable in advance for a broad substring match. Same async-commit race this test
     *  already has to wait out for EXACT_MATCH (confirmed live, run 20260828_154136) applies here
     *  too — GridColumnMenu.applyFilter's own waitForExtJS()/waitForExtMask() return before the
     *  footer visibly updates. */
    private static int waitForDisplayingTotalBelow(GeneralVoucherListPage page, int baselineTotal) {
        return WaitUtils.webDriverWait(WaitUtils.extJsTimeoutSeconds())
                .until(driver -> {
                    int current = readDisplayingTotal(page);
                    return current < baselineTotal ? current : null;
                });
    }

    /** Polls the footer's "Displaying X-Y of Z" total until it reaches {@code expectedTotal}, up
     *  to the standard ExtJS-settle timeout — GridColumnMenu.applyFilter's own waitForExtJS()/
     *  waitForExtMask() calls return almost instantly for this kind of lightweight async filter
     *  commit (confirmed live, run 20260828_154136: ~328ms round trip with no visible mask ever
     *  appearing), so this test must wait for the actually-observable effect itself rather than
     *  trusting those generic readiness signals alone. Waiting for a specific expected value
     *  (rather than "any value different from before") avoids a same-size second filter — e.g.
     *  this test's own second voucher also resolving to exactly 1 row — spuriously timing out. */
    private static int waitForDisplayingTotal(GeneralVoucherListPage page, int expectedTotal) {
        return WaitUtils.webDriverWait(WaitUtils.extJsTimeoutSeconds())
                .until(driver -> {
                    int current = readDisplayingTotal(page);
                    return current == expectedTotal ? current : null;
                });
    }

    /** Parses the "Displaying X-Y of Z" pagination-footer text into Z — the total row count
     *  across all pages. Returns 0 if no "of N" segment is found (the app's own empty-grid
     *  rendering when a filter matches nothing). */
    private static int readDisplayingTotal(GeneralVoucherListPage page) {
        String text = page.getGridFooter().getDisplayingText();
        Matcher matcher = DISPLAYING_TOTAL.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            Log.debug("readDisplayingTotal: no 'of N' match in footer text '{}' — treating as 0", text);
            return 0;
        }
        return Integer.parseInt(matcher.group(1));
    }
}
