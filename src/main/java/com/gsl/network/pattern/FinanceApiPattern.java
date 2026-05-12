package com.gsl.network.pattern;

import java.util.List;

/**
 * API URL patterns for the Finance module.
 * Each constant's urlPatterns are relative paths — the base URL from config is
 * prepended at match time so no enum changes are needed when switching environments.
 *
 * Confirmed URLs (from browser Network tab against stage-ideal.ginesys.cloud):
 *   AR Voucher → /Finance/api/vouchers/ar
 *   AP Voucher → /Finance/api/vouchers/ap
 *
 * Add more constants as additional Finance endpoints are confirmed.
 */
public enum FinanceApiPattern implements ApiPattern {

    AR_VOUCHER      ("AR Voucher",      List.of("/Finance/api/vouchers/ar")),
    AP_VOUCHER      ("AP Voucher",      List.of("/Finance/api/vouchers/ap")),
    GENERAL_VOUCHER ("General Voucher", List.of("/Finance/api/vouchers/gv")); // ← confirm path

    private final String       displayName;
    private final List<String> urlPatterns;

    FinanceApiPattern(String displayName, List<String> urlPatterns) {
        this.displayName  = displayName;
        this.urlPatterns  = urlPatterns;
    }

    @Override public String       displayName()  { return displayName; }
    @Override public List<String> urlPatterns()  { return urlPatterns; }
}
