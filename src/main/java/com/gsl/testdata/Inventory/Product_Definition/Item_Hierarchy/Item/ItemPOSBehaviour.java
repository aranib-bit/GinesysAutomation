package com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item;

import com.gsl.converter.*;
import com.gsl.enums.*;
import com.gsl.testdata.annotations.Mandatory;
import com.gsl.testdata.annotations.MandatoryType;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;
import lombok.Setter;

/**
 * Fixture-data counterpart of the "POS Behaviour" sheet of the Inventory Item workbook ({@code
 * ConfigFactory.getExcelDataConfig().inventoryitemPath()}) — how the item behaves at point of
 * sale: multi-price action, price-change allowance/limit, batch-selection process, auto-quantity
 * popup, extra tax, and return behavior. Optional sheet: {@code MergedInvItemDataSupplier} joins
 * a row in only when one exists for the {@code TestCaseID} (unlike the mandatory {@link
 * ItemGeneral}). Feeds {@code MergedInventoryItemData.itemPOSBehaviour}.
 */
@Getter
@Setter
@Sheet(name = "POS Behaviour")
public class ItemPOSBehaviour {

    @Column(name = "TestCaseID")
    public String testCaseId;

    @Mandatory(type = MandatoryType.UI_DEFAULT)
    @Column(name = "Multi Price Action",converter = MultiPriceActionConverter.class)
    public MultiPriceActionType multiPriceAction;

    @Column(name = "Allow Price Change",converter = AllowPriceChangeConverter.class)
    public AllowPriceChangeType allowPriceChange;

    @Column(name = "Price Changes Limit")
    public String priceChangesLimit;

    @Column(name = "Batch Selection Process")
    public String batchSelectionProcess;

    @Column(name = "Auto Qty. Popup",converter = AutoQtyPopupConverter.class)
    public AutoQtyPopupType autoQtyPopup;

    @Column(name = "Charge Extra Tax",converter = ChargeExtraTaxConverter.class)
    public ChargeExtraTaxType chargeExtraTax;

    @Column(name = "Return Behavior",converter =  ReturnBehaviourConverter.class)
    public ReturnBehaviourType returnBehavior;
}
