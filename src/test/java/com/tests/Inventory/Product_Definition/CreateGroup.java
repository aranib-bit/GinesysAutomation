package com.tests.Inventory.Product_Definition;

import com.google.common.util.concurrent.Uninterruptibles;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.database.DatabaseManager;
import com.gsl.driver.DriverManager;
import com.gsl.testdata.UserCredential;
import com.gsl.database.dao.inventory.InvGrpDao;
import com.gsl.enums.TestAuthor;
import com.gsl.pages.HomePage;
import com.gsl.pages.LoginPage;
import com.gsl.pages.enums.Inventory.MainMenuType;
import com.gsl.pages.enums.Inventory.Product_Definition.GroupHierarchyType;
import com.gsl.pages.enums.Inventory.Product_Definition.Product_DefintionMenuType;
import com.gsl.pages.Inventory.Product_Definition.GroupListPage;
import com.gsl.testdata.dto.MergedInventoryGroupData;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.dataprovider.MergedInvGroupDataSupplier;
import com.gsl.enums.LogCategory;
import com.gsl.reports.ReportLogger;
import com.tests.WebTest;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class CreateGroup extends WebTest {

    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "To create new division of Inventory Group from UI",
            dataProvider = "dataSupplier", dataProviderClass = MergedInvGroupDataSupplier.class)
    public void CreateDivision(MergedInventoryGroupData groupData) throws InterruptedException {
        System.out.println("Running test for: " + groupData.getTestCaseId());
        ReportLogger.info(LogCategory.VALIDATION,
                "Expected Result: Division node created. DB — invgrp: 1 new row with correct name at level 1. "
                + "UI: node visible in group hierarchy at depth 1.");
        // 1. Login to the application
        UserCredential u = DriverManager.getCurrentUser();
        LoginPage loginPage = new LoginPage();
        HomePage homePage = loginPage.loginToApplication(u.username(), u.password());

        // 2. Use the navigation utility to get to the correct screen
        GroupListPage groupListPage = NavigationUtils.navigateToInventorySideMenu(homePage,
                MainMenuType.PRODUCT_DEFINITION,
                Product_DefintionMenuType.GROUP, GroupListPage.class);

        // 3. Perform the test-specific actions
        // It is highly recommended to move this logic into a dedicated page object (e.g., GroupPage.java).
        groupListPage.clickRoot().clickaddDivision();

        // 4. Fetch tree panel data
        List<String> results = JavascriptExecutorUtils.getTreePanelTexts();
        results.forEach(System.out::println);

        // 5. Determine oldDivision
        long count = JavascriptExecutorUtils.countTreeOccurrences(GroupHierarchyType.DIVISION);
        System.out.println("Divison Count" +count);

        String oldDivision = (count == 1)
                ? GroupHierarchyType.DIVISION.getDisplayName()
                : GroupHierarchyType.DIVISION.getDisplayName() + (count - 1);
        System.out.println("Old Division: " + oldDivision);

        // 6. Get new division name from Excel
        String newDivision = groupData.getGroup().getGroupName();
        System.out.println(newDivision);

        // 7. Handle duplicates
        if (JavascriptExecutorUtils.doesTreeContainText(GroupHierarchyType.DIVISION, newDivision)) {
            newDivision = newDivision + " " + System.currentTimeMillis();
        }

        int level = GroupHierarchyType.DIVISION.getDepth();

        System.out.println("Before Divsion creation");
        var jdbi = DatabaseManager.getJdbi();
        var invGrpDao = jdbi.onDemand(InvGrpDao.class);
        int countBefore = invGrpDao.getGroupCount(newDivision, level);
        System.out.println("Count of divisionName " + newDivision + " is " + countBefore);


        // 8. Update via JS
        JavascriptExecutorUtils.updateTreeNodeText(oldDivision, newDivision);

        System.out.println("wait for root click");

        //Click Root to update Divison name
        groupListPage.clickRoot();

        System.out.println("After update");
        List<String> updated = JavascriptExecutorUtils.getTreePanelTexts();
        updated.forEach(System.out::println);

        // Verify newDivision exists exactly once at Depth 1
        long updatedCount = JavascriptExecutorUtils.countTreeOccurrences(GroupHierarchyType.DIVISION, newDivision);
        if (updatedCount == 1) {
            System.out.println("✅ Division name successfully updated to: " + newDivision);
        } else {
            throw new AssertionError("❌ Expected 1 occurrence of '" + newDivision +
                    "' at Depth 1, but found " + updatedCount);
        }
        //Thread.sleep(2000);
        System.out.println("check complete");
        // ---------- STEP 3: DB Post-Check ----------
        int countAfter = invGrpDao.getGroupCount(newDivision, level);
        System.out.println("📊 Count after save for '" + newDivision + "': " + countAfter);

        com.gsl.driver.SessionManager.closeSessionAndReleaseUser("Create Group complete");


        //DriverManager.getDriver().findElement(By.xpath("//span[text()='Root']")).click();
        //Actions actions = new Actions(DriverManager.getDriver());
        //WebElement targetElement = DriverManager.getDriver().findElement(By.xpath("//span[text()='Root']"));
        //actions.contextClick(targetElement).perform();
        //WebElement addDivisionOption = DriverManager.getDriver().findElement(By.xpath("//span[text()='Add New Division']"));
        //addDivisionOption.click();

//        System.out.println(groupData.getGroup().getGroupName());
//        System.out.println("Before division update using JS");
//
//        List<String> results = JavascriptExecutorUtils.getTreePanelTexts();
//
//        results.forEach(System.out::println);
//
//
////        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
////        System.out.println("Before division update using js");
////
////        String script1 =
////                "var result = [];" +
////                        "Ext.ComponentQuery.query('treepanel').forEach(function(tree, treeIndex) {" +
////                        "  tree.getStore().each(function(node) {" +
////                        "    result.push('Tree ' + treeIndex + ': ' + node.get('text'));" +
////                        "  });" +
////                        "});" +
////                        "return result;";
////
////        List<String> results = (List<String>) js.executeScript(script1);
//
////        for (String entry : results) {
////            System.out.println(entry);
////        }
//
//        // Count how many "New Division" exist
//        long count = results.stream()
//                .filter(line -> line.contains("Tree 1: New Division"))
//                .count();
//
//        String oldDivision;
//        if (count == 1) {
//            oldDivision = "New Division";
//        } else {
//            oldDivision = "New Division" + (count - 1);
//        }
//
//        System.out.println("Selected oldDivision: " + oldDivision);
//
//        // ✅ newDivision comes from Excel instead of hardcoded
//        String newDivision = groupData.getGroup().getGroupName();
//
//        long duplicateCheck = results.stream()
//                .filter(line -> line.equals("Tree 1: " + newDivision))
//                .count();
//
//        if (duplicateCheck >= 1) {
//            System.out.println("Duplicate Division exists in tree");
//            long epochTime = System.currentTimeMillis();
//            newDivision = newDivision + " " + epochTime;
//            System.out.println("Renamed to avoid duplication: " + newDivision);
//        }
//
//        // Escape single quotes
//        oldDivision = oldDivision.replace("'", "\\'");
//        newDivision = newDivision.replace("'", "\\'");
//
//
//
//        //String newDivision = "Updated Division Name";
//        String finalNewDivision = newDivision;
//        long count1 = results.stream()
//                .filter(line -> line.equals("Tree 1: " + finalNewDivision))
//                .count();
//        System.out.println("Division To be updated Count: " + count1);
//        if(count1>=1){
//            System.out.println("Duplicate Division exists");
//            long epochTime = System.currentTimeMillis();
//            newDivision = newDivision + " " + epochTime;
//            System.out.println("-------------------------------------");
//            System.out.println("Division to be created"+newDivision);
//            System.out.println("-------------------------------------");
//        }
//
//        oldDivision = oldDivision.replace("'", "\\'");
//        System.out.println(oldDivision);
//        newDivision = newDivision.replace("'", "\\'");
//        System.out.println(newDivision);
//        String script = String.format(
//                "var tree = Ext.ComponentQuery.query('treepanel')[1];" +
//                        "var store = tree.getStore();" +
//                        "var node = store.findNode('text', '%s');" +
//                        "if (node) { node.set('text', '%s'); node.commit(); }",
//                oldDivision, newDivision
//        );
//        js.executeScript(script);
//        System.out.println("After division update using js");
//        String script2 =
//                "var result = [];" +
//                        "Ext.ComponentQuery.query('treepanel').forEach(function(tree, treeIndex) {" +
//                        "  tree.getStore().each(function(node) {" +
//                        "    result.push('Tree ' + treeIndex + ': ' + node.get('text'));" +
//                        "  });" +
//                        "});" +
//                        "return result;";
//        List<String> results1 = (List<String>) js.executeScript(script2);
//
//        for (String entry : results1) {
//            System.out.println(entry);
//        }
//        DriverManager.getDriver().findElement(By.xpath("//span[text()='Root']")).click();
//        try {
//            String errtext = DriverManager.getDriver().findElement(By.id("messagebox-1001")).getText();
//            System.out.println(errtext);
//        }catch (Exception e){
//            System.out.println("As error did not occur so message box pop up is not seen");
//        }
//    }
    }


    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "To create new section of Inventory Group from UI",
            dataProvider = "dataSupplier", dataProviderClass = MergedInvGroupDataSupplier.class)
    public void CreateSection(MergedInventoryGroupData groupData) {
        System.out.println("Running test for: " + groupData.getTestCaseId());
        ReportLogger.info(LogCategory.VALIDATION,
                "Expected Result: Section node created. DB — invgrp: 1 new row with correct name at level 2. "
                + "UI: node visible in group hierarchy at depth 2.");

        // 1. Login
        UserCredential u = DriverManager.getCurrentUser();
        LoginPage loginPage = new LoginPage();
        HomePage homePage = loginPage.loginToApplication(u.username(), u.password());

        // 2. Navigate to Group List
        GroupListPage groupListPage = NavigationUtils.navigateToInventorySideMenu(
                homePage,
                MainMenuType.PRODUCT_DEFINITION,
                Product_DefintionMenuType.GROUP,
                GroupListPage.class
        );

        // 3. Expand the root node and the parent division if needed
        groupListPage.clickRoot().selectDivision(groupData.getGroup().getParentGroup()).clickaddSection(groupData.getGroup().getParentGroup());


        // 4. Fetch tree panel data
        List<String> results = JavascriptExecutorUtils.getTreePanelTexts();
        results.forEach(System.out::println);

        // 5. Determine oldDivision
        long count = JavascriptExecutorUtils.countTreeOccurrences(GroupHierarchyType.SECTION);
        System.out.println("Section Count" +count);

        String oldSection = (count == 1)
                ? GroupHierarchyType.SECTION.getDisplayName()
                : GroupHierarchyType.SECTION.getDisplayName() + (count - 1);
        System.out.println("Old Section: " + oldSection);

        // 6. Get new division name from Excel
        String newSection = groupData.getGroup().getGroupName();
        System.out.println(newSection);

        // 7. Handle duplicates
        if (JavascriptExecutorUtils.doesTreeContainText(GroupHierarchyType.SECTION, newSection)) {
            newSection = newSection + " " + System.currentTimeMillis();
        }

        String oldValue = JavascriptExecutorUtils.updateChildText(groupData.getGroup().getParentGroup(), oldSection, GroupHierarchyType.SECTION.getDepth(), newSection);
        System.out.println("Old child text was: " + oldValue);

        //Click Root to update Section name
        groupListPage.clickRoot();

        System.out.println("After update");
        List<String> updated = JavascriptExecutorUtils.getTreePanelTexts();
        updated.forEach(System.out::println);

        // Verify newDivision exists exactly once at Depth 1
        long updatedCount = JavascriptExecutorUtils.countTreeOccurrences(GroupHierarchyType.SECTION, newSection);
        if (updatedCount == 1) {
            System.out.println("✅ Section name successfully updated to: " + newSection);
        } else {
            throw new AssertionError("❌ Expected 1 occurrence of '" + newSection +
                    "' at Depth 2, but found " + updatedCount);
        }

        com.gsl.driver.SessionManager.closeSessionAndReleaseUser("Create Group complete");


        //JavascriptExecutorUtils.updateFirstChildText(groupData.getGroup().getParentGroup(),2,groupData.getGroup().getGroupName());
        //JavascriptExecutorUtils.expandAndReloadNode("New Section");

        // 4. Update node text AND grpName
//        boolean updated = JavascriptExecutorUtils.updateTreeNodeGroupName("New Section", "ABRADABRA");
//        Assert.assertTrue(updated, "Section name update failed!");
//
//
//        // 5. Refresh tree view to reflect changes
//        groupListPage.clickRoot();
//        JavascriptExecutorUtils.refreshAndExpandTreeNode("ABRADABRA");
//
//        // 6. Verify that 'SHIRT' exists exactly once
//        long updatedCount = JavascriptExecutorUtils.countTreeOccurrences(GroupHierarchyType.SECTION, "SHIRT");
//        Assert.assertEquals(updatedCount, 1, "❌ Section name not updated to 'ABRADABRA'!");
//
//        System.out.println("✅ Section name successfully updated to 'ABRADABRA'");

        // 7. Logout
        //homePage.logoutFromApplication();
    }

    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "To create new department of Inventory Group from UI",
            dataProvider = "dataSupplier", dataProviderClass = MergedInvGroupDataSupplier.class)
    public void CreateDepartment(MergedInventoryGroupData groupData) throws InterruptedException {
        System.out.println("Running test for: " + groupData.getTestCaseId());
        System.out.println("Inside department creation");
        ReportLogger.info(LogCategory.VALIDATION,
                "Expected Result: Department node created. DB — invgrp: 1 new row with correct name at level 3. "
                + "UI: node visible in group hierarchy at depth 3.");
        // 1. Login
        UserCredential u = DriverManager.getCurrentUser();
        LoginPage loginPage = new LoginPage();
        HomePage homePage = loginPage.loginToApplication(u.username(), u.password());

        // 2. Navigate to Group List
        GroupListPage groupListPage = NavigationUtils.navigateToInventorySideMenu(
                homePage,
                MainMenuType.PRODUCT_DEFINITION,
                Product_DefintionMenuType.GROUP,
                GroupListPage.class
        );
        //Click the expander on the division.After that right on section and click 'Add New Department'
        groupListPage.clickdivisionsectionexpander(groupData.getGroup().getParentGroup()).clickaddDepartment(groupData.getGroup().getParentGroup1());


        // 4. Fetch tree panel data
        List<String> results = JavascriptExecutorUtils.getTreePanelTexts();
        results.forEach(System.out::println);

        // 5. Determine oldDivision
        long count = JavascriptExecutorUtils.countTreeOccurrences(GroupHierarchyType.DEPARTMENT);
        System.out.println("Department Count" +count);

        String oldDepartment = (count == 1)
                ? GroupHierarchyType.DEPARTMENT.getDisplayName()
                : GroupHierarchyType.DEPARTMENT.getDisplayName() + (count - 1);
        System.out.println("Old Department: " + oldDepartment);

        // 6. Get new division name from Excel
        String newDepartment = groupData.getGroup().getGroupName();
        System.out.println(newDepartment);

        // 7. Handle duplicates
        if (JavascriptExecutorUtils.doesTreeContainText(GroupHierarchyType.DEPARTMENT, newDepartment)) {
            newDepartment = newDepartment + " " + System.currentTimeMillis();
        }
        System.out.println("New Department "+newDepartment);

        String oldValue = JavascriptExecutorUtils.updateChildText(groupData.getGroup().getParentGroup1(), oldDepartment,GroupHierarchyType.DEPARTMENT.getDepth(), newDepartment);
        System.out.println("Old child text was: " + oldValue);

        //Click Root to update Section name
        groupListPage.clickRoot();

        System.out.println("After update");
        List<String> updated = JavascriptExecutorUtils.getTreePanelTexts();
        updated.forEach(System.out::println);

        // Verify newDivision exists exactly once at Depth 3
        long updatedCount = JavascriptExecutorUtils.countTreeOccurrences(GroupHierarchyType.DEPARTMENT, newDepartment);
        if (updatedCount == 1) {
            System.out.println("✅ Department name successfully updated to: " + newDepartment);
        } else {
            throw new AssertionError("❌ Expected 1 occurrence of '" + newDepartment +
                    "' at Depth 3, but found " + updatedCount);
        }

        com.gsl.driver.SessionManager.closeSessionAndReleaseUser("Create Group complete");
    }

}

