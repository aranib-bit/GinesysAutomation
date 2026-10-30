package com.gsl.flow.Finance.Operations.Common;

import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.model.common.VoucherActionResponse;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.HrEmpDao;
import com.gsl.database.dao.finance.FinPostDao;
import com.gsl.database.dao.finance.FinTagDao;
import com.gsl.database.dao.finance.FinVchDetDao;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.model.finance.FinTag;
import com.gsl.model.finance.FinVchDet;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.CapturedTag;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver;
import org.jdbi.v3.core.Jdbi;
import org.testng.Assert;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Common steps of the AR / AP Edit, Delete and Revert flows that precede the UI action: capture
 * the live tags, untag via the Document Adjustment API, and — for AP — unpost via the API.
 * Previously each test class re-inlined the same DB lookups (header, slcode, amount, OU code)
 * and the same two step bodies (see {@code EditApVoucher}, {@code DeleteApVoucher},
 * {@code EditArVoucher}).
 *
 * <p>Ordering matters and is encoded by callers: <b>capture, untag, unpost</b> — a Posted+tagged
 * document cannot be unposted until untagged, and the tag is destroyed by the untag, so it must be
 * captured first.
 */
public final class VoucherEditFlowSteps {

    private VoucherEditFlowSteps() {}

    /** All {@code fintag} rows on either side of {@code vchcode}, each resolved to its counter-
     *  document's number through {@code finpost} ({@code fintag.entcode} is not always a voucher —
     *  e.g. {@code enttype2='PIM'} purchase invoices; run 20260930_163632). */
    public static List<CapturedTag> captureTags(Jdbi jdbi, long vchcode, String ownDocNo) {
        FinTagDao tagDao = jdbi.onDemand(FinTagDao.class);
        FinPostDao postDao = jdbi.onDemand(FinPostDao.class);
        List<CapturedTag> out = new ArrayList<>();
        for (FinTag t : tagDao.findByEntCode1(vchcode)) {
            out.add(new CapturedTag(resolveDocNo(postDao, t.getPostcode2(), ownDocNo), t.getAmount()));
        }
        for (FinTag t : tagDao.findByEntCode2(vchcode)) {
            out.add(new CapturedTag(resolveDocNo(postDao, t.getPostcode1(), ownDocNo), t.getAmount()));
        }
        return out;
    }

    private static String resolveDocNo(FinPostDao postDao, long counterPostcode, String ownDocNo) {
        return postDao.findSchemeDocnoByPostCode(counterPostcode).orElseThrow(() -> new IllegalStateException(
                "[Capture] Could not resolve scheme_docno via finpost for counter postcode=" + counterPostcode
                + " (tagged against " + ownDocNo + ")"));
    }

    /**
     * Step "Untag Document Adjustments if Tagged": for a Posted document, captures every live tag
     * into {@code capturedOut} (cleared first) and then untags via
     * {@link DocumentAdjustmentFlow#untagIfNeeded}. No-op for non-Posted documents.
     */
    public static void untagIfTagged(String docNo, String releaseStatus, List<CapturedTag> capturedOut) {
        untagStep(docNo, releaseStatus, true, capturedOut);
    }

    /** As {@link #untagIfTagged} but without the Posted gate — for flows (Revert) that only ever
     *  run against a document whose tags, if any, must be cleared before the action. */
    public static void untagIfTaggedRegardlessOfStatus(String docNo, List<CapturedTag> capturedOut) {
        untagStep(docNo, null, false, capturedOut);
    }

    /** {@link #untagIfTagged(String, String, List)} for an API-created voucher: with {@code db.available=no} the untag runs from {@code ref}. */
    public static void untagIfTagged(String docNo, String releaseStatus, List<CapturedTag> capturedOut,
                                     com.gsl.testdata.dto.ApiVoucherRef ref) {
        if (ref != null && !com.gsl.database.DbAvailability.isAvailable()) {
            untagViaApiStep(docNo, releaseStatus, true, capturedOut, ref);
            return;
        }
        untagStep(docNo, releaseStatus, true, capturedOut);
    }

    /** {@link #untagIfTaggedRegardlessOfStatus(String, List)} for an API-created voucher. */
    public static void untagIfTaggedRegardlessOfStatus(String docNo, List<CapturedTag> capturedOut,
                                                       com.gsl.testdata.dto.ApiVoucherRef ref) {
        if (ref != null && !com.gsl.database.DbAvailability.isAvailable()) {
            untagViaApiStep(docNo, null, false, capturedOut, ref);
            return;
        }
        untagStep(docNo, null, false, capturedOut);
    }

    private static void untagViaApiStep(String docNo, String releaseStatus, boolean requirePosted, List<CapturedTag> capturedOut,
                                        com.gsl.testdata.dto.ApiVoucherRef ref) {
        StepExecutor.step("Untag Document Adjustments if Tagged (API)", () -> {
            capturedOut.clear();
            if (requirePosted && !"P".equals(releaseStatus)) {
                ReportLogger.business("Voucher status=" + releaseStatus + " — not Posted, untag check skipped (nothing to unpost).");
                return;
            }
            capturedOut.addAll(ref.getCapturedTags());   // what the DB path reads from fintag before the untag
            boolean untagged = DocumentAdjustmentFlow.untagViaApi(ref);
            ReportLogger.business("Untag check for " + docNo + ": "
                    + (untagged ? "was tagged — untagged via Document Adjustment API" : "not tagged."));
        });
    }

