package com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item;

import com.gsl.converter.*;
import com.gsl.enums.*;
import com.gsl.testdata.annotations.Mandatory;
import com.gsl.testdata.annotations.MandatoryType;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;
import lombok.Setter;

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
