package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.config.ConfigFactory;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.google.common.util.concurrent.Uninterruptibles;
import org.openqa.selenium.NoSuchElementException;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Shared page-turning "find and select a document" loop for a voucher list page — extracted
 * verbatim out of {@code GeneralVoucherListPage.selectDocumentInListPage} (plan §9.1) so
 * AR/AP get the same proven pagination handling for free instead of duplicating it later. Both
 * {@link VoucherMainGridBase} and {@link VoucherListFooterBase} are already shared across
 * AR/AP/GV, so this has no domain-specific dependency to untangle.
 */
public final class VoucherGridPaginationFinder {

    private VoucherGridPaginationFinder() {}

    /**
     * Selects the given document's row checkbox in the list grid. Searches the currently loaded
     * page first (via {@link VoucherMainGridBase#selectDocumentIfPresentOnCurrentPage} — same
     * proven direct-find/jump/scroll/focusRow handling as before); if not found there, clicks
     * Next Page (via the footer's pagination controls) and retries, repeating until found or the
     * last page is exhausted.
     *
     * @param documentNumber the {@code scheme_docno} to select (e.g. {@code "VG/00304/26-27"})
     * @param grid the list page's main grid component
     * @param footer the list page's pagination-footer component
     * @throws NoSuchElementException if the document isn't found after scanning every page
     */
    public static void selectDocumentAcrossPages(String documentNumber, VoucherMainGridBase grid, VoucherListFooterBase footer) {
        scanPagesUntil(documentNumber, footer, grid::selectDocumentIfPresentOnCurrentPage, "selected");
    }

    /**
     * Double-clicks the given document's row to open it for edit, page-turning to find it first
     * — the double-click analogue of {@link #selectDocumentAcrossPages}. Added after a live run
     * confirmed that {@code DOUBLE_CLICK_EDIT}-entry-method scenarios could never find a claimed
     * voucher sitting past page 1 of a large grid: {@code EditGeneralVoucher} skips the
     * checkbox-selection step (and therefore this class) entirely for that entry method when the
     * voucher isn't Posted, so {@code VoucherMainGridBase#doubleClickRowToOpen} — which only ever
     * searches the currently loaded page — had no page-turning behind it at all.
     *
     * @param documentNumber the {@code scheme_docno} to open (e.g. {@code "VG/00304/26-27"})
     * @param grid the list page's main grid component
     * @param footer the list page's pagination-footer component
     * @throws NoSuchElementException if the document isn't found after scanning every page
     */
    public static void doubleClickRowAcrossPages(String documentNumber, VoucherMainGridBase grid, VoucherListFooterBase footer) {
        scanPagesUntil(documentNumber, footer, grid::doubleClickRowIfPresentOnCurrentPage, "double-clicked");
    }

    /**
     * Shared page-turning loop behind both {@link #selectDocumentAcrossPages} and
     * {@link #doubleClickRowAcrossPages} — the two differ only in what "try this page" means
     * (click the checkbox vs. double-click the row), so that single action is parameterized as a
     * {@link Predicate} rather than duplicating the identical page-turn/give-up logic twice.
     *
     * @param actionOnCurrentPage tries the given document on the currently loaded page only —
     *        returns {@code true} if found and acted on, {@code false} if not present there
     * @param actionPastTense past-tense verb for the success log line (e.g. {@code "selected"}/
     *        {@code "double-clicked"})
     */
    private static void scanPagesUntil(String documentNumber, VoucherListFooterBase footer,
            Predicate<String> actionOnCurrentPage, String actionPastTense) {
        int pagesScanned = 0;
        while (true) {
            pagesScanned++;

            // Page-boundary visibility (previously only the bare page NUMBER surfaced, and only
            // reactively via getAttribute()'s own generic logger on a failed find — never which
            // documents were actually at the top/bottom of the page being scanned).
            Map<String, Object> progress = JavascriptExecutorUtils.getGridScrollProgress();
            Log.info("VoucherGridPaginationFinder: scanning page {} for [{}] — first doc on page=[{}], "
                    + "last doc on page=[{}]", pagesScanned, documentNumber,
                    progress.getOrDefault("topDocNumber", "—"), progress.getOrDefault("bottomDocNumber", "—"));

            if (actionOnCurrentPage.test(documentNumber)) {
                Log.info("VoucherGridPaginationFinder: found and {} [{}] on page {}", actionPastTense, documentNumber, pagesScanned);
                return;
            }

            int currentPage = footer.getCurrentPageNo();
            int totalPages  = footer.getTotalPageNo();
            boolean canAdvance = currentPage < totalPages && footer.isNextPageEnabled();

            if (!canAdvance) {
                throw new NoSuchElementException(
                    "VoucherGridPaginationFinder: document [" + documentNumber + "] not found after scanning "
                    + pagesScanned + " page(s) (last checked page " + currentPage + " of " + totalPages + ").");
            }

            footer.clickNextPage();
            JavascriptExecutorUtils.waitForExtJS();
            Uninterruptibles.sleepUninterruptibly(
                    ConfigFactory.getWaitConfig().selectDocGridLoadBufferMs(), TimeUnit.MILLISECONDS);
        }
    }
}
