# UI validation: one common stage for every voucher

Every voucher's UI validation goes through `com.gsl.validation.ui.voucher.VoucherUiValidationStage`.
Nothing else orders checks or builds report nodes.

| Layer | Class | Owns |
|---|---|---|
| Stage | `VoucherUiValidationStage` | what is validated, in what order, under which report nodes; render / deferred render |
| Spec (data only) | `VoucherUiSpec` → `ApVoucherUiSpec`, `ArVoucherUiSpec` | domain, grid columns (+ DEFAULT/USER kind), extra fields, required flags, expected section state |
| Mechanics | `UiComponentValidator`, `VoucherGridChrome` | element type, before/after state, mandatory column, count pair, toolbar, footer, row buttons, label-driven fields, collapsible blocks |
| Baseline | `Finance/UiStyleBaseline.xlsx` | style values AND `ElementType`, `Mandatory`, `EnabledBeforeFill`, `EnabledAfterFill` per Component |
| Report | `UiValidationReporter` | tree of nodes, one group per component, OPEN/CLOSED badges |

## Rules
* A new validation is added once, in the stage (generic check in `UiComponentValidator`/`VoucherGridChrome`).
  A voucher opts in/out only through its spec. Never copy a check into one voucher's Add page.
* A new voucher = one `VoucherUiSpec` + one sheet in the baseline workbook.
* Expectations live in the workbook, not in code. A component with no baseline is a visible SKIP that shows the
  observed value (e.g. `not baselined — detected: Dropdown`) — copy it into the workbook to lock it in.
* Elements a voucher does not have (Adjust button, Adjustment Information block, ...) are SKIP "not present", never FAIL.

## Entry points
```java
VoucherUiValidationStage.forSpec(spec).blankForm(level, state, isEdit).finish();          // throws on FAIL
VoucherUiValidationStage.forSpec(spec).blankForm(level, state, isEdit).finishDeferred();  // parks FAILs
stage.headerFill(Runnable)                      // before/after of every Document Information field
stage.rowFlow(n, fillRow, selectLine, level)    // per row: footer count +1, row buttons, Narration/Sub Ledger Balance
                                                //          and Payment panel after line selection, block state change
```
`ApVoucherAdd#uiStage()` / `ArVoucherAdd#uiStage()` build the stage for their voucher.
