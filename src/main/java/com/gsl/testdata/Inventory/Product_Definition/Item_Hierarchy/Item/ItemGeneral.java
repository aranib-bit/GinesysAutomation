package com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item;

import com.gsl.converter.ItcEligibilityConverter;
import com.gsl.converter.SafeIntegerConverter;
import com.gsl.enums.ItcEligibilityType;
import com.gsl.testdata.annotations.Mandatory;
import com.gsl.testdata.annotations.MandatoryType;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

/**
 * Fixture-data counterpart of the "General" sheet of the Inventory Item workbook ({@code
 * ConfigFactory.getExcelDataConfig().inventoryitemPath()}) — the item's core identity (article,
 * HSN/SAC, ITC eligibility). The only one of the seven Item-hierarchy sheets that is mandatory:
 * {@code MergedInvItemDataSupplier} throws if a {@code TestCaseID} has no row here, while the
 * other six ({@link ItemCategoryDescription}, {@link ItemOthers}, {@link ItemPricing}, {@link
 * ItemInventoryTracking}, {@link ItemPOSBehaviour}, {@link ItemStoreWiseMultiplePrice}) are
 * optional and joined in only when present. Feeds {@code MergedInventoryItemData.itemGeneral}.
 */
@Getter
@Sheet(name= "General")
public class ItemGeneral {
    @Mandatory
    @Column(name = "TestCaseID")
    public String testCaseId;
    @Column(name="Generate GS1 Code")
    public String generateGS1Code;
    @Column(name ="Division")
    public String division;
    @Column(name="Section")
    public String section;
    @Column(name = "Department")
    public String department;
    @Mandatory
    @Column(name="Article")
    public String article;
    @Mandatory
    @Column(name = "HSN/SAC ID",converter = SafeIntegerConverter.class)
    public Integer hsnsacid;
    @Mandatory(type = MandatoryType.UI_DEFAULT)
    @Column(name = "ITC Eligibility", converter = ItcEligibilityConverter.class)
    public ItcEligibilityType itcEligibility;
}