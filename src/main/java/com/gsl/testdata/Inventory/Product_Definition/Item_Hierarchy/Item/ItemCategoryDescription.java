package com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Item;

import com.gsl.testdata.annotations.AtLeastOneMandatory;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

/**
 * Fixture-data counterpart of the "Category&amp;Description" sheet of the Inventory Item workbook
 * ({@code ConfigFactory.getExcelDataConfig().inventoryitemPath()}) — up to 6 category/description
 * pairs for one item. Optional sheet: {@code MergedInvItemDataSupplier} joins a row in only when
 * one exists for the {@code TestCaseID} (unlike the mandatory {@link ItemGeneral}), and skips
 * {@link com.gsl.validation.MandatoryFieldValidator#validate} entirely when it's absent. When present, {@link
 * com.gsl.testdata.annotations.AtLeastOneMandatory} requires at least one of the six category
 * columns to be filled. Feeds {@code MergedInventoryItemData.itemCategoryDescription}.
 */
@Getter
@AtLeastOneMandatory(
        fields = {
                "category1",
                "category2",
                "category3",
                "category4",
                "category5",
                "category6"
        },
        message = "At least one Category (1–6) must be provided"
)
@Sheet(name= "Category&Description")
public class ItemCategoryDescription {
    @Column(name = "TestCaseID")
    public String testCaseId;
    @Column(name="Category 1")
    public String category1;
    @Column(name ="Category 2")
    public String category2;
    @Column(name="Category 3")
    public String category3;
    @Column(name = "Category 4")
    public String category4;
    @Column(name="Category 5")
    public String category5;
    @Column(name = "Category 6")
    public String category6;
    @Column(name = "Description 1")
    public String description1;
    @Column(name = "Description 2")
    public String description2;
    @Column(name = "Description 3")
    public String description3;
    @Column(name = "Description 4")
    public String description4;
    @Column(name = "Description 5")
    public String description5;
    @Column(name = "Description 6")
    public String description6;
}