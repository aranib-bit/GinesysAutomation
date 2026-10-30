package com.gsl.utils.dataprovider.source.finance;

import com.gsl.api.client.ArVoucherApiClient;
import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.integration.ArVoucherDataSetup;
import com.gsl.api.model.arvoucher.ArVoucherCreateResponse;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.ApiVoucherRef;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.TestDataSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code testdata.source=API} source for {@code deleteArVoucher} and {@code editArVoucher}: creates one fresh AR Voucher through the Finance
 * API (the call the backfill strategies use) and prepares the row for the operation - the AP twin is {@link ApiApVoucherLifecycleDataSource}.
 * The release status is read back from {@code rootview}. Delete's closed-period negative row of the DB source needs a voucher in a closed
 * month, which no API creates, so it is not emitted.
 */
public class ApiArVoucherLifecycleDataSource implements TestDataSource<MergedFinanceArVoucherData> {

    /** Which test the row feeds. */
    public enum Operation { DELETE, EDIT }

    private final Operation operation;

    public ApiArVoucherLifecycleDataSource(Operation operation) {
        this.operation = operation;
    }

    @Override
    public List<MergedFinanceArVoucherData> load(String testMethodName) {
        MergedFinanceArVoucherData row = ApiSessionFactory.withWebApiSession(session -> createRow(session, testMethodName));
        List<MergedFinanceArVoucherData> rows = new ArrayList<>(List.of(row));
        TestDataSource.stampInvocations(rows);
        Log.info("[API] ArVoucher{} emitted 1 row for '{}'", operation, testMethodName);
        return rows;
    }

    private MergedFinanceArVoucherData createRow(ApiSessionBundle session, String testMethodName) {
        ApiVoucherSupport.Owner owner = ApiVoucherSupport.resolveOwnerAnyCashBank(session);
        ArVoucherApiClient ar = session.getArVoucherApiClient();
        // The create call resolves its sub-ledger with exactly this lookup (class code 0 = first), so this is the SL the voucher carries.
        SubLedgerLookupResult sl = ar.resolveSubLedger(owner.siteCode(), owner.ouCode(), (int) owner.empCode(), owner.workDate(), 0);
        ArVoucherCreateResponse created = ArVoucherDataSetup.setupArVoucherForTest(
                ar, 0, owner.siteCode(), owner.ouCode(), owner.empCode(), ApiVoucherSupport.DEFAULT_QTY, ApiVoucherSupport.DEFAULT_RATE);
        long vchcode = Long.parseLong(created.getVoucherCode());
        String status = ar.fetchDocumentStatus(vchcode, owner.ouCode(), owner.empCode());
        BigDecimal amount = BigDecimal.valueOf((long) ApiVoucherSupport.DEFAULT_QTY * ApiVoucherSupport.DEFAULT_RATE);

        List<String> log = GatheringLog.start("API DATA GATHERING — AR VOUCHER CREATED (" + operation + ")");
        log.add(ApiVoucherSupport.ownerLine(owner));
        log.add("  Created voucher scheme_docno='" + created.getDocScheme() + "' (vchcode=" + vchcode + "), sub-ledger='" + sl.getSlName()
                + "', status read back='" + status + "'");

        MergedFinanceArVoucherData.MergedFinanceArVoucherDataBuilder row = MergedFinanceArVoucherData.builder()
                .existingVoucherNo(created.getDocScheme())
                .releaseStatus(ApiVoucherSupport.releaseStatusOrPosted(status))
                .apiVoucherRef(ApiVoucherRef.builder().vchcode(vchcode).docNo(created.getDocScheme()).slCode(sl.getSlCode())
                        .amount(amount).ouCode(owner.ouCode()).tagged(false).build())
                .masterTestData(new MasterTestData(testMethodName, null, null, null, "FIN-AR-" + operation + "-" + vchcode, 1))
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(List.of())
                .paymentProvided(false)
                .needsPaymentInfoWarning(false);

        if (operation == Operation.EDIT) {
            LocalDate newDate = LocalDate.now();
            DocumentInformation docInfo = new DocumentInformation(
                    created.getDocScheme(), null, null, null,   // owner site / voucher no / cash-bank stay as created: the Edit page has nothing to pick
                    FakerUtils.randomRemarks(), newDate, FakerUtils.randomReference());
            log.add("  New header: date=" + newDate + ", reference='" + docInfo.getReference() + "', remarks='" + docInfo.getRemarks() + "'");
            row.currentDocDate(newDate)
                    .documentInformation(docInfo)
                    .subLedgerInformationList(List.of(new SubLedgerInformation(
                            null, "1", sl.getSlName(), sl.getSlid(), sl.getGlName(), null, null, "N", amount, null, null)));
        } else {
            row.documentInformation(null).subLedgerInformationList(List.of());
        }
        GatheringLog.end(log);
        return row.dbGatheringLog(log).build();
    }
}
