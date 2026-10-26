package com.tests.API;

import com.gsl.api.client.GeneralVoucherApiClient;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.database.DbAvailability;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.dataprovider.source.finance.ApiGeneralVoucherEditDataSource;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Live probe (run on demand, not part of any suite) of {@link ApiGeneralVoucherEditDataSource}: loads all three Edit methods for the
 * MANDATORY group — expect header 4, detail 2, payment 3, no fixture failures — prints what each row holds, and removes the vouchers it
 * created. Run with {@code -Ddb.available=no} to prove nothing touches the database.
 */
public class GeneralVoucherEditApiSourceProbe {

    @Test
    public void mandatoryEditRowsAreBuiltViaApi() {
        System.out.println("[EDIT-PROBE] db.available=" + DbAvailability.isAvailable());
        List<MergedFinanceGeneralVoucherData> all = new ArrayList<>();
        int[] expected = {4, 2, 3};
        String[] methods = {"editGeneralVoucherHeader", "editGeneralVoucherDetailLine", "editGeneralVoucherPaymentInfo"};
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < methods.length; i++) {
            List<MergedFinanceGeneralVoucherData> rows = new ApiGeneralVoucherEditDataSource("MANDATORY").load(methods[i]);
            System.out.println("[EDIT-PROBE] " + methods[i] + " -> " + rows.size() + " row(s) (expected " + expected[i] + ")");
            if (rows.size() != expected[i]) problems.add(methods[i] + " emitted " + rows.size() + ", expected " + expected[i]);
            for (MergedFinanceGeneralVoucherData r : rows) {
                System.out.println("[EDIT-PROBE]   " + r.getScenarioTypeLabel() + " | voucher=" + r.getExistingVoucherNo()
                        + " status=" + r.getReleaseStatus() + " date=" + r.getCurrentDocDate()
                        + " newDate=" + (r.getDocumentInformation() == null ? null : r.getDocumentInformation().getDocumentdate())
                        + " lines=" + (r.getExpenseIncomeDetailList() == null ? 0 : r.getExpenseIncomeDetailList().size())
                        + " detailEdit=" + (r.getEditedDetailLine() == null ? null : r.getEditedDetailLine().getShape())
                        + " payEdit=" + (r.getPaymentEditTarget() == null ? null : r.getPaymentEditTarget().getShape())
                        + " expectedValidation=" + r.getExpectedValidation()
                        + (r.getFixtureFailure() != null ? " | FIXTURE FAILURE: " + r.getFixtureFailure() : ""));
                if (r.getFixtureFailure() != null) problems.add(r.getScenarioTypeLabel() + ": " + r.getFixtureFailure());
                all.add(r);
            }
        }
        ApiSessionFactory.withWebApiSession(session -> {
            GeneralVoucherApiClient api = session.getGeneralVoucherApiClient();
            long emp = Long.parseLong(session.getUserId());
            long ou = api.fetchUserContext().getOucode();
            for (MergedFinanceGeneralVoucherData r : all) {
                if (r.getReservedDocumentKey() == null) continue;
                long code = Long.parseLong(r.getReservedDocumentKey());
                try {
                    if (api.fetchRootView(code, ou, emp).isPosted()) api.revertGeneralVoucherOrThrow(code, emp, ou);
                    api.deleteGeneralVoucherOrThrow(code, emp, ou);
                    System.out.println("[EDIT-PROBE] removed " + r.getExistingVoucherNo());
                } catch (RuntimeException e) {
                    System.out.println("[EDIT-PROBE] could not remove " + r.getExistingVoucherNo() + ": " + e.getMessage());
                }
            }
            return null;
        });
        Assert.assertTrue(problems.isEmpty(), "problems: " + problems);
    }
}
