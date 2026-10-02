package com.gsl.testdata.Procurement;

/**
 * {@link ItemDetail} variant for a "Regular" line-item row (plain item code/description/quantity/
 * UOM, no batch/serial tracking) — built by {@link ItemDetailFactory#create} from an {@code
 * "ItemType"="Regular"} row. See {@link ItemDetail}'s Javadoc for this whole chain's
 * orphaned/unconfirmed-live-caller status.
 */
public class RegularItemDetail implements ItemDetail {
    private String testCaseId;
    private String itemCode;
    private String description;
    private Double quantity;
    private String uom;
    private String remarks;
    private String itemSelectionMajorMethod;
    private String itemSetSelectionSelectionWays;

    /** Always {@code "Regular"} — this variant's {@link ItemDetail#getItemType()} discriminator. */
    @Override
    public String getItemType() {
        return "Regular";
    }

    @Override
    public String getTestCaseId() {
        return testCaseId;
    }

    @Override
    public String getItemSelectionMajorMethod() {
        return itemSelectionMajorMethod;
    }

    @Override
    public String getItemSetSelectionSelectionWays() {
        return itemSetSelectionSelectionWays;
    }

    public void setTestCaseId(String testCaseId) {
        this.testCaseId = testCaseId;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public String getUom() {
        return uom;
    }

    public void setUom(String uom) {
        this.uom = uom;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public void setItemSelectionMajorMethod(String itemSelectionMajorMethod) {
        this.itemSelectionMajorMethod = itemSelectionMajorMethod;
    }

    public void setItemSetSelectionSelectionWays(String itemSetSelectionSelectionWays) {
        this.itemSetSelectionSelectionWays = itemSetSelectionSelectionWays;
    }
}
