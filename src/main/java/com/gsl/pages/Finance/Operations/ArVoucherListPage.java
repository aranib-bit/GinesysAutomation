package com.gsl.pages.Finance.Operations;

import com.google.common.util.concurrent.Uninterruptibles;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

import java.util.concurrent.TimeUnit;

public class ArVoucherListPage {
    private static final By ARVOUCHERADD = By.xpath("//span[text()='Add']");

    public ArVoucherAdd clickAdd(){
        Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);
        System.out.println("Inside clickAdd");
        SeleniumUtils.click(ARVOUCHERADD, WaitType.CLICKABLE,"ARVOUCHERADD");
        return new ArVoucherAdd();
    }
}
