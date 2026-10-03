package com.gsl.utils.io;

import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xddf.usermodel.XDDFColor;
import org.apache.poi.xddf.usermodel.XDDFLineProperties;
import org.apache.poi.xddf.usermodel.XDDFShapeProperties;
import org.apache.poi.xddf.usermodel.XDDFSolidFillProperties;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.BarGrouping;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import java.util.List;

/**
 * Native (XDDF) bar-chart helper for {@link XlsxBook} workbooks - domain-free: a chart is just a
 * title, a category range, and one or more numeric series ranges, all addressed as cell ranges so
 * the chart stays bound to the sheet data.
 *
 * <p>Apache POI 5.1.0 cannot create Excel PivotCharts (a chart bound to a pivot table); these are
 * ordinary charts over ordinary cell ranges.
 */
public final class XlsxCharts {

    private XlsxCharts() {}

    /** One numeric series: legend name (taken from {@code nameCell}), value range and bar colour. */
    public record Series(String name, CellRangeAddress values, String rgb) {}

    /**
     * Adds a bar/column chart to {@code host}.
     *
     * @param host        sheet that displays the chart
     * @param anchor      {@code {col1,row1,col2,row2}} cell box on {@code host} (0-based, exclusive end)
     * @param data        sheet holding the category and series ranges (may be {@code host})
     * @param categories  single-column or single-row range of category labels
     * @param stacked     {@code true} = stacked columns, else clustered
     * @param horizontal  {@code true} = horizontal bars
     */
    public static void bar(XSSFSheet host, int[] anchor, String title, XSSFSheet data, CellRangeAddress categories,
                           List<Series> series, boolean stacked, boolean horizontal) {
        XSSFDrawing drawing = host.createDrawingPatriarch();
        XSSFClientAnchor a = drawing.createAnchor(0, 0, 0, 0, anchor[0], anchor[1], anchor[2], anchor[3]);
        XSSFChart chart = drawing.createChart(a);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);
        chart.getOrAddLegend().setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis cat = chart.createCategoryAxis(horizontal ? AxisPosition.LEFT : AxisPosition.BOTTOM);
        XDDFValueAxis val = chart.createValueAxis(horizontal ? AxisPosition.BOTTOM : AxisPosition.LEFT);
        val.setCrosses(AxisCrosses.AUTO_ZERO);
        val.setNumberFormat("0");
        val.setMinimum(0);
        // light grid instead of the heavy default look
        val.getOrAddMajorGridProperties().setLineProperties(
                new XDDFLineProperties(new XDDFSolidFillProperties(XDDFColor.from(new byte[]{(byte) 0xE0, (byte) 0xE0, (byte) 0xE0}))));

        XDDFBarChartData bars = (XDDFBarChartData) chart.createData(ChartTypes.BAR, cat, val);
        bars.setBarDirection(horizontal ? BarDirection.BAR : BarDirection.COL);
        bars.setBarGrouping(stacked ? BarGrouping.STACKED : BarGrouping.CLUSTERED);
        bars.setGapWidth(60);


        XDDFDataSource<String> cats = XDDFDataSourcesFactory.fromStringCellRange(data, categories);
        for (Series s : series) {
            XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory.fromNumericCellRange(data, s.values());
            XDDFBarChartData.Series ser = (XDDFBarChartData.Series) bars.addSeries(cats, vals);
            ser.setTitle(s.name(), new CellReference(data.getSheetName(), s.values().getFirstRow() - 1,
                    s.values().getFirstColumn(), true, true));
            XDDFShapeProperties props = new XDDFShapeProperties();
            props.setFillProperties(new XDDFSolidFillProperties(XDDFColor.from(rgbBytes(s.rgb()))));
            ser.setShapeProperties(props);
        }
        chart.plot(bars);
        if (stacked) {
            stackOnTop(chart);
        }
    }

    /**
     * Writes {@code <c:overlap val="100"/>} so stacked series sit on top of each other instead of
     * side by side. {@code XDDFBarChartData#setOverlap} is not usable with the slim {@code
     * poi-ooxml-lite} schema jar (its typed setter needs a schema type that jar omits), so the
     * attribute is added with an XML cursor, which needs no schema type. Non-fatal: if it fails the
     * chart is still valid, just less tidy.
     */
    private static void stackOnTop(XSSFChart chart) {
        try {
            org.openxmlformats.schemas.drawingml.x2006.chart.CTBarChart ct =
                    chart.getCTChart().getPlotArea().getBarChartArray(0);
            org.openxmlformats.schemas.drawingml.x2006.chart.CTOverlap ov = ct.addNewOverlap();
            org.apache.xmlbeans.XmlCursor cur = ov.newCursor();
            try {
                cur.toFirstContentToken();
                cur.insertAttributeWithValue("val", "100");
            } finally {
                cur.dispose();
            }
        } catch (Throwable t) {
            com.gsl.utils.Log.warn("[XlsxCharts] stacked overlap not applied: {}", t.toString());
        }
    }

    private static byte[] rgbBytes(String rgb) {
        int v = Integer.parseInt(rgb.replace("#", ""), 16);
        return new byte[]{(byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }
}
