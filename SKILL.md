# SKILL.md — SeleniumFramework3.141

## Project Overview
An enterprise-grade Selenium test automation framework built for the **GINESYS ERP web application**. It automates UI workflows across modules including Inventory, Procurement, Admin, and Finance. The framework is designed for scalability, thread safety, and data-driven testing.

---

## Tech Stack

| Technology         | Version       | Purpose                                  |
|--------------------|---------------|------------------------------------------|
| Java               | 17            | Core language                            |
| Selenium           | 4.39.0        | Browser automation                       |
| TestNG             | 7.4.0         | Test execution and lifecycle management  |
| ExtentReports      | 5.0.8         | HTML test reporting                      |
| Apache POI         | 5.1.0         | Excel data reading/writing               |
| ZeroCell           | 0.5.1         | Excel-to-POJO mapping                    |
| Owner (Aeonbits)   | 1.0.12        | Config property file binding             |
| JavaFaker          | 1.0.2         | Random test data generation              |
| TestDataSupplier   | 2.3.0         | Stream-based TestNG DataProvider         |
| JDBI3              | 3.49.6        | SQL database access                      |
| HikariCP           | 7.0.2         | Database connection pooling              |
| PostgreSQL/Oracle/MSSQL | latest   | Multi-DB support                         |
| Lombok             | 1.18.34       | Boilerplate reduction (Builder, Getter)  |
| Log4j2             | 2.20.0        | Logging                                  |
| WebDriverManager   | 6.0.1         | Automatic browser driver management      |
| Maven              | —             | Build and dependency management          |

---

## Project Structure

```
src/
├── main/java/com/gsl/
│   ├── annotations/        # Custom annotations: @FrameworkAnnotation, @InvocationEnabled
│   ├── config/             # Config interfaces (Owner): FrameworkConfig, DataBaseConfig, ExcelDataConfig
│   ├── constants/          # FrameworkConstants (paths, static values)
│   ├── converter/          # Excel enum-to-Java converters (e.g., AllowPriceChangeConverter)
│   ├── database/           # DB layer: DatabaseManager, JdbiFactory, DAO interfaces
│   ├── driver/             # WebDriver lifecycle: Driver, DriverManager (ThreadLocal), DriverFactory
│   ├── enums/              # Domain enums: WaitType, TestAuthor, inventory/procurement types
│   ├── listeners/          # TestNG listener: TestListener (report init, pass/fail/start hooks)
│   ├── model/              # DB model POJOs (e.g., InvGrp)
│   ├── pages/              # Page Object Model (POM)
│   │   ├── LoginPage, HomePage, IndexPage
│   │   ├── pagecomponent/  # Module home pages + top/side menu components
│   │   │   ├── Inventory/  # InventoryHomePage, TopMenuComponent, Product_Definition/, WMS/, etc.
│   │   │   ├── Procurement/# ProcurementHomePage, Operations/, Setup/
│   │   │   └── Admin/      # AdminHomePage, Organization/, Finance/, Security/
│   │   ├── Inventory/      # Action pages: CategoryListPage, ItemListPage, ItemAdd, GroupPage
│   │   ├── Procurement/Operations/ # GoodsReceiveChallanAdd, SelectItemPage, ScanItemPage, etc.
│   │   ├── Common/         # Shared popups: DocumentMessageBox
│   │   └── enums/          # Page-scoped menu enums per module
│   ├── reports/            # ExtentReport, ExtentLogger, ExtentManager
│   ├── testdata/           # DTOs, data builders, and Excel-mapped POJOs
│   │   ├── dto/            # Row-level DTOs: ItemDetailsRow, ScanItemRow, MergedReceiptData
│   │   ├── Inventory/      # Item, Group POJOs + MergedInventoryItemData builder
│   │   ├── Procurement/    # ReceiptHeader, ItemDetail subtypes, ScanItem, etc.
│   │   ├── annotations/    # @Mandatory, @AtLeastOneMandatory for DTO validation
│   │   └── MasterTestData  # Master Excel POJO mapping class
│   ├── utils/
│   │   ├── Selenium/       # SeleniumUtils, WaitUtils, ActionUtils, ExtJsUtils, XpathUtils
│   │   ├── dataprovider/   # DataProviderUtils + Merged*DataSupplier classes
│   │   ├── Faker/          # FakerUtils (random name/email/number generators)
│   │   └── NavigationUtils # Module navigation helper
│   └── validation/         # MandatoryFieldValidator, ExcelEnumValidator
│
└── test/
    ├── java/com/tests/
    │   ├── BaseTest.java                     # @BeforeMethod / @AfterMethod (Driver init/quit)
    │   ├── LoginTest.java
    │   ├── Inventory/Product_Definition/     # CreateItem, CreateGroup, CreateCategory, CreateGroupTest
    │   └── Procurement/Operations/           # Receipts, ReceiptsDataTest
    └── resources/
        ├── config/
        │   ├── config.properties             # browser, url, runmode, timeout
        │   ├── databaseconfig.properties     # DB connection settings
        │   └── exceldata.properties          # Excel file paths
        ├── Inventory/
        │   ├── Item.xlsx
        │   └── ItemGroup.xlsx
        └── Procurement/
            ├── GRC.xlsx
            └── Receipts.xlsx
```

