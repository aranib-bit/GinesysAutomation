package com.gsl.enums;

import com.gsl.model.adm.AdmOuMonth;

/**
 * Which per-month closing status a document's own module actually cares about —
 * {@code admou_month} splits period-closing into independent Finance vs. Inventory flags
 * ({@code fin_month_status}/{@code inv_month_status}), and a given document type only ever
 * checks one of them.
 *
 * <p>Per business classification: General Voucher, AP Voucher, and AR Voucher are all
 * {@link #FINANCE}; Receipts are {@link #INVENTORY}. Only {@link #FINANCE} is wired by any
 * test today (GV/AR Edit) — {@link #INVENTORY} is modeled here so the same
 * {@code AccountingPeriodResolver} utility is ready, unchanged, whenever an Inventory-domain
 * Edit test (e.g. Receipts) is built.
 */
public enum PeriodDomain {
    FINANCE,
    INVENTORY;

    /** The {@link AdmOuMonth} status column this domain checks. */
    public String statusOf(AdmOuMonth month) {
        return this == FINANCE ? month.getFinMonthStatus() : month.getInvMonthStatus();
    }
}
