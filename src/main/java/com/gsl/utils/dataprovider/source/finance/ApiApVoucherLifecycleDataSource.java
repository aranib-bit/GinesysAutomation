package com.gsl.utils.dataprovider.source.finance;

import com.gsl.api.client.ApVoucherApiClient;
import com.gsl.api.client.DocumentAdjustmentApiClient;
import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.model.apvoucher.ApVoucherAdjustmentDetail;
import com.gsl.api.model.apvoucher.ApVoucherCreateResponse;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.database.DataAvailabilityGuard;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.database.DocumentSelectionResult;
import com.gsl.database.query.PendingAdjQuery;
import com.gsl.enums.CashBankType;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.model.finance.SlNameWithId;
import com.gsl.testdata.dto.ApiVoucherRef;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherExecutionGroup;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherTagState;
import com.gsl.utils.dataprovider.source.TestDataSource;
import org.testng.SkipException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code testdata.source=API} source for {@code revertApVoucher} and {@code deleteApVoucher}: instead of claiming a live Posted AP Voucher
 * from the database it creates one per tag state through the Finance API, so every row owns a fresh, unreserved voucher.
 *
 * <ul>
 *   <li>{@code UNTAGGED}: a Customer Advance AP Voucher (the flow the backfill strategies already use).</li>
 *   <li>{@code TAGGED}: an AP Voucher whose line adjusts a pending document (HAR: APVoucherwithalllookupsDocAdjustment), which tags it. The
 *       pending document comes from {@link ApiPendingDocumentSelector} (reserved like in Create), backfilled on a shortfall.</li>
 * </ul>
 * The created voucher's code, sub-ledger, amount and tag state travel on the row as {@link ApiVoucherRef}, so the test can untag/unpost it
 * through the API with {@code db.available=no}. The release status is read back from {@code rootview} (not assumed).
 */
public class ApiApVoucherLifecycleDataSource implements TestDataSource<MergedFinanceApVoucherData> {

    /** Amount the tagged voucher adjusts against its document (the HAR adjusted 1 of 50000). */
    private static final BigDecimal ADJUST_AMOUNT = BigDecimal.ONE;

    private final String xmlGroupParam;
    private final String operation;

    /** @param operation "REVERT" or "DELETE", used only for labelling the report */
    public ApiApVoucherLifecycleDataSource(String xmlGroupParam, String operation) {
        this.xmlGroupParam = xmlGroupParam;
        this.operation = operation;
    }

    @Override
    public List<MergedFinanceApVoucherData> load(String methodName) {
        ApVoucherExecutionGroup activeGroup = ApVoucherTagStateClaimSupport.resolveActiveGroup(xmlGroupParam);
        List<MergedFinanceApVoucherData> results = new ArrayList<>();
        if (ApVoucherTagStateClaimSupport.matchesGroup(activeGroup, ApVoucherExecutionGroup.MANDATORY)) {
            for (ApVoucherTagState tagState : List.of(ApVoucherTagState.UNTAGGED, ApVoucherTagState.TAGGED)) {
                results.add(ApiSessionFactory.withWebApiSession(session -> createRow(session, tagState)));
            }
        }
        TestDataSource.stampInvocations(results);
        int total = results.size();
        Log.info("[API] ApVoucher{} emitted {} row(s) for '{}' — group filter={}", operation, total, methodName, activeGroup);
        return results;
    }

    /** Creates {@code count} TAGGED AP Vouchers (each adjusting a pending document) — the DB-mode TAGGED-cell backfill. */
    public static void createTaggedVouchers(int count) {
        for (int i = 0; i < count; i++) {
            ApiSessionFactory.withWebApiSession(session -> new ApiApVoucherLifecycleDataSource(null, "BACKFILL")
                    .createRow(session, ApVoucherTagState.TAGGED));
        }
    }

