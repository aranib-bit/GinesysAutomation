package com.tests.API;

import com.gsl.api.bulk.VoucherBulkProvisioner;
import com.gsl.api.model.apvoucher.ApVoucherAdjustmentDetail;
import com.gsl.api.model.apvoucher.ApVoucherCreateRequest;
import com.gsl.api.model.apvoucher.TdsSectionLookupResult;
import com.gsl.api.model.arvoucher.ArVoucherCreateRequest;
import com.gsl.api.model.common.CashBankLedgerLookupResult;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.common.UserSiteContext;
import com.gsl.api.model.generalvoucher.GeneralVoucherCreateRequest;
import com.gsl.api.model.generalvoucher.GlLedgerLookupResult;
import com.gsl.api.webapi.factory.ApVoucherRequestFactory;
import com.gsl.api.webapi.factory.ArVoucherRequestFactory;
import com.gsl.api.webapi.factory.GeneralVoucherRequestFactory;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/** The voucher create bodies are assembled from resolved values alone (no HTTP): TDS arithmetic, advance vs adjusting, cash vs bank pay mode. */
public class VoucherRequestFactoryUnitTest {

    private static final SubLedgerLookupResult SL = SubLedgerLookupResult.builder().slCode(42).slName("Vendor A").slid("V1").glCode(7).className("Supplier").build();

    private static ApVoucherRequestFactory.Inputs ap(double gross, TdsSectionLookupResult tds, Long journal, List<ApVoucherAdjustmentDetail> adj) {
        return new ApVoucherRequestFactory.Inputs(SL, 138, 9, 3, 3, 1, 513150, "2026-10-30T00:00:00", gross, tds, journal, adj, List.of());
    }

    @Test
    public void anAdvanceWithoutTdsIsOnAccountAndCarriesTheGrossAmount() {
        ApVoucherCreateRequest r = ApVoucherRequestFactory.create(ap(1000, null, null, null), "rid-1");
        assertTrue(r.isOnAccount());
        assertNull(r.getAutoCreatedJournalDocSchemeCode());
        assertFalse(r.getApVoucherDetail().get(0).isDeductTDS());
        assertEquals(r.getApVoucherDetail().get(0).getAmount(), 1000.0);
        assertEquals(r.getRequestId(), "rid-1");
    }

    @Test
    public void tdsIsRoundedAndDeductedFromTheNetPayable() {
        TdsSectionLookupResult tds = TdsSectionLookupResult.builder().code(5).tdsRate(10.5).build();
        ApVoucherCreateRequest r = ApVoucherRequestFactory.create(ap(1001, tds, 77L, null), "rid-2");
        assertEquals(r.getAutoCreatedJournalDocSchemeCode(), Long.valueOf(77));
        var line = r.getApVoucherDetail().get(0);
        assertTrue(line.isDeductTDS());
        double roundOff = Math.round(1001 * 10.5 / 100.0);
        assertEquals((double) line.getTdsRoundOffAmt(), roundOff);
        assertEquals((double) line.getNetPayable(), 1001 - roundOff);
        assertEquals((double) line.getAmount(), 1001 - roundOff);
    }

    @Test
    public void anAdjustingVoucherIsNotOnAccountAndTagsTheDocuments() {
        ApVoucherAdjustmentDetail doc = ApVoucherAdjustmentDetail.builder().docCode(99).documentType("PSM").actualAmount(500).adjustedAmount(500).build();
        ApVoucherCreateRequest r = ApVoucherRequestFactory.create(ap(500, null, null, List.of(doc)), "rid-3");
        assertFalse(r.isOnAccount());
        assertEquals(r.getApVoucherDetail().get(0).getApVoucherAdjustmentDetail(), List.of(doc));
    }

    @Test
    public void anArVoucherIsACashReceiptForTheSubLedger() {
        ArVoucherRequestFactory.Inputs in = new ArVoucherRequestFactory.Inputs(SL, 3, "Main", 138, 9, 3, 1, 513150, "2026-10-30T00:00:00", 250, List.of());
        ArVoucherCreateRequest r = ArVoucherRequestFactory.create(in, "rid-4");
        var line = r.getArVoucherDetail().get(0);
        assertEquals(line.getPaymentMode(), "C");
        assertEquals(line.getAmount(), 250.0);
        assertEquals(line.getSubLedgerCode(), 42L);
        assertEquals(r.getCashBankGLCode(), 9L);
    }

    private static GeneralVoucherCreateRequest gv(String cashOrBank, boolean debit) {
        UserSiteContext.Site site = new UserSiteContext.Site();
        site.setSiteName("HO");
        CashBankLedgerLookupResult header = CashBankLedgerLookupResult.builder().glCode(9).sourceType(cashOrBank).build();
        GlLedgerLookupResult gl = GlLedgerLookupResult.builder().glCode(11).glName("Rent").build();
        return GeneralVoucherRequestFactory.simple(new GeneralVoucherRequestFactory.Inputs(site, 1, 513150, header, 12, gl, null, debit,
                "2026-10-30T00:00:00", true, List.of()), "rid-5");
    }

    @Test
    public void aGeneralVoucherLineFollowsTheLedgerSideAndThePaymentSource() {
        var cashDebit = gv("C", true).getExpenseIncomeDetail().get(0);
        assertEquals(cashDebit.getPaymentMode(), "H");
        assertEquals(cashDebit.getExpense(), 1);
        assertEquals(cashDebit.getIncome(), 0);
        var bankCredit = gv("B", false).getExpenseIncomeDetail().get(0);
        assertEquals(bankCredit.getPaymentMode(), "C");
        assertEquals(bankCredit.getIncome(), 1);
        assertEquals(cashDebit.getSiteApplicable(), "N");
    }

    @Test
    public void theVoucherCodeIsReadFromK6sOkLineOnly() {
        assertEquals(VoucherBulkProvisioner.parseCreatedCode("CreateAPVoucher [VU 2 iter 4] OK id=? status=200 code=551234 ms=912"), Long.valueOf(551234));
        assertNull(VoucherBulkProvisioner.parseCreatedCode("CreateAPVoucher [VU 2 iter 4] FAIL id=? status=400 body=code=5"));
        assertNull(VoucherBulkProvisioner.parseCreatedCode("some other line"));
    }
}