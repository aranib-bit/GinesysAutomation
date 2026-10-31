package com.tests.API;

import com.gsl.testdata.ExecutionSet;
import com.gsl.testdata.LoginType;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserCredentialLoader;
import com.gsl.testdata.UserType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.annotations.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** The usertype/logintype columns of UsersList.xlsx: [UI, WEBAPI] is the only allowed combination; everything else is alone; legacy sheets still read as UI/ERP. */
public class UserCredentialLoaderUnitTest {

    @Test
    public void bracketsAndBareValuesParseToTheSameSets() {
        assertEquals(UserCredentialLoader.parseTypes("[UI, WEBAPI]", 2), EnumSet.of(UserType.UI, UserType.WEBAPI));
        assertEquals(UserCredentialLoader.parseTypes("[webapi,ui]", 2), EnumSet.of(UserType.UI, UserType.WEBAPI), "either order, any case");
        assertEquals(UserCredentialLoader.parseTypes("UI", 2), EnumSet.of(UserType.UI));
        assertEquals(UserCredentialLoader.parseTypes("[PERF]", 2), EnumSet.of(UserType.PERF));
        assertEquals(UserCredentialLoader.parseTypes("API", 2), EnumSet.of(UserType.WEBAPI), "the legacy value API reads as WEBAPI");
        assertEquals(UserCredentialLoader.parseTypes("[PUBLICAPI]", 2), EnumSet.of(UserType.PUBLICAPI));
    }

    @Test
    public void everyCombinationOtherThanUiPlusWebapiIsRejectedWithTheRow() {
        for (String bad : List.of("[PERF, WEBAPI]", "[UI, PERF]", "[UI, PUBLICAPI]", "[WEBAPI, PUBLICAPI]", "[UI, WEBAPI, PERF]")) {
            IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> UserCredentialLoader.parseTypes(bad, 7));
            assertTrue(e.getMessage().contains("row 7") && e.getMessage().contains("only allowed combination"), e.getMessage());
        }
    }

    @Test
    public void malformedCellsAreRejected() {
        for (String bad : List.of("", "[]", "[UI,]", "[UI,,WEBAPI]", "[UI", "UI]", "[UI, UI]", "[ADMIN]", "FOO")) {
            expectThrows(IllegalArgumentException.class, () -> UserCredentialLoader.parseTypes(bad, 3));
        }
        expectThrows(IllegalArgumentException.class, () -> UserCredentialLoader.parseTypes(null, 3));
    }

    @Test
    public void loginTypeDefaultsToErpAndRejectsUnknownValues() {
        assertEquals(UserCredentialLoader.parseLoginType(null, 2), LoginType.ERP);
        assertEquals(UserCredentialLoader.parseLoginType("  ", 2), LoginType.ERP);
        assertEquals(UserCredentialLoader.parseLoginType("sso", 2), LoginType.SSO);
        expectThrows(IllegalArgumentException.class, () -> UserCredentialLoader.parseLoginType("saml", 2));
    }

    private static Workbook workbook(String sheetName, String[] header, String[][] rows) {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet(sheetName);
        Row h = sheet.createRow(0);
        for (int i = 0; i < header.length; i++) h.createCell(i).setCellValue(header[i]);
        for (int r = 0; r < rows.length; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < rows[r].length; c++) row.createCell(c).setCellValue(rows[r][c]);
        }
        return wb;
    }

    @Test
    public void aUsersSheetWithTheNewColumnsLoadsTypesAndLoginTypes() throws Exception {
        try (Workbook wb = workbook("Users", new String[]{"username", "password", "usertype", "logintype"}, new String[][]{
                {"webapi1", "p1", "[UI, WEBAPI]", ""},
                {"webui1", "p2", "[UI]", "ERP"},
                {"perftest1", "perftest1", "[PERF]", "SSO"},
                {"broken", "x", "[PERF, UI]", ""}})) {
            List<UserCredential> users = UserCredentialLoader.fromWorkbook(wb);
            assertEquals(users.size(), 3, "the row with a rejected combination is skipped, not loaded");
            UserCredential shared = users.get(0);
            assertEquals(shared.types(), Set.of(UserType.UI, UserType.WEBAPI));
            assertTrue(shared.isShared());
            assertEquals(shared.loginType(), LoginType.ERP);
            assertEquals(users.get(2).types(), Set.of(UserType.PERF));
            assertEquals(users.get(2).loginType(), LoginType.SSO);
            assertEquals(users.get(2).executionSet(), ExecutionSet.SET1);
        }
    }

    @Test
    public void aLegacyUiUsersSheetWithoutTheColumnsStillReadsAsUiAndErp() throws Exception {
        try (Workbook wb = workbook("UIUsers", new String[]{"username", "password"}, new String[][]{{"webui1", "pw"}, {"webui2", "pw"}})) {
            List<UserCredential> users = UserCredentialLoader.fromWorkbook(wb);
            assertEquals(users.size(), 2);
            for (UserCredential u : users) {
                assertEquals(u.types(), Set.of(UserType.UI));
                assertEquals(u.loginType(), LoginType.ERP);
                assertEquals(u.executionSet(), ExecutionSet.SET1);
            }
        }
    }
}