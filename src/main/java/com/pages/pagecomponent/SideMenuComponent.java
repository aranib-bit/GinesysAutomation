package com.pages.pagecomponent;

import com.enums.WaitType;
import static com.utils.Selenium.SeleniumUtils.*;
import org.openqa.selenium.By;

public class SideMenuComponent {
  private static final By LNK_ADMIN =By.xpath("//a[span[text()='Admin']]");
  private String sideMenus="//a[span[text()='%replaceable%']]";

  public void clickOnMenu(String menu){
      String replace = sideMenus.replace("replaceable", "Admin");
      click(By.xpath(replace),menu);
  }

public void clickAdmin(){
    click(LNK_ADMIN, WaitType.CLICKABLE,"AdminModule");
}

}

