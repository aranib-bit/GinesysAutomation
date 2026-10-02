package com.gsl.pages.enums.Procurement.Operations;

import com.gsl.enums.ISubModule;

/**
 * Procurement &gt; Operations' side-menu sub-items (grouped under Order/Goods In-Out/Invoicing/
 * Service-Expenses/Others headers) — consumed by {@code
 * NavigationUtils.navigateToProcurementSideMenu(HomePage, MainMenuType, OperationsMenuType, Class)},
 * which passes it to {@code OperationsSideMenuComponent.clickMenuItem(ISubModule, Class)} to
 * expand the right group and click the matching item. Also registered in {@code
 * SubModuleResolver}'s {@code PROCUREMENT} entry so a test's declared {@code fw.subModule} short
 * code (e.g. {@code "ProcGRCAdhoc"}) resolves back to one of these constants.
 */
public enum OperationsMenuType implements ISubModule {

    PURCHASE_ORDER                   ("Purchase Order",                   "ProcOrd",                   "Order"),
    CANCEL_PURCHASE_ORDER            ("Cancel Purchase Order",            "ProcOrdCnl",                "Order"),
    RECEIPTS                         ("Receipts",                         "ProcGRCAdhoc",              "Goods In/Out"),
    RETURNS                          ("Returns",                          "ProcGRT",                   "Goods In/Out"),
    PURCHASE_INVOICE                 ("Purchase Invoice",                 "ProcPurchaseInv",           "Invoicing"),
    PURCHASE_RETURN                  ("Purchase Return",                  "ProcPurchaseRet",           "Invoicing"),
    DEBIT_NOTE                       ("Debit Note",                       "PurDRNote",                 "Invoicing"),
    CREDIT_NOTE                      ("Credit Note",                      "PurCRNote",                 "Invoicing"),
    SERVICE_ORDER                    ("Service Order",                    "ProcServOrd",               "Service / Expenses"),
    CANCEL_SERVICE_ORDER             ("Cancel Service Order",             "ProcSrvOrdCnl",             "Service / Expenses"),
    SERVICE_INVOICE_ADHOC            ("Service Invoice Adhoc",            "ServInvAdhoc",              "Service / Expenses"),
    SERVICE_INVOICE_AGAINST_DOCUMENT ("Service Invoice Against Document", "ServInvDoc",                "Service / Expenses"),
    SERVICE_DEBIT_NOTE               ("Service Debit Note",               "ServDRNote",                "Service / Expenses"),
    DN_CN_RECONCILIATION             ("DN/CN Reconciliation",             "VendDNCNRecon",             "Others");

    private final String name;
    private final String shortCode;
    private final String group;

    OperationsMenuType(String name, String shortCode, String group) {
        this.name      = name;
        this.shortCode = shortCode;
        this.group     = group;
    }

    @Override public String getName()      { return name; }
    @Override public String getShortCode() { return shortCode; }
    @Override public String getGroup()     { return group; }
}
