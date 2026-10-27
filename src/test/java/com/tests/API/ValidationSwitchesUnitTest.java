package com.tests.API;

import com.gsl.validation.ValidationSwitches;
import com.gsl.validation.ValidationSwitches.Mode;
import com.gsl.validation.api.ApiCheckRow;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ValidationSwitchesUnitTest {

    @Test
    public void frameworkNoForcesOff() {
        Assert.assertEquals(ValidationSwitches.mode(false, true), Mode.OFF);
        Assert.assertEquals(ValidationSwitches.mode(false, false), Mode.OFF);
    }

    @Test
    public void frameworkYesReportYesIsFull() {
        Assert.assertEquals(ValidationSwitches.mode(true, true), Mode.FULL);
    }

    @Test
    public void frameworkYesReportNoIsFailuresOnly() {
        Assert.assertEquals(ValidationSwitches.mode(true, false), Mode.FAILURES_ONLY);
    }

    @Test
    public void rowGroupsSplitLineLabels() {
        ApiCheckRow line = ApiCheckRow.of("Line 2 payment mode", "H", "H", true);
        Assert.assertEquals(line.group(), "Line 2");
        Assert.assertEquals(line.check(), "payment mode");
        ApiCheckRow doc = ApiCheckRow.of("Document date", "a", "b", false);
        Assert.assertEquals(doc.group(), "Document");
        Assert.assertEquals(doc.check(), "Document date");
        ApiCheckRow added = ApiCheckRow.of("Added line GL", "x", "x", true);
        Assert.assertEquals(added.group(), "Added line");
        Assert.assertEquals(added.check(), "GL");
    }
}
