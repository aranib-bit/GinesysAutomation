package com.gsl.config;

import org.aeonbits.owner.Config;

@Config.Sources({
        "file:${user.dir}/src/test/resources/config/exceldata.properties"
})
public interface ExcelDataConfig extends Config {
    @Key("receipts.path")
    String receiptsPath();
    @Key("mastertestcase.path")
    String MasterTestData();
    @Key("inventorygroup.path")
    String inventorygroupPath();
    @Key("inventoryitem.path")
    String  inventoryitemPath();
    @Key("arVoucherPath")
    String arVoucherPath();

    @Key("apVoucherPath")
    String apVoucherPath();

    @Key("generalVoucherPath")
    String generalVoucherPath();

    @Key("finAccGroupPath")
    String finAccGroupPath();

    @Key("finAccGroupLifecyclePath")
    String finAccGroupLifecyclePath();

    @Key("finAccountClassLifecyclePath")
    String finAccountClassLifecyclePath();

    @Key("userslist.path")
    @DefaultValue("UsersList.xlsx")
    String usersListPath();
}
