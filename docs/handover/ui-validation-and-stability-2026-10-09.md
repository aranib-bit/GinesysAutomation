# Handover: Common UI validation (GV / AP / AR) + run stability

Session ended 2026-10-09. Final live run of `testng-ar-ap-mandatory.xml` (staging): **all 14 passed**.
Next step owned by the user: review the HTML report for formatting issues.

---

## 1. Goals (as set by the user)

- One common UI-validation standard for General Voucher (GV), AP Voucher and AR Voucher.
- UI validation is always a **soft assert**. Save, DB validation and snapshot validation must still run, and everything is reported at the end.
- Report layout:
  - grouped, indented hierarchy;
  - every row shows **Before fill** and **After fill**;
  - **Grid footer is the last sub-section**;
  - **Payment Information comes after Expense/Income Detail**;
  - exactly **one consolidated "UI Full Validation Details" per Create flow**.
- The GL / Sub Ledger picker popups are validated: header, rows, footer, before and after fill.
- Live failures are fixed at the root cause.

## 2. Working rules agreed with the user

- **Plan before execution:** show the plan as text (the user replies "Show the plan..."), then wait for approval.
- **No threshold or timeout masking:** fix the cause and capture diagnostics; never raise a timeout to hide a problem.
- **No hardcoding:** expected values live in Excel or config. UI baselines are in `UiStyleBaseline.xlsx`, and the observed live values are the baseline.
- **Locators:** prefer native UI (Selenium actions, DOM XPaths) over ExtJS injection. Avoid positional `[1]` XPath indexes unless proven necessary.
- **Paths:** keep runtime and temp paths inside the project.
- **Logins:** they come from `UsersList.xlsx` via `UserPool`. The config `appusername` / `apppassword` are obsolete.
- **Launching test JVMs:** use WMI, never Claude's process tree. `runInParallel` is incompatible with session reuse.
- **Commits:** none were requested; all changes are uncommitted on branch `GinesysAutomation`.

## 3. Build and run

```
JAVA_HOME = C:\Users\arani.barman\.jdks\ms-17.0.19
Maven     = C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\plugins\maven-plugin\lib\maven3\bin\mvn.cmd
Unit tests (offline):
  mvn -o test -f pom.xml -Dtest=UiValidationStandardsTest,PickerAndCellLocatorTest,ColdStartTimeoutTest,PickerReExpandTest,ReportOnceTest -Dsurefire.failIfNoSpecifiedTests=false
Suites: testng-ar-ap-mandatory.xml, gv-mandatory, -Pgv-ui / -Pap-ui / -Par-ui
Reports: reports\runs\<runId>\ (index.html, summary.html, logs.zip, screenshots\, html-captures\)
```

Logs inside `logs.zip`:

| File | What it holds |
|---|---|
| `diagnostics.log` | Small, never flooded. Look here first for session, cold-start, skip and failure lines. |
| `framework.log` + `rolled/*.gz` | The full trace. |
| `ui-actions.log`, `business.log`, `db-validation.log` | UI actions, business steps and DB checks. |

---

## 4. Architecture of the UI-validation standard

All paths are under `src/main/java/com/gsl/`.

### Reporting

| Piece | File | Role |
|---|---|---|
| Path nodes | `validation/ui/UiValidationScope` | Nesting: section > row > popup > Before/After fill |
| Result | `validation/ui/UiValidationFailure` | PASS / FAIL / SKIP + component / property / path |
| Deferred soft failures | `validation/ui/UiFailureCollector` | Parked, then thrown once at the flow's end |
| Renderer | `validation/ui/UiValidationReporter` | `renderDeferred`, `holdForFill` / `flushHeld` / `clearHeld` / `drainHeld`; `orderNodes` (see below); grouping by component; display names from the workbook |
| Second tree (Actual Results) | `reports/ActualResultsLogger` | Same ordering and grouping helpers |
| Final assert | `flow/.../Common/VoucherSaveFlowSteps.finalizeAsserts`, `UiFailureCollector.assertNoPending`, `listeners/TestListener` (backstop + per-test `clearHeld`) | |

`orderNodes` sets the top-level order: Document Information, then the grid section, then Payment Information, then Page Footer. Any "Grid footer…" node always sorts last.

### Common stage

`validation/ui/voucher/VoucherUiValidationStage` is the common stage, with `VoucherUiSpec` per voucher.

**One consolidated report per Create flow:**
1. The blank form is captured with `validateUiContractBeforeFill(...)`, which uses `holdForFill`.
2. After the fill, a single "UI Validation" step renders everything.
3. The steps are named "Capture blank-form UI state" and "UI Validation", in `CreateGeneralVoucher`, `CreateApVoucher`, `CreateArVoucher` and the predicate factories.

### Per-row recording

