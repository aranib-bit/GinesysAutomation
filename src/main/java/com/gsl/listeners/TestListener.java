package com.gsl.listeners;

import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ExtentReport;
import com.gsl.testdata.Inventory.MergedInventoryItemData;
import com.gsl.testdata.InvocationData;
import com.gsl.testdata.dto.MergedReceiptData;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.util.Arrays;

public class TestListener implements ITestListener {

    @Override
    public void onTestStart(ITestResult result) {

        System.out.println("TestListener onTestStart methodname: "
                + result.getMethod().getMethodName());

        Object[] params = result.getParameters();

        if (params.length > 0 && params[0] instanceof InvocationData data) {

            ExtentReport.createTest(
                    result.getMethod().getDescription() +
                            " [" + data.getInvocationIndex() +
                            "/" + data.getTotalInvocations() + "]"
            );

        }

        //Assign Author
        ExtentReport.assignAuthor(
                result.getMethod()
                        .getConstructorOrMethod()
                        .getMethod()
                        .getAnnotation(FrameworkAnnotation.class)
                        .value()
        );
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        ExtentLogger.pass(result.getName() + " is passed");
    }

    @Override
    public void onTestFailure(ITestResult result) {
        ExtentLogger.fail(result.getName() + " is failed");
        ExtentLogger.fail(result.getMethod().getDescription() + " is failed");
        ExtentLogger.fail("Exception: " + result.getThrowable().getClass().getSimpleName());
        ExtentLogger.fail("Message " + result.getThrowable().getMessage());
        ExtentLogger.fail("StackTrace " + Arrays.toString(result.getThrowable().getStackTrace()));
    }

    @Override
    public void onStart(ITestContext context) {
        ExtentReport.initReports();
    }

    @Override
    public void onFinish(ITestContext context) {
        ExtentReport.flushReports();
    }


}