    private static void untagStep(String docNo, String releaseStatus, boolean requirePosted,
                                  List<CapturedTag> capturedOut) {
        StepExecutor.step("Untag Document Adjustments if Tagged", () -> {
            capturedOut.clear();
            if (requirePosted && !"P".equals(releaseStatus)) {
                ReportLogger.business("Voucher status=" + releaseStatus
                        + " — not Posted, untag check skipped (nothing to unpost).");
                return;
            }
            Jdbi jdbi = DatabaseManager.getJdbi();
            FinVchMain header = requireHeader(jdbi, docNo, "Untag");
            long vchcode = header.getVchcode();

            List<FinVchDet> detLines = jdbi.onDemand(FinVchDetDao.class).findByVoucherCode(vchcode);
            if (detLines.isEmpty()) {
                throw new IllegalStateException("[Untag] finvchdet has no lines for vchcode=" + vchcode
                        + " (scheme_docno=" + docNo + ") — cannot resolve slcode for the untag request.");
            }
            long slcode = detLines.get(0).getSlcode();
            long distinctSlcodes = detLines.stream().map(FinVchDet::getSlcode).distinct().count();
            if (distinctSlcodes > 1) {
                ReportLogger.warn("[Untag] Document " + docNo + " has " + distinctSlcodes
                        + " distinct slcode values across its finvchdet lines — using the first line's slcode="
                        + slcode + " per convention.");
            }
            BigDecimal actualAmount = header.getCamount() != null && header.getCamount().signum() != 0
                    ? header.getCamount() : header.getDamount();
            long connectedOUCode = AccountingPeriodResolver.resolveOuCode(jdbi, header.getAdmsiteCodeOwner());

            // Capture BEFORE the untag call — the tags are destroyed the moment it succeeds.
            capturedOut.addAll(captureTags(jdbi, vchcode, docNo));
            if (!capturedOut.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                capturedOut.forEach(c -> sb.append(" ").append(c.getCounterDocNo()).append("/").append(c.getAmount()));
                ReportLogger.business("[Capture] " + capturedOut.size() + " tag(s) captured before untag:" + sb);
            }

            boolean untagged = DocumentAdjustmentFlow.untagIfNeeded(
                    vchcode, docNo, slcode, actualAmount, connectedOUCode);
            ReportLogger.business("Untag check for " + docNo + ": "
                    + (untagged ? "was tagged — untagged via Document Adjustment API" : "not tagged."));
        });
    }

    /** Step "Unpost if Posted (release_status='P') — via API" (AP only — AR has no captured API
     *  contract; its callers unpost through the UI Revert action instead). */
    public static void unpostApVoucherViaApi(String docNo, String releaseStatus) {
        StepExecutor.step("Unpost if Posted (release_status='P') — via API", () -> {
            if (!"P".equals(releaseStatus)) {
                ReportLogger.business("Voucher status=" + releaseStatus + " — unpost not required");
                return;
            }
            Jdbi jdbi = DatabaseManager.getJdbi();
            FinVchMain header = requireHeader(jdbi, docNo, "Unpost");
            long connectedOUCode = AccountingPeriodResolver.resolveOuCode(jdbi, header.getAdmsiteCodeOwner());

            VoucherActionResponse response = ApiSessionFactory.withWebApiSession(session -> {
                long empCode = jdbi.onDemand(HrEmpDao.class).findEcodeByAbbre(session.getCredential().username())
                        .orElseThrow(() -> new IllegalStateException(
                                "[Unpost] Could not resolve ecode for self-acquired API user "
                                + session.getCredential().username()));
                return session.getApVoucherApiClient().revertApVoucher(header.getVchcode(), empCode, connectedOUCode);
            });
            Assert.assertTrue(response.isSuccess(),
                    "[Unpost] API revert for voucher " + docNo + " returned success=false");
            ReportLogger.business("Voucher unposted via API (was release_status='P') for: " + docNo);
        });
    }

    /** {@link #unpostApVoucherViaApi(String, String)} for an API-created voucher: with {@code db.available=no} the call uses {@code ref}. */
    public static void unpostApVoucherViaApi(String docNo, String releaseStatus, com.gsl.testdata.dto.ApiVoucherRef ref) {
        if (ref == null || com.gsl.database.DbAvailability.isAvailable()) {
            unpostApVoucherViaApi(docNo, releaseStatus);
            return;
        }
        StepExecutor.step("Unpost if Posted (release_status='P') — via API", () -> {
            if (!"P".equals(releaseStatus)) {
                ReportLogger.business("Voucher status=" + releaseStatus + " — unpost not required");
                return;
            }
            VoucherActionResponse response = ApiSessionFactory.withWebApiSession(session ->
                    session.getApVoucherApiClient().revertApVoucher(ref.getVchcode(), Long.parseLong(session.getUserId()), ref.getOuCode()));
            Assert.assertTrue(response.allSucceeded(),
                    "[Unpost] API revert for voucher " + docNo + " was refused: " + response.firstFailureMessage());
            ReportLogger.business("Voucher unposted via API (was release_status='P') for: " + docNo);
        });
    }

    private static FinVchMain requireHeader(Jdbi jdbi, String docNo, String tag) {
        return jdbi.onDemand(FinVchMainDao.class).findBySchemeDocno(docNo)
                .orElseThrow(() -> new IllegalStateException(
                        "[" + tag + "] finvchmain row not found for scheme_docno=" + docNo));
    }
}
