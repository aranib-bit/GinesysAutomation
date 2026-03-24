package com.gsl.pages.Inventory.Product_Definition;

import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.SeleniumActions;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class GroupListPage {
    private static final By GROUPROOT = By.xpath("//span[text()='Root']");
    private static final By ADDDIVISION = By.xpath("//span[text()='Add New Division']");
    private static final By ADDSECTION=By.xpath("//span[text()='Add New Section']");
    private static final String DIVISIONSELECTION_XPATH="//span[text()='%s']";
    private static final String DIVISIONSECTIONEXPANDER_XPATH="//span[text()='%s']/preceding-sibling::div[contains(@class,' x-tree-elbow-img x-tree-elbow-plus x-tree-expander')]";
    private static final By ADDDEPARTMENT=By.xpath("//span[text()='Add New Department']");

    public GroupListPage clickRoot() {
        SeleniumUtils.click(GROUPROOT, WaitType.CLICKABLE, "GROUPROOT");
        return this;
    }

    public GroupListPage clickaddDivision() {
        SeleniumActions.rightClick(GROUPROOT);
        //ActionUtils.rightClick(GROUPROOT);
        SeleniumUtils.click(ADDDIVISION, WaitType.CLICKABLE, "ADDDIVISION");
        return this;
    }

    private By getDivisionGroupSelection(String divisionName) {
        String xpath = String.format(DIVISIONSELECTION_XPATH, divisionName);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }
    private By getDivisionSectionExpander(String divisionsectionName) {
        String xpath = String.format(DIVISIONSECTIONEXPANDER_XPATH, divisionsectionName);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }

    public GroupListPage selectDivision(String divisionName){
        SeleniumUtils.click(getDivisionGroupSelection(divisionName), WaitType.CLICKABLE, "DIVISIONSELECTION");
        return this;
    }

    public GroupListPage clickaddSection(String divisionName) {
        SeleniumActions.rightClick(getDivisionGroupSelection(divisionName));
        SeleniumUtils.click(ADDSECTION, WaitType.CLICKABLE, "ADDSECTION");
        return this;
    }

    public GroupListPage clickdivisionsectionexpander(String divsionsectionName){
        SeleniumUtils.click(getDivisionSectionExpander(divsionsectionName),WaitType.CLICKABLE,"DIVISIONSECTIONEXPANDER");
        return this;
    }

    public GroupListPage clickaddDepartment(String sectionName) {
        SeleniumActions.rightClick(getDivisionGroupSelection(sectionName));
        SeleniumUtils.click(ADDDEPARTMENT, WaitType.CLICKABLE, "ADDDEPARTMENT");
        return this;
    }

}