---

## Running Tests

```bash
# Run default suite via Maven (smoke profile)
mvn test -Psmoke

# Run directly via TestNG XML
# Right-click testng.xml → Run in IntelliJ
```

The active suite is configured in `testng.xml`. Add/remove `<class>` entries to control which tests run.

---

## Configuration

All config is managed via `src/test/resources/config/config.properties` using the Owner library.

| Key                  | Description                              | Example Value                              |
|----------------------|------------------------------------------|--------------------------------------------|
| `browser`            | Browser to use                           | `chrome`, `firefox`, `edge`                |
| `runmode`            | Local or remote grid                     | `local`, `remote`                          |
| `url`                | Application under test URL               | `https://stage-ideal.ginesys.cloud/...`    |
| `timeout`            | Explicit wait timeout (seconds)          | `30`                                       |
| `remoteurl`          | Selenium Grid URL                        | `http://localhost:4444/wd/hub`             |
| `itemscanmaxtimeout` | Fluent wait max timeout for item scan    | `180000` (ms)                              |
| `pollingof`          | Fluent wait polling interval (seconds)   | `2`                                        |

Config is accessed via: `ConfigFactory.getConfig().<method>()`

---

## WebDriver Management

- **ThreadLocal** pattern — `DriverManager` stores `WebDriver` per thread using `ThreadLocal<WebDriver>`.
- Driver is initialized in `Driver.initDriver()` and torn down via `Driver.quitDriver()`.
- `DriverFactory` delegates to `LocalDriverFactory` or `RemoteDriverFactory` based on `runmode`.
- WebDriverManager auto-resolves browser drivers — no manual driver downloads needed.
- `BaseTest.setUp()` calls `Driver.initDriver()` in `@BeforeMethod`.

---

## Page Object Model (POM)

### Pattern
Pages are plain Java classes. No `PageFactory` or `@FindBy` is used. All element locators are defined inline using `By` (XPath/CSS). Page methods return `this` or the next page for fluent chaining.

### Menu Navigation
Use `NavigationUtils` to navigate between modules and sub-menus:

```java
CategoryListPage categoryListPage = NavigationUtils.navigateToInventorySideMenu(
    homePage,
    MainMenuType.PRODUCT_DEFINITION,
    Product_DefintionMenuType.CATEGORY,
    CategoryListPage.class
);
```

Module menus are driven by enums under `pages/enums/`. Each module has its own `MainMenuType` and sub-menu enums (e.g., `Product_DefintionMenuType`, `OperationsMenuType`).

### Page Component Pattern
Module pages are split into:
- **HomPage** — landing page after clicking a module (e.g., `InventoryHomePage`)
- **TopMenuComponent** — top horizontal menu navigation
- **SideMenuComponent** — left side menu navigation
- **Action Pages** — actual form/list pages (e.g., `ItemAdd`, `GoodsReceiveChallanAdd`)

Complex forms are split into tab-specific page components (e.g., `ItemAddGeneral`, `ItemAddPricing`, `ItemAddInventoryTracking`).

---

## Selenium Utilities

All utility methods are in `com.gsl.utils.Selenium.*` and wrap WebDriver calls with explicit waits and ExtentReport logging.

### SeleniumUtils
```java
SeleniumUtils.click(by, elementName);
SeleniumUtils.click(by, WaitType.CLICKABLE, elementName);
SeleniumUtils.sendKeys(by, WaitType.PRESENCE, value, elementName);
SeleniumUtils.getText(by, elementName);
SeleniumUtils.getAttribute(by, "value", elementName);
SeleniumUtils.elementDisappear(by, elementName);  // returns boolean
SeleniumUtils.waitForInputReady(by);
```

### WaitUtils
```java
WaitUtils.fluentWait(by);                     // For item scan (long timeout, fluent)
WaitUtils.waitUntilAjaxAndElementClickable(by); // Waits for ExtJS AJAX + element clickable
```

### WaitType Enum
Used to specify wait strategy in `SeleniumUtils`:
- `WaitType.PRESENCE` — `presenceOfElementLocated`
- `WaitType.VISIBLE` — `visibilityOfElementLocated`
- `WaitType.CLICKABLE` — `elementToBeClickable`

### ExtJS Support
- `ExtJsUtils` — dropdowns, combo boxes, grid interactions for Ext JS components
- `ExtJsTreeUtils` — tree node expansion and selection
- `XpathUtils` — dynamic XPath generators for form labels and sections
- `JavascriptExecutorUtils` — page load waits, ExtJS load wait, JS clicks

