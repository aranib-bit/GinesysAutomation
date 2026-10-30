package com.gsl.utils.dataprovider.source.finance;

import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.model.generalvoucher.GeneralVoucherCreateResponse;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DbAvailability;
import com.gsl.enums.GlAccountType;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.dto.GeneralVoucherOperationMode;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.TestDataSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * {@code testdata.source=API} source for {@code deleteGeneralVoucher}: creates one fresh <b>Unposted</b> General Voucher through the
 * Finance API for the positive row. The closed-period negative row keeps the DB claim when {@code db.available=yes} and is omitted
 * otherwise (no API creates a voucher in a closed period).
 */
public class ApiGeneralVoucherDeleteDataSource implements TestDataSource<MergedFinanceGeneralVoucherData> {

    @Override
    public List<MergedFinanceGeneralVoucherData> load(String testMethodName) {
        List<MergedFinanceGeneralVoucherData> results = new ArrayList<>();
        results.add(ApiSessionFactory.withWebApiSession(session -> {
            long empCode = Long.parseLong(session.getUserId());
            GeneralVoucherCreateResponse created = new com.gsl.service.GeneralVoucherService(session.getGeneralVoucherApiClient())
                    .createGeneralVoucherWithDefaults(GlAccountType.EXPENSE, 0, 0, empCode, false);
            List<String> log = GatheringLog.start("API DATA GATHERING - UNPOSTED VOUCHER CREATED (GENERAL VOUCHER DELETE)");
            log.add("  Created voucher scheme_docno='" + created.getDocScheme() + "' (vchcode=" + created.vchcode() + ")");
            GatheringLog.end(log);
            DocumentInformation docInfo = new DocumentInformation(
                    "GV_DELETE_POSITIVE", created.getDocScheme(), LocalDate.now(), null, null, null);
            return MergedFinanceGeneralVoucherData.builder()
                    .scenarioTypeLabel("GV_DELETE_POSITIVE")
                    .existingVoucherNo(created.getDocScheme())
                    .releaseStatus("U")
                    .documentInformation(docInfo)
                    .reservedDocumentKey(String.valueOf(created.vchcode()))
                    .operationMode(GeneralVoucherOperationMode.DELETE)
                    .expenseIncomeDetailList(List.of())
                    .siteAllocationList(List.of())
                    .dbGatheringLog(log)
                    .invocationIndex(1)
                    .totalInvocations(1)
                    .build();
        }));

        if (DbAvailability.isAvailable()) {
            DbGeneralVoucherDeleteDataSource.resolveClosedPeriodNegativeCandidate(DatabaseManager.getJdbi(), new HashSet<>())
                    .ifPresent(results::add);
        } else {
            Log.warn("[API] GeneralVoucherDelete: closed-period negative row omitted - db.available=no.");
        }

        TestDataSource.stampInvocations(results);
        int total = results.size();
        Log.info("[API] GeneralVoucherDelete emitted {} row(s) for '{}'", total, testMethodName);
        return results;
    }
}
