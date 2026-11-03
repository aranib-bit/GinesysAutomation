package com.gsl.approval.facts;

import com.gsl.approval.model.ApApprovalVariable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The facts of a saved AP Voucher, keyed by rule variable code, as {@code ApprovalRuleResolver} reads them. Lookup variables (site, cash/bank,
 * created person, entry source, on account, sub-ledger) are the CODES the rule stores, not display names. Only the values the caller sets are
 * present: a rule that needs an unset one fails as {@code MISSING_FACT} rather than being guessed.
 */
public final class ApVoucherApprovalFacts {

    private final Map<String, Object> facts = new LinkedHashMap<>();

    private ApVoucherApprovalFacts() {}

    public static ApVoucherApprovalFacts builder() {
        return new ApVoucherApprovalFacts();
    }

    private ApVoucherApprovalFacts put(ApApprovalVariable variable, Object value) {
        facts.put(variable.varCode(), value);
        return this;
    }

    public ApVoucherApprovalFacts ownerSite(int siteCode) {
        return put(ApApprovalVariable.OWNER_SITE, siteCode);
    }

    public ApVoucherApprovalFacts voucherNo(String voucherNo) {
        return put(ApApprovalVariable.VOUCHER_NO, voucherNo);
    }

    /** The cash/bank ledger's gl code. */
    public ApVoucherApprovalFacts cashBank(int glCode) {
        return put(ApApprovalVariable.CASH_BANK, glCode);
    }

    public ApVoucherApprovalFacts onAccount(String yesNoCode) {
        return put(ApApprovalVariable.ON_ACCOUNT, yesNoCode);
    }

    public ApVoucherApprovalFacts voucherDate(LocalDate date) {
        return put(ApApprovalVariable.VOUCHER_DATE, date);
    }

    public ApVoucherApprovalFacts reference(String reference) {
        return put(ApApprovalVariable.REFERENCE, reference);
    }

    public ApVoucherApprovalFacts paymentAmount(BigDecimal amount) {
        return put(ApApprovalVariable.PAYMENT_AMOUNT, amount);
    }

    public ApVoucherApprovalFacts createdPerson(int userCode) {
        return put(ApApprovalVariable.CREATED_PERSON, userCode);
    }

    public ApVoucherApprovalFacts createdOn(LocalDate date) {
        return put(ApApprovalVariable.CREATED_ON, date);
    }

    public ApVoucherApprovalFacts totalAmount(BigDecimal amount) {
        return put(ApApprovalVariable.TOTAL_AMOUNT, amount);
    }

    /** The entry source code, e.g. {@code WEB}. */
    public ApVoucherApprovalFacts entrySource(String code) {
        return put(ApApprovalVariable.ENTRY_SOURCE, code);
    }

    public ApVoucherApprovalFacts remarks(String remarks) {
        return put(ApApprovalVariable.REMARKS, remarks);
    }

    /** The sub-ledger codes of the voucher's lines (a line condition holds when any of them matches). */
    public ApVoucherApprovalFacts subLedgers(Collection<Integer> slCodes) {
        return put(ApApprovalVariable.SUB_LEDGER, slCodes);
    }

    public Map<String, Object> build() {
        // Not Map.copyOf: a caller may legitimately set a null value (e.g. no remarks), which a NULL/NOTNULL rule then reads.
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }
}