| Voucher | Where | What |
|---|---|---|
| GV | `GeneralVoucherExpenseIncomeDetail` | `FillUiListener` with `beforeRowFilled` / `afterRowFilled` |
| AP / AR | `validation/ui/voucher/VoucherFillUiRecorder` | Row > "Cells and row buttons", popups, picker observations |

### Generic helpers

| Helper | Purpose |
|---|---|
| `utils/Selenium/GridCellLocator` | Finds `td[@data-columnid = header id OR itemId]`, matching the header on the exact `x-column-header` class token. Hidden columns have a header but no `td`. `LEDGER_NARRATION` has itemId `ledgerNarrationCode`. |
| `IVoucherGridColumnHeader.itemId()` | Default `null`; supplies the itemId above. |
| `validation/ui/voucher/VoucherGridChrome.footerFields()` | The toolbar containing the "Record Count" label (works for AP, AR and GV). |
| `validation/ui/voucher/PopupUi`, `PaymentPopupUi` | Popup checks. Payment popup style follows the field state (`FieldStateProbe`). Instrument No. element type is checked per payment mode: `PAYMENT_POPUP_INSTRUMENT_NO_<MODE>`. |
| `UiElementType.parseAll` | Lets a workbook cell hold alternatives, e.g. `Text field\|Dropdown`. |

### Picker popups (GL / Sub Ledger)

The checks live in `validation/ui/PickerUi`, which records `Snapshot` and `Observation`. Snapshots are taken at two points:
- **before:** when the editor opens;
- **after:** through the `beforeClick` hook of `SeleniumUtils.clickBoundlistOptionAcrossPages(root, option, tag, beforeClick)`, at the moment the option has been found.

**Before fill** is a SKIP when the list hasn't loaded yet ("Page 0 of 0").

| Voucher | Picker columns |
|---|---|
| AP Sub Ledger | Name, Id, Class Name |
| AR Sub Ledger | Name, Id, Ledger, Class Name |
| GV General Ledger | Name, Abbreviation, Group, Site Applicable, Sub Ledger Appl. |
| GV Sub Ledger | Name, Id, Class Name |

GV also checks that Site Applicable / Sub Ledger Appl. match the row's `GlLineType`.

### Baseline workbook

`src/test/resources/env/*/data/Finance/UiStyleBaseline.xlsx`: 10 env copies, which must be kept in sync.
- **Sheets:** Common, GV, AR, AP.
- **Columns:** ElementType, Mandatory, EnabledBeforeFill, DisplayName, plus the style columns.
- **Notable rows:**
  - Payment-popup element types are set per voucher. AR: Instrument No. = Text field, "Drawn on:" = Search field. AP/GV: Instrument No. = Text field|Dropdown, "Cheque label:" = Text field.
  - Adjustment popup rows use 11px.
  - `FOOTER_BUTTON_COPY` is on the Common sheet.

---

## 5. Stability fixes made this session (rounds 17-23)

| Round | Problem (evidence) | Fix |
|---|---|---|
| 17 | Ledger Narration cell "NOT FOUND" | `GridCellLocator` (column-id, not header counting) |
| 17 | Adjustment "sum of cells" wrong | `AdjustmentEntry.setAdjustedAmountForDocument` commits with Tab + waits for the cell |
| 17 | Mismatch DB expected 16 vs saved 8 | `MergedFinanceApVoucherData.expectedSavedAmountDr` / `chooseSavedAmount` |
| 17 | AR/AP supplier-time user lookup | `utils/dataprovider/source/finance/SupplierUser` (thread user, else first pool user with an hrdemp row) |
| 18 | Picker checks fired too early | `beforeClick` snapshot; unloaded list = SKIP |
| 19 | `logs.zip` lost the run (flood) | `LoggingAspect` excludes `utils.io`; `LogArchiver` zips rolled `*.log.gz` since run start |
| 20 | Recurring AR SKIP (cold-start `java.util.concurrent.TimeoutException`) | `Driver.navigateToApp` applies `pageload.timeout.seconds` around the first load (it was never applied before), logs page state, reloads once. `SessionPool` retries ONE fresh browser on infra timeouts (`isColdStartTimeout`). The `LoggingAspect` exclusions above (`toString`/`hashCode`/`equals`, `UiStyleBaseline`) were also added. |
| 21 | AR Sub Ledger list collapsed after typing (row loaded, `display:none`, `aria-expanded=false`) | Shared re-expand (ALT+DOWN, once) inside `SeleniumUtils.clickBoundlistOptionAcrossPages` plus picker-state diagnostics; GV's private copy removed. `LoggingAspect` also excludes zero-arg `get*`/`is*`, `model`/`config`/`diagnostics`, `UiValidationFailure`/`UiValidationReporter` and `*Combinations`. |
| 22 | AP doc re-used with wrong pending | Fail-fast "Test data conflict" in `AdjustmentEntry` (kept). `LoggingAspect` excludes `lambda$*`. (The run-wide document hold was reverted in R23.) |
| 23 | AR SKIP from the R22 run-wide hold | Reverted `holdForRun` (the R22 premise was wrong; see 6). Conflict message now shows the live `v_pending_adj` value (`PendingAdjQuery.livePendingForDiagnostics`). |
| 23 | AR "Select Document not interactable" | The Adjusted Document "•••" menu is opened right before its option is used: AR branches, and AP via `AdjustmentFlowSteps.applyAdjustment`. `openAdjustmentEntry` only clicks Adjust. |
| 23 | AP test hung 15 min (hung renderer + one screenshot per AOP frame at ~60 s each) | `LoggingAspect` reports an exception to Extent once (`firstReport` / `isSameOrWraps`). `Driver.navigateToApp` requires the login form (`LoginPage.usernameField()`) after load, otherwise a `TimeoutException`, which leads to a fresh browser via `SessionPool`. |

