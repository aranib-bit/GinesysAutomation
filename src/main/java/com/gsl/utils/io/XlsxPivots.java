package com.gsl.utils.io;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.DataConsolidateFunction;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFPivotTable;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import java.util.List;

/**
 * Real (XSSF) pivot-table helper - domain-free. Creates a pivot table that reads a header+data
 * range of another sheet and is flagged refresh-on-load, so Excel (re)builds the layout and values
 * itself when the workbook is opened (POI writes only the definition, not the cached records).
 *
 * <p>Apache POI 5.1.0 cannot create PivotCharts; chart the pivot's source data with
 * {@link XlsxCharts} instead.
 */
public final class XlsxPivots {

    private XlsxPivots() {}

    /**
     * Adds a count-style pivot table.
     *
     * @param target       sheet that receives the pivot
     * @param anchorRow    0-based top-left row of the pivot on {@code target}
     * @param anchorCol    0-based top-left column of the pivot on {@code target}
     * @param source       sheet holding the data
     * @param headerRow    0-based row of the source header (first row of the pivot source range)
     * @param lastRow      0-based last data row of the source
     * @param firstCol     0-based first source column
     * @param lastCol      0-based last source column
     * @param rowFields    source column indexes (absolute) placed on the row axis, outermost first
     * @param colFields    source column indexes placed on the column axis
     * @param filterFields source column indexes offered as report filters
     * @param countField   source column index whose non-blank cells are counted
     * @param valueTitle   caption of the count value field
     */
    public static XSSFPivotTable countPivot(XSSFSheet target, int anchorRow, int anchorCol, XSSFSheet source,
                                            int headerRow, int lastRow, int firstCol, int lastCol,
                                            List<Integer> rowFields, List<Integer> colFields,
                                            List<Integer> filterFields, int countField, String valueTitle) {
        String ref = "'" + source.getSheetName().replace("'", "''") + "'!"
                + new CellReference(headerRow, firstCol, true, true).formatAsString(false)
                + ":" + new CellReference(lastRow, lastCol, true, true).formatAsString(false);
        // formatAsString(false) drops a sheet prefix we do not have; the explicit prefix above carries it.
        AreaReference area = new AreaReference(ref, SpreadsheetVersion.EXCEL2007);
        XSSFPivotTable pt = target.createPivotTable(area, new CellReference(anchorRow, anchorCol), source);
        // POI numbers pivot fields from the START of the source area, not from column A.
        for (int f : filterFields) pt.addReportFilter(f - firstCol);
        for (int f : rowFields) pt.addRowLabel(f - firstCol);
        for (int f : colFields) pt.addColLabel(f - firstCol);
        pt.addColumnLabel(DataConsolidateFunction.COUNT, countField - firstCol, valueTitle);
        // Excel rebuilds layout + values on open (no cache records are written by POI).
        pt.getPivotCacheDefinition().getCTPivotCacheDefinition().setRefreshOnLoad(true);
        return pt;
    }
}
