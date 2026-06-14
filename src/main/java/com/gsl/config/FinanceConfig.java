package com.gsl.config;

import org.aeonbits.owner.Config;

@Config.Sources("file:${user.dir}/src/test/resources/config/finance.properties")
public interface FinanceConfig extends Config {

    @Key("subledger.selection.mode")
    @DefaultValue("JAVASCRIPT")
    String subLedgerSelectionMode();

    @Key("extjs.timeout")
    @DefaultValue("40")
    long extjsTimeout();

    @Key("extjs.min.timeout")
    @DefaultValue("10")
    long extjsMinTimeout();

    @Key("finance.backdate.max.days")
    @DefaultValue("58")
    int financeBackdateMaxDays();

    @Key("db.creation.tolerance")
    @DefaultValue("60")
    long dbCreationTolerance();

    @Key("cheque.country")
    @DefaultValue("ROTATE")
    String chequeCountry();
}