Unit tests added in `src/test/java/com/tests/Validation/`:
- `PickerAndCellLocatorTest`, `ColdStartTimeoutTest`, `PickerReExpandTest`, `ReportOnceTest`;
- additions to `UiValidationStandardsTest` (ordering, held results, `parseAll`).

### Run history (AR/AP mandatory)

| Run | Result |
|---|---|
| 20261009_115711 | 0 pass / 10 fail / 4 skip |
| 20261009_130742 | 13 / 0 / 1 |
| 20261009_134343 | 13 / 0 / 1 |
| 20261009_142905 | 13 / 1 / 0 |
| 20261009_152746 | 13 / 1 / 0 |
| 20261009_164937 | 11 / 2 / 1 |
| latest | **14 / 0 / 0** |

---

## 6. Facts verified in the database (staging idealdb, 172.22.4.4)

- `v_pending_adj.pending = camount + damount - adjamt` from `finpost`. Only posted rows are included (`release_status='P'`) and only `fingl.srctype='R'`.
- Test vouchers are posted on save.
- `fintag` triggers update `finpost.adjamt` in the same transaction. Audit table: `aud2_fintag`.
- **Unexplained:** run 152746 read `Pending=20` for TDS/06980/26-27 at 15:39:33, although the 10.00 tag was inserted at 15:38:25. If a "Test data conflict" failure appears again, its message now shows the popup pending, the live DB pending and the typed amount side by side.
- Document selectors limit each Sub Ledger to its **oldest** `documentCount` documents before the reservation filter. Effects:
  - a Sub Ledger whose oldest document is reserved is unusable for that moment;
  - backfilled documents are only seen once older ones are free;
  - this is intended for FIFO, because the app always adjusts the oldest document first.

---

## 7. Open items (not started)

1. **Formatting review** of the latest report. The user is doing this; expect layout tweaks in `UiValidationReporter` / `ActualResultsLogger`.
2. **Run the GV suite** (`gv-mandatory` and `-Pgv-ui`) to confirm the shared changes on GV:
   - picker snapshots;
   - `GridCellLocator`;
   - the picker re-expand;
   - the `LoggingAspect` exclusions;
   - the login-form check.
3. **AP AdvancePayment stale editor:** the Amount editor id `numberfield-…-inputEl` goes stale after the Sub Ledger is selected. It needs re-locating by role, not by generated id.
4. **AR stale Sub Ledger combo:** an occasional `stale element reference` on the combo input.
5. **AR picker business check:** the target row's Ledger should equal the data's General Ledger, but that General Ledger isn't passed into the observation yet.
6. **Picker header/row style baselines:** no style checks yet. Add `PICKER_*` rows to the workbook from observed values.
7. **Unexplained stale-pending read** (section 6): investigate only if a "Test data conflict" failure recurs.
8. **Commit** when the user asks. Everything is uncommitted on `GinesysAutomation`; a plan history is in `C:\Users\arani.barman\.claude\plans\pasted-content-id-0773-ui-validation-curious-melody.md`.

## 8. Debugging tips that worked

- **A skip or failure with a bare `TimeoutException`:**
  - read `diagnostics.log` for `[SessionPool]`, `[Driver.initDriver]` and "Cold start" lines;
  - then use `html-captures/*.html` for the DOM at the moment of failure.
- **"Option not found" in a picker:** search the capture for the combo's `-picker` div. Check its `display` and whether the row exists; the input shows `aria-expanded`.
- **A click blocked by a mask:** the failure screenshot usually shows an unexpected Confirmation dialog.
- **Logs:** if `logs.zip` lacks the run start, check the first line of each `rolled/framework-*.log.gz` and count `[START]` methods to find the new flood source. Fix it with a pointcut exclusion, never with a larger rollover cap.
- **DB facts:** the postgres MCP is read-only (`query_data`). It sometimes returns ECONNRESET; retrying later works.
