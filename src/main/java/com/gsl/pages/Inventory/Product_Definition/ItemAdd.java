package com.gsl.pages.Inventory.Product_Definition;

import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.pagecomponent.Inventory.Product_Definition.*;
import com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item.ItemStoreWiseMultiplePrice;

import java.util.List;

public class ItemAdd {

    private ItemAddGeneral itemAddGeneral;
    private ItemAddCategoryDescription itemAddCategoryDescription;
    private ItemAddOthers itemAddOthers;
    private ItemAddPricing itemAddPricing;
    private ItemAddInventoryTracking  itemAddInventoryTracking;
    private ItemAddPOSBehaviour itemAddPOSBehaviour;
    private ItemAddStoreWiseMultiPrice itemAddStoreWiseMultiPrice;
    private ItemAddFooter itemAddFooter;

    public ItemAdd() {
        this.itemAddGeneral = new ItemAddGeneral();
        this.itemAddCategoryDescription=new ItemAddCategoryDescription();
        this.itemAddOthers=new ItemAddOthers();
        this.itemAddPricing=new ItemAddPricing();
        this.itemAddInventoryTracking=new ItemAddInventoryTracking();
        this.itemAddPOSBehaviour=new ItemAddPOSBehaviour();
        this.itemAddStoreWiseMultiPrice=new ItemAddStoreWiseMultiPrice();
        this.itemAddFooter=new ItemAddFooter();
    }


    public ItemAdd clickGenerateGs1Code(String value){
        if (value.equalsIgnoreCase("yes")) {
            itemAddGeneral.clickGenerateGS1Code();
        } else if (value.equalsIgnoreCase("no")) {
            System.out.println("Generate GS1 Code value is no");
        } else {
            System.out.println("Please enter yes or no in Generate GS1 Code");
        }
        return this;
    }

    public ItemAdd setArticle(String article) {
        //article="";
        itemAddGeneral.setArticle(article);
        return this;
    }

    public ItemAdd sethsnsacid(Integer hsnsacid) {
        itemAddGeneral.setHSNSACID(hsnsacid);
        return this;
    }

    public ItemAdd setitceligibity(String  itceligibity) {
        itemAddGeneral.setitceligibity(itceligibity);
        return this;
    }

    public ItemAdd setCategory1(String category)  {
        itemAddCategoryDescription.setCategory1(category);
        return this;
    }

    public ItemAdd setCategory2(String category)  {
        itemAddCategoryDescription.setCategory2(category);
        return this;
    }

    public ItemAdd setCategory3(String category)  {
        itemAddCategoryDescription.setCategory3(category);
        return this;
    }

    public ItemAdd setCategory4(String category)  {
        itemAddCategoryDescription.setCategory4(category);
        return this;
    }

    public ItemAdd setCategory5(String category)  {
        itemAddCategoryDescription.setCategory5(category);
        return this;
    }

    public ItemAdd setCategory6(String category)  {
        itemAddCategoryDescription.setCategory6(category);
        return this;
    }

    public ItemAdd setDescription1(String description)  {
        itemAddCategoryDescription.setDescription1(description);
        return this;
    }

    public ItemAdd setDescription2(String description)  {
        itemAddCategoryDescription.setDescription2(description);
        return this;
    }

    public ItemAdd setDescription3(String description)  {
        itemAddCategoryDescription.setDescription3(description);
        return this;
    }

    public ItemAdd setDescription4(String description)  {
        itemAddCategoryDescription.setDescription4(description);
        return this;
    }

    public ItemAdd setDescription5(String description)  {
        itemAddCategoryDescription.setDescription5(description);
        return this;
    }

    public ItemAdd setDescription6(String description)  {
        itemAddCategoryDescription.setDescription6(description);
        return this;
    }

    public ItemAdd setUOM(String uom){
        itemAddOthers.setUOM(uom);
        return this;
    }

    public ItemAdd setnegativestockmethod(String negativestockmethod){
        itemAddOthers.setnegativestockmethod(negativestockmethod);
        return this;
    }



    public ItemAdd setinventoryitem(String inventoryitem){
        itemAddOthers.setinventoryitem(inventoryitem);
        return this;
    }


    public ItemAdd setitemname(String itemname){
        itemAddOthers.setItemName(itemname);
        return this;
    }

