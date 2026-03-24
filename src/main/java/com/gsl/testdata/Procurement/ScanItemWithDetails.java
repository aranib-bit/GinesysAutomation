package com.gsl.testdata.Procurement;

import java.util.List;

public class ScanItemWithDetails {
    private ScanItem scanItem;
    private List<ItemDetail> itemDetails;

    public ScanItemWithDetails(ScanItem scanItem, List<ItemDetail> itemDetails) {
        this.scanItem = scanItem;
        this.itemDetails = itemDetails;
    }

    public ScanItem getScanItem() {
        return scanItem;
    }

    public List<ItemDetail> getItemDetails() {
        return itemDetails;
    }
}
