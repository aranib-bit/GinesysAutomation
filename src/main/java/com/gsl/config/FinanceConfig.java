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

    /**
     * Temporary escape hatch for a known, already-logged UI defect: a Numeric UDF field
     * (General Voucher's {@code finvchmain.udfnum01}) displays/saves {@code 0} instead of blank
     * when no value is present. {@code false} by default so the Edit snapshot validator stays
     * strict everywhere except an environment where a tester has deliberately flipped this on
     * (in their own local {@code finance.properties}, not the checked-in default) to work around
     * the known bug — remove this flag entirely once the UI defect is fixed, it is not meant to
     * be a permanent fixture.
     */
    @Key("finance.gv.suppress.udfnum01.zero.bug")
    @DefaultValue("false")
    boolean suppressUdfnum01ZeroBug();
}
