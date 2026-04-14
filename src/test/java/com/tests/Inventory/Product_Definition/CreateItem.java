package com.tests.Inventory.Product_Definition;

import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.InvocationEnabled;
import com.gsl.enums.TestAuthor;
import com.gsl.pages.HomePage;
import com.gsl.pages.LoginPage;
import com.gsl.pages.enums.Inventory.MainMenuType;
import com.gsl.pages.enums.Inventory.Product_Definition.Product_DefintionMenuType;
import com.gsl.pages.Inventory.Product_Definition.ItemListPage;
import com.gsl.testdata.Inventory.MergedInventoryItemData;
import com.gsl.testdata.dto.MergedInventoryGroupData;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedInvGroupDataSupplier;
import com.gsl.utils.dataprovider.MergedInvItemDataSupplier;
import com.tests.BaseTest;
import org.testng.annotations.Test;

public class CreateItem extends BaseTest {

    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "To create new item of Inventory Item from UI",
            dataProvider = "dataSupplier", dataProviderClass = MergedInvItemDataSupplier.class)
    public void CreateItem(MergedInventoryItemData ItemData) throws InterruptedException {
        System.out.println("Running test for: " + ItemData.getMasterTestData().getTestcaseid());
        System.out.println(ItemData.getItemGeneral().getTestCaseId());
        System.out.println(ItemData.getItemGeneral().getArticle());
        System.out.println(ItemData.getItemCategoryDescription().getCategory1());
        System.out.println(ItemData.getItemOthers().getUnitOfMeasurement());
        System.out.println(ItemData.getItemPricing().getRetailSalePrice());
        System.out.println(ItemData.getItemOthers().getNumber1());
        System.out.println(ItemData.getItemGeneral().getHsnsacid());
        System.out.println(FakerUtils.randomInt());
        System.out.println(FakerUtils.randomDouble());

        System.out.println("Start Test");

        // 1. Login
        LoginPage loginPage = new LoginPage();
        HomePage homePage = loginPage.loginToApplication(
                ItemData.getMasterTestData().getUsername(),
                ItemData.getMasterTestData().getPassword()
        );





        // 2. Navigate to Item List
        ItemListPage itemListPage = NavigationUtils.navigateToInventorySideMenu(
                homePage,
                MainMenuType.PRODUCT_DEFINITION,
                Product_DefintionMenuType.ITEM,
                ItemListPage.class
        );

        itemListPage.clickAdd().
                clickGenerateGs1Code(ItemData.getItemGeneral().getGenerateGS1Code()).
                setArticle(ItemData.getItemGeneral().getArticle()).
                sethsnsacid(ItemData.getItemGeneral().getHsnsacid()).
                setitceligibity((ItemData.getItemGeneral().getItcEligibility().toString())).
                setCategory1(ItemData.getItemCategoryDescription().getCategory1()).
                setCategory2(ItemData.getItemCategoryDescription().getCategory2()).
                setCategory3(ItemData.getItemCategoryDescription().getCategory3()).
                setCategory4(ItemData.getItemCategoryDescription().getCategory4()).
                setCategory5(ItemData.getItemCategoryDescription().getCategory5()).
                setCategory6(ItemData.getItemCategoryDescription().getCategory6()).
                setDescription1(ItemData.getItemCategoryDescription().getDescription1()).
                setDescription2(ItemData.getItemCategoryDescription().getDescription2()).
                setDescription3(ItemData.getItemCategoryDescription().getDescription3()).
                setDescription4(ItemData.getItemCategoryDescription().getDescription4()).
                setDescription5(ItemData.getItemCategoryDescription().getDescription5()).
                setDescription6(ItemData.getItemCategoryDescription().getDescription6()).
                setitemname(ItemData.getItemOthers().getItemName()).
                setshortitemname(ItemData.getItemOthers().getShortName()).
                setvendorname(ItemData.getItemOthers().getVendorName()).
                settaxgrpname(ItemData.getItemOthers().getTaxGroup()).
                setmaterialtype(ItemData.getItemOthers().getMaterialType().toString()).
                setcostsheetname(ItemData.getItemOthers().getCostSheet()).
                setgeneralledgername(ItemData.getItemOthers().getGeneralLedger()).
                setsubledgername(ItemData.getItemOthers().getSubLedger()).
                setUOM(ItemData.getItemOthers().getUnitOfMeasurement()).
                setnegativestockmethod(ItemData.getItemOthers().getNegativeStockMethod().toString()).
                setinventoryitem(ItemData.getItemOthers().getInventoryItem().toString()).
                setscanunit(ItemData.getItemOthers().getScanUnit()).
                setnumber1(ItemData.getItemOthers().getNumber1()).
                setnumber2(ItemData.getItemOthers().getNumber2()).
                setnumber3(ItemData.getItemOthers().getNumber3()).
                setremarks(ItemData.getItemOthers().getRemarks()).
                setstandardrate(ItemData.getItemPricing().getStandardRate()).
                setWSP(ItemData.getItemPricing().getWholeSalePrice()).
                setRSP(ItemData.getItemPricing().getRetailSalePrice()).
                setMRP(ItemData.getItemPricing().getMaximumRetailPrice()).
                setinventorymanagement(ItemData.getItemInventoryTracking().getInventoryManagement().toString()).
                setpricingmanagement(ItemData.getItemInventoryTracking().getPricingManagement().toString()).
                setexpirymanagement(ItemData.getItemInventoryTracking().getExpiryManagement().toString()).
                setvaliditymode(ItemData.getItemInventoryTracking().getValidityMode().toString()).
                setvalidityperiod(ItemData.getItemInventoryTracking().getValidityPeriod()).
                setmultipriceaction(ItemData.getItemPOSBehaviour().getMultiPriceAction().toString()).
                setallowpricechange(ItemData.getItemPOSBehaviour().getAllowPriceChange().toString()).
                setpricechangeslimit(ItemData.getItemPOSBehaviour().getPriceChangesLimit()).
                setautoqtypopup(ItemData.getItemPOSBehaviour().getAutoQtyPopup().toString()).
                setchargeextratax(ItemData.getItemPOSBehaviour().getChargeExtraTax().toString()).
                setreturnbehavior(ItemData.getItemPOSBehaviour().getReturnBehavior().toString()).
                setstoreinformation(ItemData.getStoreWiseMultiplePriceList()).
                clickCreateItem().clickClose().closeItem();

        HomePage homePage1=new HomePage();
        homePage1.logoutFromApplication();
    }
}
