package com.gsl.enums;

/**
 * Contract implemented by each application's per-module side-menu enum (e.g.
 * {@code com.gsl.pages.enums.Finance.Operations.OperationsMenuType}) so that generic framework
 * code — {@code SubModuleResolver} (looks up a constant by module + short code) and
 * {@code ExtentReport} (shards report files per module/sub-module/operation) — can work with any
 * sub-module without depending on its concrete enum type.
 */
public interface ISubModule {
    /** Human-readable menu item label, e.g. "AR Voucher". */
    String getName();
    /** Side-menu group/section this sub-module is listed under, e.g. "Vouchers". */
    String getGroup();
    /** The app's internal menu code for this sub-module, e.g. {@code "FinanceARVoucher"} — used to key report file/dir names and (via {@code SubModuleResolver}) to resolve a constant back from that code. */
    String getShortCode();
}
