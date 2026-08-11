package com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher;

import com.gsl.converter.PaymentModeConverter;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Payment info for General Voucher's Cash/Bank counter-leg. Structurally the same shape
 * as {@code APVoucher.PaymentInformation}/{@code ARVoucher.PaymentInformation} (both
 * implement {@link VoucherPaymentInfo}), but GV attaches exactly one instance per voucher
 * to the single counter-leg row — not one per sub-ledger line, since GV has no per-line
 * payment concept (see {@code GeneralVoucherPaymentVariant}'s class Javadoc).
 *
 * <p>{@code lineNo} is a fixed sentinel ({@code "CASHBANK"}) rather than an
 * {@code ExpenseIncomeDetail.lineNo} value — the counter-leg row isn't one of the
 * Expense/Income Detail grid rows this payment info is unrelated to those line numbers.
 *
 * <p><b>{@code instrumentNo}/{@code instrumentDate} are populated only once GV's Payment
 * entry control is discovered</b> (see the plan's "Blocking prerequisite" callout) — until
 * then, {@link com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherPaymentVariant#CHEQUE}/
 * {@code NEFT_RTGS} combinations are skipped before this class is ever built with real
 * instrument data.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Sheet(name = "PaymentInformation")
public class PaymentInformation implements VoucherPaymentInfo {

    @Column(name = "TestCaseID")
    private String testCaseID;

    @Column(name = "LineNo")
    private String lineNo;

    @Column(name = "Payment Mode", converter = PaymentModeConverter.class)
    private PaymentSelectionType paymentMode;

    @Column(name = "Instrument No")
    private String instrumentNo;

    @Column(name = "Instrument Date")
    private String instrumentDate;

    @Column(name = "Drawn On")
    private String drawnOn;

    @Column(name = "Reference")
    private String reference;
}
