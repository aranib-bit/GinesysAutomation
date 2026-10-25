# Utility Layering Convention: generic mechanics vs. project/domain logic

## The rule

A class is **generic** if it only needs to change when the underlying *mechanism* changes
(e.g. "switch POI version", "ExtJS markup changed", "switch from POI to a different Excel
library"). A class is **domain/project-specific** if it needs to change when a *business
rule* changes (e.g. "TDS calculation changed", "a new mandatory grid column was added", "GL-type
resolution logic changed").

A generic class:
- Never imports domain packages (`com.gsl.pages.enums.Finance.*`, `com.gsl.testdata.Finance.*`,
  `com.gsl.enums.VoucherDomain`, `GlLineType`, and similar).
- Accepts its inputs only via primitives, `String`, generic collections, and functional
  interfaces (`Function`, `BiConsumer`, `Supplier`, etc.) — never a concrete domain POJO or enum.

A domain-specific class is free to depend on generic utilities, and adds the business meaning:
which sheet/column means what, which rule applies, what to do with a parsed value.

**Litmus test before writing a new utility:** *does this need to know a business rule to work?*
If yes, it's domain-specific and belongs near the domain code it serves — not in
`com.gsl.utils`. If no, it belongs in a generic package, and its method signatures should read
as "operate on data of this shape" rather than "operate on a Voucher/GL/SubLedger".

## Already-correct examples in this codebase (imitate these)

- `com.gsl.utils.io.XlsxWriter` / `ExportedExcelReader` — genuinely generic, explicitly
  documented as reusable, zero business knowledge.
- `com.gsl.pages.enums.Finance.Operations.Common.IVoucherListColumn` / `ColumnDataType`
  (generic contract) vs. `CommonVoucherListColumn` / `GeneralVoucherListColumn` (domain enums)
  — `GridColumnMenu` (the grid column-header-menu utility) deliberately follows this pattern:
  it operates on the *interface* `IVoucherListColumn`, never on a concrete domain enum directly.
- `com.gsl.api.core.ApiClient` (generic contract) vs. `com.gsl.api.client.ApVoucherApiClient`/
  etc. (domain implementations), with `api/model/common` vs. `api/model/<domain>` mirroring the
  same split.
- `com.gsl.database.dao` (generic DAOs) vs. `com.gsl.database.dao.finance`/`adm`/`inventory`
  (domain-nested DAOs) — a folder-level version of the same split.
- `com.gsl.utils.dataprovider.source.TestDataSource<T>` — a pure one-method generic contract
  (`List<T> load(String testMethodName)`), correctly domain-agnostic even though every
  implementation of it (`Excel*DataSource`/`Db*DataSource`) is domain-specific.

## Case study: `OptionalColumnMerger`

`ExcelApVoucherDataSource` and `ExcelGeneralVoucherDataSource` each needed to read one Excel
column that `XlsxReader`/ZeroCell can't safely map via `@Column` (it would hard-fail whenever
that header is absent from an older sheet). Three near-identical ~60-line private methods
accumulated independently — same mechanic (open workbook via raw POI, resolve header indices
by name, build a lookup keyed by 1-2 columns, merge into a domain list via a setter), only the
sheet/column names, key shape, and setter differing. Even the `cellStr(Cell)` helper was
copy-pasted verbatim in both files.

This was extracted into `com.gsl.utils.io.OptionalColumnMerger` — `mergeByOneKey(...)` /
`mergeByTwoKeys(...)` — taking the domain list, key/value column names, and `Function`/
`BiConsumer` extractors as parameters. Zero domain imports. The three call sites became
one-liners; behavior is unchanged (pure extraction).

## Checklist for adding a new utility

1. Would this class need to change if a business rule changed? If yes, put it next to the
   domain code it serves (e.g. `com.gsl.pages.enums.Finance.Operations.<Domain>`,
   `com.gsl.testdata.Finance.Operations.Vouchers.<Domain>`, `com.gsl.utils.dataprovider.source.<domain>`).
2. If no, put it in a generic package (`com.gsl.utils.io`, `com.gsl.utils.Selenium`, etc.) and
   check its method signatures contain no domain types.
3. Before writing new mechanics, check whether a near-identical shape already exists elsewhere
   (copy-pasted with only names/keys differing) — that's usually a sign the mechanic should be
   extracted once, generically, the way `OptionalColumnMerger` was.
