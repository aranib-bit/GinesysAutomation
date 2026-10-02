package com.gsl.config;

import com.gsl.config.converter.EnvExcelPathConverter;
import org.aeonbits.owner.Config;

/**
 * File-path lookup for the Excel workbooks that back EXCEL-mode test data (see
 * {@link FrameworkConfig#testDataSource()}), read from {@code exceldata.properties}. Each
 * accessor resolves the path to one workbook, consumed by the corresponding
 * {@code DataProvider}/DataSource implementation via POI/ZeroCell.
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "file:${user.dir}/src/test/resources/env/${env}/exceldata.properties",
        "file:${user.dir}/src/test/resources/config/exceldata.properties"
})
public interface ExcelDataConfig extends Config {
    // Every accessor returns the ACTIVE environment's copy (env/<env>/data/<path>) — see EnvExcelPathConverter.
    /** Path to the receipts workbook. */
    @ConverterClass(EnvExcelPathConverter.class)
    @Key("receipts.path")
    String receiptsPath();
    /** Path to the master test-case workbook. */
    @ConverterClass(EnvExcelPathConverter.class)
    @Key("mastertestcase.path")
    String MasterTestData();
    /** Path to the inventory-group workbook. */
    @ConverterClass(EnvExcelPathConverter.class)
    @Key("inventorygroup.path")
    String inventorygroupPath();
    /** Path to the inventory-item workbook. */
    @ConverterClass(EnvExcelPathConverter.class)
    @Key("inventoryitem.path")
    String  inventoryitemPath();
    /** Path to the AR (Accounts Receivable) voucher workbook. */
    @ConverterClass(EnvExcelPathConverter.class)
    @Key("arVoucherPath")
    String arVoucherPath();

    /** Path to the AP (Accounts Payable) voucher workbook. */
    @ConverterClass(EnvExcelPathConverter.class)
    @Key("apVoucherPath")
    String apVoucherPath();

    /** Path to the General Voucher workbook. */
    @ConverterClass(EnvExcelPathConverter.class)
    @Key("generalVoucherPath")
    String generalVoucherPath();

    /** Path to the Finance account-group workbook. */
    @ConverterClass(EnvExcelPathConverter.class)
    @Key("finAccGroupPath")
    String finAccGroupPath();

    /** Path to the Finance account-group lifecycle workbook. */
    @ConverterClass(EnvExcelPathConverter.class)
    @Key("finAccGroupLifecyclePath")
    String finAccGroupLifecyclePath();

    /** Path to the Finance account-class lifecycle workbook. */
    @ConverterClass(EnvExcelPathConverter.class)
    @Key("finAccountClassLifecyclePath")
    String finAccountClassLifecyclePath();

    /** Path to the UI style baseline workbook (sheets Common + one per voucher: expected font/colour
     *  values for UI-contract checks) — see {@code UiStyleBaseline}. Edit the workbook, no code change. */
    @ConverterClass(EnvExcelPathConverter.class)
    @Key("uiStyleBaselinePath")
    @DefaultValue("Finance/UiStyleBaseline.xlsx")
    String uiStyleBaselinePath();

    /** Path to the users-list workbook used to seed/validate test user credentials. */
    @ConverterClass(EnvExcelPathConverter.class)
    @Key("userslist.path")
    @DefaultValue("UsersList.xlsx")
    String usersListPath();
}
