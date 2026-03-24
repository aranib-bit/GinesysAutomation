package com.gsl.pages.pagecomponent.Inventory.Product_Definition;

import com.google.common.util.concurrent.Uninterruptibles;
import com.gsl.enums.WaitType;
import com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item.ItemStoreWiseMultiplePrice;
import com.gsl.utils.Selenium.SeleniumUtils;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.gsl.utils.Selenium.ExtJsUtils.addPriceInfo;
import static com.gsl.utils.Selenium.ExtJsUtils.setExtJsComboByName;

@Slf4j
public class ItemAddStoreWiseMultiPrice{

private static final By STOREINFORMATIONADDROW =
        By.xpath("(//div[normalize-space(.)='Price Information']" +
                "/parent::*/parent::*/parent::*/parent::*/parent::*/parent::*" +
                "//a[contains(@class,'x-btn')" +
                " and contains(@class,'x-toolbar-item')" +
                " and @aria-disabled='false'" +
                " and .//span[normalize-space(.)='Add Row']])[1]");

private static final By PRICEINFORMATIONADDROW =
        By.xpath("//*[contains(@class,'x-title-text x-title-text-panelHeading x-title-item')" +
                " and normalize-space(.)='Price Information']" +
                "/ancestor::div[contains(@class,'x-panel-header')]" +
                "/following-sibling::div[contains(@class,'x-panel-body')]" +
                "//div[contains(@class,'x-toolbar')]" +
                "//span[normalize-space(.)='Add Row']/ancestor::a");

    private static final String STORE_NAME_TEMPLATE =
            "(//div[contains(@class,'x-window')]//table[contains(@class,'x-grid-item')])[last()]//tr[last()]//td[1]";


    private static final By STOREINFORMATIONNAME=
        By.xpath("(//div[contains(@class,'x-window')]  //a[contains(@class,'x-btn') and .//span[normalize-space()='Add Row']]  /parent::*  /parent::*  /parent::*  /parent::*  /parent::*  /parent::*  //table[contains(@class,'x-grid-item')] )[2]//td[1]");




    public ItemAddStoreWiseMultiPrice setstoreinformation(
            List<ItemStoreWiseMultiplePrice> storeWiseList) {

        if (storeWiseList == null || storeWiseList.isEmpty()) {
            System.out.println("No store-wise price data provided");
            return this;
        }

        Map<String, List<ItemStoreWiseMultiplePrice>> storeGroupedMap =
                storeWiseList.stream()
                        .collect(Collectors.groupingBy(ItemStoreWiseMultiplePrice::getStorename));

        for (Map.Entry<String, List<ItemStoreWiseMultiplePrice>> entry : storeGroupedMap.entrySet()) {

            String storeName = entry.getKey();
            List<ItemStoreWiseMultiplePrice> storeRows = entry.getValue();
            System.out.println("Processing Store: {}"+storeName);

            // ===============================
            // ADD STORE ROW
            // ===============================
            SeleniumUtils.click(
                    STOREINFORMATIONADDROW,
                    WaitType.CLICKABLE,
                    "STOREINFORMATIONADDROW"
            );
            Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);

            // 🔑 USE STRING TEMPLATE HERE
            SeleniumUtils.click(
                    By.xpath(STORE_NAME_TEMPLATE),
                    WaitType.CLICKABLE,
                    "STORE NAME CELL"
            );
            Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);


            setExtJsComboByName("siteCode", storeName);

            // ===============================
            // ADD PRICE ROWS
            // ===============================
            for (ItemStoreWiseMultiplePrice data : storeRows) {

                addPriceInfo(
                        data.getEffectiveDate(),
                        data.getMrp(),
                        data.getRsp()
                );

            }
        }

        System.out.println("All Store-wise & Price-wise data added successfully");
        return this;
    }

    public static Double tryParse(String str, Double defaultVal) {
        try {
            return str == null || str.isEmpty() ? defaultVal : Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }


}