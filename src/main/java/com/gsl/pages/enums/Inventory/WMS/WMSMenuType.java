package com.gsl.pages.enums.Inventory.WMS;

import com.gsl.enums.ISubModule;

/**
 * Inventory &gt; WMS' side-menu sub-items (grouped under Transactions/Setup headers) — consumed
 * by {@code WMSSideMenuComponent.clickMenuItem(WMSMenuType)} to expand the right group and click
 * the matching item. Also registered in {@code SubModuleResolver}'s {@code INVENTORY} entry so a
 * test's declared {@code fw.subModule} short code (e.g. {@code "binputaway"}) resolves back to
 * one of these constants.
 */
public enum WMSMenuType implements ISubModule {

    PUT_AWAY          ("Put Away",          "binputaway",      "Transactions"),
    TAKE_AWAY         ("Take Away",         "bintakeaway",     "Transactions"),
    BIN_ADJUSTMENT    ("Bin Adjustment",    "binadjustment",   "Transactions"),
    STOCK_RESERVATION ("Stock Reservation", "binreservation",  "Transactions"),
    PICK_LIST         ("Pick List",         "picklist",        "Transactions"),
    CONFIRM_PICK_LIST ("Confirm Pick List", "confirmpicklist", "Transactions"),
    BIN_COUNT         ("Bin Count",         "WMSbincount",     "Transactions"),
    BIN_GROUP_MASTER  ("Bin Group Master",  "BinGroupMaster",  "Setup"),
    BIN_ASSORTMENT    ("Bin Assortment",    "BinAssortment",   "Setup"),
    BIN_MASTER        ("Bin Master",        "BinMaster",       "Setup");

    private final String name;
    private final String shortCode;
    private final String group;

    WMSMenuType(String name, String shortCode, String group) {
        this.name      = name;
        this.shortCode = shortCode;
        this.group     = group;
    }

    @Override public String getName()      { return name; }
    @Override public String getShortCode() { return shortCode; }
    @Override public String getGroup()     { return group; }
}
