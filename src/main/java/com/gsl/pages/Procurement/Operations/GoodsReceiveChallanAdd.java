package com.gsl.pages.Procurement.Operations;

import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Procurement.Operations.Common.CopyChallanPage;
import com.gsl.pages.Procurement.Operations.Common.ScanItemPage;
import com.gsl.pages.Procurement.Operations.Common.SelectItemPage;
import com.gsl.pages.Procurement.Operations.Common.SelectOrderItemPage;
import com.gsl.pages.enums.Procurement.Operations.GoodsReceiveChallanItemSelectionType.ItemSelectionType;
import com.gsl.pages.pagecomponent.Procurement.Operations.GoodsReceiveChallan.GoodsReceiveChallanFooter;
import com.gsl.pages.pagecomponent.Procurement.Operations.GoodsReceiveChallan.GoodsReceiveChallanGeneral;
import com.gsl.pages.pagecomponent.Procurement.Operations.GoodsReceiveChallan.GoodsReceiveChallanItemDetails;


public class GoodsReceiveChallanAdd {

    private String vendorType;
    private String OwnerSite;
    private String siteType;

    private GoodsReceiveChallanGeneral goodsReceiveChallanGeneral;
    private GoodsReceiveChallanItemDetails goodsReceiveChallanItemDetails;
    private GoodsReceiveChallanFooter goodsReceiveChallanFooter;

    public GoodsReceiveChallanAdd() {
        this.goodsReceiveChallanGeneral = new GoodsReceiveChallanGeneral();
        this.goodsReceiveChallanItemDetails=new GoodsReceiveChallanItemDetails();
        this.goodsReceiveChallanFooter=new GoodsReceiveChallanFooter();
    }

//    public GoodsReceiveChallanGeneral getGoodsReceiveChallanGeneral() {
//        return goodsReceiveChallanGeneral;
//    }
//
//    public GoodsReceiveChallanItemDetails getGoodsReceiveChallanItemDetails(){
//        return goodsReceiveChallanItemDetails;
//    }

    // Delegator for fluent API
    public GoodsReceiveChallanAdd setDocScheme(String docScheme) {
        goodsReceiveChallanGeneral.setDocScheme(docScheme);
        return this;
    }

    // Delegator for fluent API
    public GoodsReceiveChallanAdd setVendor(String docScheme) {
        goodsReceiveChallanGeneral.setVendor(docScheme);
        return this;
    }

    public GoodsReceiveChallanAdd setStockPoint(String stockPoint) {
        goodsReceiveChallanGeneral.setStockPoint(stockPoint);
        return this;
    }

    public GoodsReceiveChallanAdd setTermName(String termName) {
        goodsReceiveChallanGeneral.setTermName(termName);
        return this;
    }



    public GoodsReceiveChallanAdd getVendorType() {
        vendorType = goodsReceiveChallanGeneral.getVendorType();
        return this;
    }

    public GoodsReceiveChallanAdd getOwnerSite() {
        OwnerSite = goodsReceiveChallanGeneral.getOwnerSite();
        return this;
    }

    public String fetchOwnerSite() {
        return OwnerSite;
    }

    public String fetchVendorType() {
        return vendorType;
    }

    //API for scroll to ItemDetails from General or Logistics section
    public GoodsReceiveChallanAdd scrollToItemDetails(){
        goodsReceiveChallanItemDetails.scrollToItemDetails();
        return this;
    }
    //API to select the Item Populater List
    public GoodsReceiveChallanAdd clickItemPopulationSelector() {
        goodsReceiveChallanItemDetails.clickItemPopulationSelector();
        return this;
    }

    //API to select choose ItemSelector
    public Object clickItemSelector(ItemSelectionType itemSelectionType) {
        return goodsReceiveChallanItemDetails.clickItemSelector(itemSelectionType);
    }

    public ScanItemPage clickItemSelectorForScan() throws InterruptedException {
        return (ScanItemPage) goodsReceiveChallanItemDetails.clickItemSelector(ItemSelectionType.SCAN_ITEM);
    }

    public SelectItemPage clickItemSelectorForSelect() {
        return (SelectItemPage) goodsReceiveChallanItemDetails.clickItemSelector(ItemSelectionType.SELECT_ITEM);
    }

    public SelectOrderItemPage clickItemSelectorForOrderItems() {
        return (SelectOrderItemPage) goodsReceiveChallanItemDetails.clickItemSelector(ItemSelectionType.SELECT_ORDER_ITEMS);
    }

    public CopyChallanPage clickItemSelectorForCopy() {
        return (CopyChallanPage) goodsReceiveChallanItemDetails.clickItemSelector(ItemSelectionType.COPY_CHALLAN);
    }

    // Delegator for fluent API
    public GoodsReceiveChallanAdd clickCalculateCharge() {
        goodsReceiveChallanItemDetails.clickCalculateCharge();
        return this;
    }

    // Delegator for fluent API
    public DocumentMessageBox clickSave() {
        goodsReceiveChallanFooter.clickSave();
        return new DocumentMessageBox();
    }

    // Delegator for fluent API
    public DocumentMessageBox clickOk() {
        goodsReceiveChallanFooter.clickSave();
        return new DocumentMessageBox();
    }


    public GoodsReceiveChallanAdd setOwnerSite(String ownersite) {
        goodsReceiveChallanGeneral.setOwnerSite(ownersite);
        this.siteType = goodsReceiveChallanGeneral.getLastSiteType(); // capture SiteType
        return this;
    }

    public String fetchSiteType() {
        return siteType;
    }

    public GoodsReceiveChallanAdd setTradeGroup(String tradeGroupName) {
        goodsReceiveChallanGeneral.setTradeGroup(tradeGroupName);
        return this;
    }
}