    private MergedFinanceApVoucherData createRow(ApiSessionBundle session, ApVoucherTagState tagState) {
        List<String> log = new ArrayList<>();
        GatheringLog.end(log);
        log.add("  API DATA GATHERING — AP VOUCHER CREATED (" + operation + ", tagState=" + tagState + ")");
        GatheringLog.end(log);

        ApVoucherApiClient ap = session.getApVoucherApiClient();
        ApiVoucherSupport.Owner owner = ApiVoucherSupport.resolveOwnerAnyCashBank(session);
        log.add(ApiVoucherSupport.ownerLine(owner));

        ApVoucherCreateResponse created;
        SubLedgerLookupResult sl;
        boolean tagged = tagState == ApVoucherTagState.TAGGED;
        BigDecimal amount;
        List<com.gsl.testdata.Finance.Operations.Vouchers.Common.CapturedTag> capturedTags = List.of();
        if (!tagged) {
            sl = ap.resolveSubLedger(owner.siteCode(), owner.ouCode(), (int) owner.empCode(), owner.workDate(), 0);
            created = new com.gsl.service.ApVoucherService(ap).createApVoucherWithDefaults(sl, owner.siteCode(), owner.ouCode(), owner.empCode(), ApiVoucherSupport.DEFAULT_QTY, ApiVoucherSupport.DEFAULT_RATE, false, null);
            amount = BigDecimal.valueOf((long) ApiVoucherSupport.DEFAULT_QTY * ApiVoucherSupport.DEFAULT_RATE);
        } else {
            DocumentAdjustmentApiClient adj = session.getDocumentAdjustmentApiClient();
            List<SubLedgerLookupResult> all = com.gsl.api.client.ExtinctPartyFilter.excluding(
                    ap.fetchSubLedgers(owner.siteCode(), owner.ouCode(), (int) owner.empCode(), owner.workDate()), SubLedgerLookupResult::getSlCode,
                    com.gsl.api.client.ExtinctPartyFilter.extinctSlCodes(session.getVendorApiClient(), owner.siteCode(), owner.ouCode(), owner.workDate()));
            DocumentSelectionResult selection = DataAvailabilityGuard.selectOrBackfill(
                    () -> ApiPendingDocumentSelector.select(adj, ApiPendingDocumentSelector.Domain.AP, all, owner.ouCode(), owner.workDate(),
                            1, 1, false, null, false),
                    1, ApiApVoucherDataSource.backfillFor(com.gsl.utils.dataprovider.fixture.finance.ApVoucherFixtureOptions.defaults(),
                            all, all, owner, 1));
            SlNameWithId picked = selection.getSelectedSls().get(0);
            String key = PendingAdjQuery.compositeKey(picked.getSlName(), picked.getSlId());
            PendingSlDocument doc = selection.getDocsBySlKey().get(key).get(0);
            sl = all.stream().filter(s -> picked.getSlId().equals(s.getSlid()) && picked.getSlName().equals(s.getSlName())).findFirst()
                    .orElseThrow(() -> new SkipException("Selected sub-ledger '" + picked.getSlName() + "' vanished from the lookup."));
            long docCode = pendingDocCode(adj, owner, sl, doc.getDocumentNo());
            BigDecimal adjusted = ADJUST_AMOUNT.min(doc.getPending().abs());
            ApVoucherAdjustmentDetail detail = ApVoucherAdjustmentDetail.builder()
                    .docCode(docCode)
                    .documentType(doc.getEntype())
                    .actualAmount(doc.getPending().abs().doubleValue())
                    .adjustedAmount(adjusted.doubleValue())
                    .invoiceAdjustedAmountAfterDiscount(adjusted.doubleValue())
                    .build();
            log.add("  Adjusting document '" + doc.getDocumentNo() + "' (code=" + docCode + ", type=" + doc.getEntype() + ", pending=" + doc.getPending() + ")");
            created = new com.gsl.service.ApVoucherService(ap).createApVoucherWithDefaults(sl, owner.siteCode(), owner.ouCode(), owner.empCode(), ApiVoucherSupport.DEFAULT_QTY, ApiVoucherSupport.DEFAULT_RATE, false, null, List.of(detail));
            amount = adjusted;
            capturedTags = List.of(new com.gsl.testdata.Finance.Operations.Vouchers.Common.CapturedTag(doc.getDocumentNo(), adjusted));
            DocumentReservationRegistry.release(doc.getDocumentNo());   // the voucher now holds its own slice of the pending; the reservation ends here
        }

        long vchcode = Long.parseLong(created.getVoucherCode());
        String status = ap.fetchDocumentStatus(vchcode, owner.ouCode(), owner.empCode());
        log.add("  Created voucher scheme_docno='" + created.getDocScheme() + "' (vchcode=" + vchcode + "), sub-ledger='" + sl.getSlName()
                + "', tagged=" + tagged + ", status read back='" + status + "'");
        GatheringLog.end(log);

        // Edit applies new header values (the date stays as created; only reference/remarks change, like the DB source).
        com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.DocumentInformation docInfo = "EDIT".equals(operation)
                ? new com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.DocumentInformation(
                        created.getDocScheme(), null, null, null,
                        com.gsl.utils.Faker.FakerUtils.randomRemarks(), java.time.LocalDate.now(),
                        com.gsl.utils.Faker.FakerUtils.randomReference(), null)
                : null;

        return MergedFinanceApVoucherData.builder()
                .existingVoucherNo(created.getDocScheme())
                .releaseStatus(ApiVoucherSupport.releaseStatusOrPosted(status))
                .documentInformation(docInfo)
                .apiVoucherRef(ApiVoucherRef.builder().vchcode(vchcode).docNo(created.getDocScheme()).slCode(sl.getSlCode())
                        .amount(amount).ouCode(owner.ouCode()).tagged(tagged).capturedTags(capturedTags).build())
                .dbGatheringLog(log)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /** The pending-document list carries the document's {@code entCode}, which the adjustment detail needs as {@code docCode}. */
    private static long pendingDocCode(DocumentAdjustmentApiClient adj, ApiVoucherSupport.Owner owner, SubLedgerLookupResult sl, String documentNo) {
        return adj.fetchPendingDocuments(DocumentAdjustmentApiClient.MODULE_AP, sl.getGlCode(), sl.getSlCode(), owner.ouCode(), owner.workDate()).stream()
                .filter(d -> documentNo.equals(d.getDocument_No()))
                .map(d -> Long.parseLong(d.getEntCode()))
                .findFirst()
                .orElseThrow(() -> new SkipException("Pending document '" + documentNo + "' is no longer offered for sub-ledger '" + sl.getSlName() + "'."));
    }

    }
