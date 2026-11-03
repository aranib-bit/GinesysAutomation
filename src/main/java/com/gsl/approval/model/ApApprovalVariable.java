package com.gsl.approval.model;

import static com.gsl.approval.model.ApprovalDataType.DATE;
import static com.gsl.approval.model.ApprovalDataType.LOOKUP;
import static com.gsl.approval.model.ApprovalDataType.NUMBER;
import static com.gsl.approval.model.ApprovalDataType.TEXT;

/** The AP Voucher rule variables (HAR: ApprovalRule/GetVariables?entityCode=AP; the Approval Rule screen lists the same). UDFs are handled generically. */
public enum ApApprovalVariable implements ApprovalVariable {
    OWNER_SITE("H", LOOKUP),
    VOUCHER_NO("H", TEXT),
    CASH_BANK("H", LOOKUP),
    ON_ACCOUNT("H", LOOKUP),
    VOUCHER_DATE("H", DATE),
    REFERENCE("H", TEXT),
    PAYMENT_AMOUNT("H", NUMBER),
    CREATED_PERSON("H", LOOKUP),
    CREATED_ON("H", DATE),
    TOTAL_AMOUNT("H", NUMBER),
    ENTRY_SOURCE("H", LOOKUP),
    REMARKS("H", TEXT),
    SUB_LEDGER("L", LOOKUP);

    private final String basis;
    private final ApprovalDataType dataType;

    ApApprovalVariable(String basis, ApprovalDataType dataType) {
        this.basis = basis;
        this.dataType = dataType;
    }

    @Override
    public String varCode() {
        return name();
    }

    @Override
    public String basis() {
        return basis;
    }

    @Override
    public ApprovalDataType dataType() {
        return dataType;
    }
}