    public ItemAdd setshortitemname(String shortitemname){
        itemAddOthers.setshortitemname(shortitemname);
        return this;
    }

    public ItemAdd setvendorname(String vendorname){
        itemAddOthers.setvendorname(vendorname);
        return this;
    }

    public ItemAdd settaxgrpname(String taxgrpname){
        itemAddOthers.settaxgroup(taxgrpname);
        return this;
    }

    public ItemAdd setmaterialtype(String materialtype){
        itemAddOthers.setmaterialtype(materialtype);
        return this;
    }

    public ItemAdd setcostsheetname(String costsheetname){
        itemAddOthers.setcostsheetname(costsheetname);
        return this;
    }

    public ItemAdd setgeneralledgername(String generalledgername){
        itemAddOthers.setgeneralledgername(generalledgername);
        return this;
    }

    public ItemAdd setsubledgername(String subledgername) throws InterruptedException {
        itemAddOthers.setsubledgername(subledgername);
        return this;
    }

    public ItemAdd setscanunit(String scanunit){
        itemAddOthers.setscanunit(scanunit);
        return this;
    }

    public ItemAdd setnumber1(String number1){
        itemAddOthers.setnumber1(number1);
        return this;
    }

    public ItemAdd setnumber2(String number2){
        itemAddOthers.setnumber2(number2);
        return this;
    }

    public ItemAdd setnumber3(String number3){
        itemAddOthers.setnumber3(number3);
        return this;
    }

    public ItemAdd setremarks(String remarks){
        itemAddOthers.setremarks(remarks);
        return this;
    }

    public ItemAdd setstandardrate(String standardrate){
        itemAddPricing.setstandardrate(standardrate);
        return this;
    }

    public ItemAdd setWSP(String wsp){
        itemAddPricing.setWSP(wsp);
        return this;
    }

    public ItemAdd setRSP(String rsp){
        itemAddPricing.setRSP(rsp);
        return this;
    }

    public ItemAdd setMRP(String mrp){
        itemAddPricing.setMRP(mrp);
        return this;
    }

    public ItemAdd setinventorymanagement(String inventorymanagement){
        itemAddInventoryTracking.setinventorymanagement(inventorymanagement);
        return this;
    }

    public ItemAdd setpricingmanagement(String pricingmanagement){
        itemAddInventoryTracking.setpricingmanagement(pricingmanagement);
        return this;
    }

    public ItemAdd setexpirymanagement(String expirymanagement){
        itemAddInventoryTracking.setexpirymanagement(expirymanagement);
        return this;
    }

    public ItemAdd setvaliditymode(String validitymode){
        itemAddInventoryTracking.setvaliditymode(validitymode);
        return this;
    }

    public ItemAdd setvalidityperiod(String validityperiod){
        itemAddInventoryTracking.setvalidityperiod(validityperiod);
        return this;
    }

    public ItemAdd setmultipriceaction(String multipriceaction){
        itemAddPOSBehaviour.setmultipriceaction(multipriceaction);
        return this;
    }

    public ItemAdd setallowpricechange(String allowpricechange){
        itemAddPOSBehaviour.setallowpricechange(allowpricechange);
        return this;
    }

    public ItemAdd setpricechangeslimit(String pricechangeslimit){
        itemAddPOSBehaviour.setpricechangeslimit(pricechangeslimit);
        return this;
    }

    public ItemAdd setautoqtypopup(String autoqtypopup){
        itemAddPOSBehaviour.setautoqtypopup(autoqtypopup);
        return this;
    }

    public ItemAdd setchargeextratax(String chargeextratax){
        itemAddPOSBehaviour.setchargeextratax(chargeextratax);
        return this;
    }

    public ItemAdd setreturnbehavior(String returnbehavior){
        itemAddPOSBehaviour.setreturnbehavior(returnbehavior);
        return this;
    }

    public ItemAdd setstoreinformation(List<ItemStoreWiseMultiplePrice> storeWiseMultiplePriceList){
        itemAddStoreWiseMultiPrice.setstoreinformation(storeWiseMultiplePriceList);
        return this;
    }



    public ItemAdd clickCreateItem(){
        itemAddFooter.clickCreateItem();
        return this;
    }

    public DocumentMessageBox clickClose(){
        itemAddFooter.clickClose();
        return new DocumentMessageBox();
    }

}
