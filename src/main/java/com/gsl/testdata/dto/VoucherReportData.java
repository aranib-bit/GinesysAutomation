package com.gsl.testdata.dto;

import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherDocumentHeader;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSelectDocRow;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSubLedgerRow;
import com.gsl.testdata.MasterTestData;
import com.gsl.utils.dataprovider.fixture.finance.NegativeValidation;

import java.util.List;

/**
 * Common contract for all Finance voucher test data DTOs.
 *
 * <p>Both {@link MergedFinanceApVoucherData} and {@link MergedFinanceArVoucherData}
 * implement this interface, enabling the report layer
 * ({@link com.gsl.reports.VoucherExtentDescriptionLogger} and utilities)
 * to work against a single type rather than duplicating logic per voucher.
 */
public interface VoucherReportData {

    MasterTestData getMasterTestData();

    /** Returns the document header fields common to all voucher types. */
    VoucherDocumentHeader getDocumentInformation();

    /** All sub-ledger rows. AP carries {@code amountDr}; AR carries {@code amountCr}. */
    List<? extends VoucherSubLedgerRow> getSubLedgerInformationList();

    /**
     * Selected documents for adjustment (On Account = No / AR always).
     * Returns an empty list rather than null when not applicable.
     */
    List<? extends VoucherSelectDocRow> getSelectDocumentList();

    /**
     * Payment information rows. Returns an empty list rather than null when not applicable.
     */
    List<? extends VoucherPaymentInfo> getPaymentInformationList();

    boolean isNeedsPaymentInfoWarning();

    /** Non-null only for negative/boundary scenarios; null for positive tests. */
    NegativeValidation getExpectedValidation();

    /**
     * Structured gathering log lines set by the data source after DB resolution.
     * Lines prefixed with {@code "[DB Query"} are routed to the
     * "SQL Queries Executed" section by {@link com.gsl.reports.DbGatheringExtentLogger}.
     */
    List<String> getDbGatheringLog();

    /** Human-readable label for the scenario (e.g. {@code "SELECT_DOCUMENT"}, {@code "FIFO_SETOFF"}). */
    String getScenarioTypeLabel();
}