---

## Test Data

### Excel-Driven Data (ZeroCell + Apache POI)
Test data is stored in `.xlsx` files under `src/test/resources/`:
- `Inventory/Item.xlsx`, `Inventory/ItemGroup.xlsx`
- `Procurement/Receipts.xlsx`, `Procurement/GRC.xlsx`
- `MasterTestData.xlsx`

Excel files are mapped to POJOs using **ZeroCell** annotations on fields. File paths are configured via `exceldata.properties`.

### Data Suppliers (TestDataSupplier)
Data is supplied to `@Test` methods via `@DataSupplier` using `IDataSupplier` implementations:
- `MergedInvItemDataSupplier` — Inventory item test data
- `MergedInvGroupDataSupplier` — Inventory group test data
- `MergedReceiptDataSupplier` — Procurement receipt test data

Merged data objects (e.g., `MergedInventoryItemData`) aggregate multiple Excel POJOs using the **Builder pattern** (Lombok `@Builder`).

### Data Annotations
Custom annotations on DTO fields control validation:
- `@Mandatory` — field must be non-null/non-empty
- `@AtLeastOneMandatory` — at least one of the annotated fields must be present
- Validated via `MandatoryFieldValidator`

### FakerUtils
```java
FakerUtils.generateName();
FakerUtils.generateEmail();
// etc.
```

---

## Reporting (ExtentReports)

- `ExtentReport.initReports()` — called in `TestListener.onStart()`
- `ExtentReport.createTest(description)` — creates a new test node
- `ExtentReport.assignAuthor(TestAuthor[])` — assigns author from `@FrameworkAnnotation`
- `ExtentReport.flushReports()` — called in `TestListener.onFinish()`

Log during tests:
```java
ExtentLogger.pass("Step passed");
ExtentLogger.fail("Step failed");
ExtentLogger.info("Some info");
```

---

## Custom Annotations

### `@FrameworkAnnotation`
Applied to each `@Test` method to define test metadata:
```java
@FrameworkAnnotation(TestAuthor.ARANI)
@Test(description = "Test description shown in report")
public void myTest() { ... }
```
`TestAuthor` is an enum of valid test authors.

### `@InvocationEnabled`
Marks methods eligible for repeated invocation (used with data-driven tests).

---

## Database Access (JDBI3 + HikariCP)

- `JdbiFactory` creates `Jdbi` instances with HikariCP connection pooling.
- `DatabaseManager` manages multi-DB connections (PostgreSQL, Oracle, MSSQL).
- DAOs are interfaces under `database/dao/` using JDBI3 `@SqlQuery` / `@SqlUpdate`.
- DB config sourced from `databaseconfig.properties`.

---

## Adding a New Test

1. Create a test class under `src/test/java/com/tests/<Module>/<SubModule>/`.
2. Extend `BaseTest`.
3. Annotate the test method with `@FrameworkAnnotation(TestAuthor.XXX)` and `@Test(description = "...")`.
4. Use `LoginPage` → `HomePage` → `NavigationUtils` to navigate.
5. Interact via Page Object classes.
6. Add the class to `testng.xml`.

```java
public class CreateVendor extends BaseTest {
    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "Create a new vendor in Procurement Setup")
    public void createVendor() {
        LoginPage loginPage = new LoginPage();
        HomePage homePage = loginPage.loginToApplication("user", "pass");
        VendorListPage vendorListPage = NavigationUtils.navigateToProcurementSetupSideMenu(
            homePage, SetupMenuType.VENDOR, VendorListPage.class
        );
        vendorListPage.clickAdd();
        // ... fill form
    }
}
```

---

## Adding a New Page Object

1. Create your class under `src/main/java/com/gsl/pages/<Module>/`.
2. Define `By` locators as private static finals.
3. Write public methods that use `SeleniumUtils`, `WaitUtils`, or `ExtJsUtils`.
4. Log steps using `ExtentLogger.info(...)`.
5. Return `this` or the next page object for fluent chaining.

---

## Adding a New Data-Driven Test

1. Create a POJO class with ZeroCell `@Column` annotations.
2. Map it to a sheet in the corresponding `.xlsx` file.
3. Create a `IDataSupplier` implementation in `utils/dataprovider/`.
4. In your test, use `@Test(dataProvider = "...", dataProviderClass = DataProviderUtils.class)`.

---

## Key Conventions

- All page locators use `By` (XPath or CSS) — no `PageFactory`.
- All Selenium interactions go through `SeleniumUtils` (not directly on `WebDriver`).
- Always log actions using `ExtentLogger` inside page methods.
- Test data objects use Lombok `@Builder` pattern.
- Enum converters in `converter/` translate Excel string values to Java enums.
- Navigation between modules is always done through `NavigationUtils`.
- Each module has its own enum hierarchy under `pages/enums/<Module>/`.
