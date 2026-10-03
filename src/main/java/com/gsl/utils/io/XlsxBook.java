package com.gsl.utils.io;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.ComparisonOperator;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.FontFormatting;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PatternFormatting;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFFontFormatting;
import org.apache.poi.xssf.usermodel.XSSFHyperlink;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFSheetConditionalFormatting;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain-free, formatted-report builder over Apache POI's {@link XSSFWorkbook}: RGB styles
 * (cached), typed cells (numbers stay numeric, real dates, hyperlinks), merged banners, KPI tiles,
 * data tables with dark header / zebra / borders / wrap / widths / freeze / autofilter, and
 * conditional formatting. Charts and pivot tables live in {@link XlsxCharts} / {@link XlsxPivots}
 * and work on {@link #raw()}.
 *
 * <p>Mechanics only - takes primitives, strings, collections and the small value types of this
 * package; it never sees a domain object (see {@code docs/conventions/utility-layering.md}). The
 * older plain-string {@link XlsxWriter#write} overloads are unrelated and untouched.
 */
public final class XlsxBook implements AutoCloseable {

    /** Default dark table header. */
    public static final XlsxStyle HEADER = XlsxStyle.builder().bold(true).fontRgb("FFFFFF").fillRgb("1F3864")
            .border(true).borderRgb("1F3864").wrap(true).align(XlsxStyle.Align.CENTER).build();

    private final XSSFWorkbook wb = new XSSFWorkbook();
    private final Map<XlsxStyle, XSSFCellStyle> styles = new HashMap<>();
    private final Map<String, XSSFFont> fonts = new HashMap<>();

    /** Options for {@link #writeTable}. */
    public static final class TableOptions {
        public boolean freeze = true;
        public boolean autoFilter = true;
        public boolean zebra = true;
        public String zebraRgb = "F3F6FB";
        public XlsxStyle headerStyle = HEADER;
        public float headerHeight = 32f;
        /** Freeze this many leading columns too (0 = none). */
        public int freezeColumns = 0;
    }

    // -- structure ---------------------------------------------------------------------------

    /** The underlying workbook, for {@link XlsxCharts}/{@link XlsxPivots} and anything not wrapped here. */
    public XSSFWorkbook raw() {
        return wb;
    }

    /** Creates a sheet with an optional tab colour ({@code RRGGBB}); gridlines off gives the clean "report" look. */
    public XSSFSheet sheet(String name, String tabRgb, boolean showGridlines) {
        XSSFSheet s = wb.createSheet(name);
        if (tabRgb != null) s.setTabColor(color(tabRgb));
        s.setDisplayGridlines(showGridlines);
        s.getPrintSetup().setLandscape(true);
        s.setFitToPage(true);
        s.getPrintSetup().setFitWidth((short) 1);
        s.getPrintSetup().setFitHeight((short) 0);
        return s;
    }

    public void setWidths(XSSFSheet s, int... charWidths) {
        for (int i = 0; i < charWidths.length; i++) {
            s.setColumnWidth(i, Math.min(255, Math.max(2, charWidths[i])) * 256);
        }
    }

    // -- styles ------------------------------------------------------------------------------

    public static XSSFColor color(String rgb) {
        int v = Integer.parseInt(rgb.replace("#", ""), 16);
        return new XSSFColor(new byte[]{(byte) (v >> 16), (byte) (v >> 8), (byte) v}, null);
    }

    /** Cached POI style for {@code s}; {@code null} yields the plain default style. */
    public XSSFCellStyle style(XlsxStyle s) {
        XlsxStyle key = s == null ? XlsxStyle.builder().build() : s;
        XSSFCellStyle cached = styles.get(key);
        if (cached == null) {
            cached = create(key);
            styles.put(key, cached);
        }
        return cached;
    }

    private XSSFCellStyle create(XlsxStyle s) {
        XSSFCellStyle cs = wb.createCellStyle();
        cs.setFont(font(s));
        if (s.getFillRgb() != null) {
            cs.setFillForegroundColor(color(s.getFillRgb()));
            cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        if (s.getNumFmt() != null) {
            cs.setDataFormat(wb.createDataFormat().getFormat(s.getNumFmt()));
        }
        cs.setWrapText(s.isWrap());
        cs.setAlignment(switch (s.getAlign()) {
            case LEFT -> HorizontalAlignment.LEFT;
            case CENTER -> HorizontalAlignment.CENTER;
            case RIGHT -> HorizontalAlignment.RIGHT;
            case GENERAL -> HorizontalAlignment.GENERAL;
        });
        cs.setVerticalAlignment(s.isVCenter() ? VerticalAlignment.CENTER : VerticalAlignment.TOP);
        if (s.isBorder()) {
            XSSFColor bc = color(s.getBorderRgb());
            cs.setBorderTop(BorderStyle.THIN);
            cs.setBorderBottom(BorderStyle.THIN);
            cs.setBorderLeft(BorderStyle.THIN);
            cs.setBorderRight(BorderStyle.THIN);
            cs.setTopBorderColor(bc);
            cs.setBottomBorderColor(bc);
            cs.setLeftBorderColor(bc);
            cs.setRightBorderColor(bc);
        }
        return cs;
    }

    private XSSFFont font(XlsxStyle s) {
        String key = s.getFontRgb() + "|" + s.isBold() + "|" + s.isItalic() + "|" + s.isUnderline() + "|" + s.getFontSize();
        XSSFFont f = fonts.get(key);
        if (f == null) {
            f = wb.createFont();
            f.setBold(s.isBold());
            f.setItalic(s.isItalic());
            if (s.isUnderline()) f.setUnderline(org.apache.poi.ss.usermodel.Font.U_SINGLE);
            if (s.getFontSize() > 0) f.setFontHeightInPoints((short) s.getFontSize());
            if (s.getFontRgb() != null) f.setColor(color(s.getFontRgb()));
            fonts.put(key, f);
        }
        return f;
    }

    // -- cells -------------------------------------------------------------------------------

    private static Cell cell(XSSFSheet s, int r, int c) {
        Row row = s.getRow(r);
        if (row == null) row = s.createRow(r);
        Cell cell = row.getCell(c);
        return cell != null ? cell : row.createCell(c);
    }

    /** Writes {@code v} typed by its Java type (null = blank). */
    public Cell put(XSSFSheet s, int r, int c, Object v, XlsxStyle st) {
        Cell cell = cell(s, r, c);
        setValue(cell, v);
        cell.setCellStyle(style(st));
        return cell;
    }

    private void setValue(Cell cell, Object v) {
        if (v == null) {
            cell.setBlank();
        } else if (v instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (v instanceof LocalDateTime t) {
            cell.setCellValue(t);
        } else if (v instanceof Instant i) {
            cell.setCellValue(LocalDateTime.ofInstant(i, ZoneId.systemDefault()));
        } else if (v instanceof Boolean b) {
            cell.setCellValue(b);
        } else if (v instanceof XlsxLink link) {
            setLink(cell, link);
        } else {
            String text = v.toString();
            // Excel's hard cap is 32,767 chars per cell.
            cell.setCellValue(text.length() > 32000 ? text.substring(0, 32000) + "..." : text);
        }
    }

    private void setLink(Cell cell, XlsxLink link) {
        cell.setCellValue(link.text() == null ? "" : link.text());
        String t = link.target();
        if (t == null || t.isBlank()) return;
        boolean absolute = t.matches("(?i)^(https?|file|mailto):.*");
        XSSFHyperlink h = (XSSFHyperlink) wb.getCreationHelper()
                .createHyperlink(absolute ? HyperlinkType.URL : HyperlinkType.FILE);
        h.setAddress(absolute ? t : t.replace('\\', '/'));
        cell.setHyperlink(h);
    }

    /** Excel formula cell (no leading {@code =}). */
    public Cell formula(XSSFSheet s, int r, int c, String formula, XlsxStyle st) {
        Cell cell = cell(s, r, c);
        cell.setCellFormula(formula);
        cell.setCellStyle(style(st));
        return cell;
    }

    /** Merges {@code r1..r2 x c1..c2}, styles every cell of the region (so borders/fill render) and writes {@code v} in the top-left. */
    public void merged(XSSFSheet s, int r1, int c1, int r2, int c2, Object v, XlsxStyle st) {
        for (int r = r1; r <= r2; r++) {
            for (int c = c1; c <= c2; c++) {
                put(s, r, c, r == r1 && c == c1 ? v : null, st);
            }
        }
        if (r1 != r2 || c1 != c2) s.addMergedRegion(new CellRangeAddress(r1, r2, c1, c2));
    }

    public void rowHeight(XSSFSheet s, int r, float points) {
        Row row = s.getRow(r);
        if (row == null) row = s.createRow(r);
        row.setHeightInPoints(points);
    }

    /** KPI tile: a small label over a large value, merged across {@code span} columns on a tinted fill. */
    public void kpiTile(XSSFSheet s, int row, int col, int span, String label, Object value, String numFmt,
                        String fillRgb, String valueFontRgb) {
        XlsxStyle lab = XlsxStyle.builder().fillRgb(fillRgb).fontRgb("595959").fontSize(9).bold(true)
                .align(XlsxStyle.Align.CENTER).border(true).borderRgb("FFFFFF").build();
        XlsxStyle val = XlsxStyle.builder().fillRgb(fillRgb).fontRgb(valueFontRgb == null ? "1F3864" : valueFontRgb)
                .fontSize(20).bold(true).align(XlsxStyle.Align.CENTER).numFmt(numFmt).border(true).borderRgb("FFFFFF").build();
        merged(s, row, col, row, col + span - 1, label, lab);
        merged(s, row + 1, col, row + 1, col + span - 1, value, val);
        rowHeight(s, row, 16f);
        rowHeight(s, row + 1, 34f);
    }

    // -- tables ------------------------------------------------------------------------------

    /**
     * Writes a header row plus {@code rows} starting at ({@code startRow}, {@code startCol}) with
     * the dark header, borders, zebra banding, per-column format/wrap/alignment and widths, and
     * (per {@code opts}) a frozen header and autofilter.
     *
     * @return {@code {firstDataRow, lastDataRow}} (0-based; {@code lastDataRow < firstDataRow} when empty)
     */
    public int[] writeTable(XSSFSheet s, int startRow, int startCol, List<XlsxColumn> cols, List<Object[]> rows,
                            TableOptions opts) {
        TableOptions o = opts == null ? new TableOptions() : opts;
        for (int c = 0; c < cols.size(); c++) {
            put(s, startRow, startCol + c, cols.get(c).getHeader(), o.headerStyle);
            s.setColumnWidth(startCol + c, Math.min(255, Math.max(2, cols.get(c).getWidth())) * 256);
        }
        rowHeight(s, startRow, o.headerHeight);
        Map<String, XlsxStyle> cache = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Object[] values = rows.get(i);
            boolean banded = o.zebra && i % 2 == 1;
            for (int c = 0; c < cols.size(); c++) {
                XlsxColumn col = cols.get(c);
                Object v = c < values.length ? values[c] : null;
                final boolean isLink = v instanceof XlsxLink;
                String key = c + "|" + banded + "|" + isLink;
                XlsxStyle st = cache.get(key);
                if (st == null) {
                    st = XlsxStyle.builder()
                            .numFmt(col.getKind().format)
                            .wrap(col.isWrap())
                            .vCenter(!col.isWrap())
                            .align(col.isCenter() ? XlsxStyle.Align.CENTER
                                    : (col.getKind() == XlsxColumn.Kind.TEXT || col.getKind() == XlsxColumn.Kind.LINK
                                            ? XlsxStyle.Align.LEFT : XlsxStyle.Align.RIGHT))
                            .border(true)
                            .fillRgb(banded ? o.zebraRgb : null)
                            .fontRgb(isLink ? "0563C1" : null)
                            .underline(isLink)
                            .build();
                    cache.put(key, st);
                }
                put(s, startRow + 1 + i, startCol + c, v, st);
            }
        }
        int first = startRow + 1;
        int last = startRow + rows.size();
        if (o.autoFilter && !cols.isEmpty()) {
            s.setAutoFilter(new CellRangeAddress(startRow, Math.max(last, startRow), startCol, startCol + cols.size() - 1));
        }
        if (o.freeze) {
            s.createFreezePane(o.freezeColumns, startRow + 1);
        }
        return new int[]{first, last};
    }

    // -- conditional formatting --------------------------------------------------------------

    /** Highlights cells of {@code range} whose text equals {@code text} with fill/font colours. */
    public void highlightEquals(XSSFSheet s, CellRangeAddress range, String text, String fillRgb, String fontRgb) {
        XSSFSheetConditionalFormatting scf = s.getSheetConditionalFormatting();
        ConditionalFormattingRule rule = scf.createConditionalFormattingRule(ComparisonOperator.EQUAL,
                "\"" + text.replace("\"", "\"\"") + "\"");
        if (fillRgb != null) {
            PatternFormatting pf = rule.createPatternFormatting();
            pf.setFillBackgroundColor(color(fillRgb));
            pf.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
        }
        if (fontRgb != null) {
            FontFormatting ff = rule.createFontFormatting();
            ((XSSFFontFormatting) ff).setFontColor(color(fontRgb));
            ff.setFontStyle(false, true);
        }
        scf.addConditionalFormatting(new CellRangeAddress[]{range}, rule);
    }

    /** Excel data bars over a numeric range. */
    public void dataBars(XSSFSheet s, CellRangeAddress range, String rgb) {
        XSSFSheetConditionalFormatting scf = s.getSheetConditionalFormatting();
        scf.addConditionalFormatting(new CellRangeAddress[]{range}, scf.createConditionalFormattingRule(color(rgb)));
    }

    // -- output ------------------------------------------------------------------------------

    /** Makes Excel recompute every formula on open (POI writes no cached values for formulas it did not evaluate). */
    public void recalcOnOpen() {
        wb.setForceFormulaRecalculation(true);
    }

    /** Evaluates all formulas now so cached values exist for viewers that do not recalculate; failures are ignored. */
    public void evaluateAllQuietly() {
        try {
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
        } catch (Exception ignored) {
            // recalcOnOpen() still makes Excel compute them
        }
    }

    public void write(Path out) throws IOException {
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        try (OutputStream os = Files.newOutputStream(out)) {
            wb.write(os);
        }
    }

    @Override
    public void close() throws IOException {
        wb.close();
    }
}
