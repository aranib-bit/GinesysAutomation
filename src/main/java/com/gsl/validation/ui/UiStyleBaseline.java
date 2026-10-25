package com.gsl.validation.ui;

import com.gsl.config.ConfigFactory;
import com.gsl.utils.Log;
import com.gsl.utils.io.ExcelSheetReader;
import com.gsl.utils.ui.FieldStyleSpec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The expected UI style values (font size/family/weight, text/background/border colour), read from
 * the baseline workbook ({@code exceldata.properties → uiStyleBaselinePath}) instead of being
 * hardcoded in enums — edit the workbook, re-run, no code change.
 *
 * <p><b>Workbook shape.</b> Sheets {@code Common} (all vouchers) plus one per voucher ({@code GV},
 * {@code AR}, {@code AP}, ...) with identical columns: {@code Spec | Component | FontSize |
 * FontFamily | FontWeight | Color | BackgroundColor | BorderColor | Note}. A row with {@code Spec}
 * filled is that style role's default; a row with {@code Component} filled overrides ONE component
 * ({@code *} wildcards allowed, e.g. {@code GRID_CELL_*_ROW_*}).
 *
 * <p><b>Resolution, per property, first match wins:</b> current-voucher sheet Component row →
 * current-voucher sheet Spec row → Common Component row → Common Spec row. A blank cell in a
 * voucher sheet inherits; a blank cell in Common means "not checked"; {@code -} anywhere means
 * "explicitly not checked". If nothing defines the spec at all the result is {@link Kind#MISSING}
 * (the check reports a visible SKIP — never a silent pass). The current voucher comes from
 * {@link UiStyleScope}.
 *
 * <p>The file is read from {@code ${user.dir}/src/test/resources/<path>} first (so an edit applies
 * on the next run without a rebuild), the classpath second, once per JVM. A missing or unreadable
 * workbook is logged loudly and every lookup then resolves to {@link Kind#MISSING}.
 */
public final class UiStyleBaseline {

    private UiStyleBaseline() {}

    public enum Kind { VALUE, NOT_CHECKED, MISSING }

    /** @param value the expected CSS value when {@code kind == VALUE}, else {@code null} */
    public record Resolved(Kind kind, String value) {
        static final Resolved NOT_CHECKED = new Resolved(Kind.NOT_CHECKED, null);
        static final Resolved MISSING = new Resolved(Kind.MISSING, null);
    }

    /** Property keys, exactly as the report and {@code UiComponentValidator} name them. */
    public static final String FONT_SIZE = "font-size";
    public static final String FONT_FAMILY = "font-family";
    public static final String FONT_WEIGHT = "font-weight";
    public static final String COLOR = "color";
    public static final String BACKGROUND = "background-color";
    public static final String BORDER = "border-color";

    private static final Map<String, String> PROPERTY_BY_HEADER = Map.of(
            "fontsize", FONT_SIZE, "fontfamily", FONT_FAMILY, "fontweight", FONT_WEIGHT,
            "color", COLOR, "backgroundcolor", BACKGROUND, "bordercolor", BORDER);

    /** Behaviour attributes (non-style) a component row may carry, keyed as the {@code attribute(...)} callers name them. */
    public static final String ELEMENT_TYPE = "element-type";
    public static final String MANDATORY = "mandatory";
    public static final String ENABLED_BEFORE_FILL = "enabled-before-fill";
    public static final String ENABLED_AFTER_FILL = "enabled-after-fill";
    /** Heading text shown for the component in the report ("Cash/Bank" instead of "Cash Bank"). */
    public static final String DISPLAY_NAME = "display-name";

    private static final Map<String, String> ATTRIBUTE_HEADERS = Map.of(
            ELEMENT_TYPE, "ElementType", MANDATORY, "Mandatory",
            ENABLED_BEFORE_FILL, "EnabledBeforeFill", ENABLED_AFTER_FILL, "EnabledAfterFill",
            DISPLAY_NAME, "DisplayName");

    private static final String COMMON = "Common";

    private record ComponentRow(Pattern pattern, int literalLength, Map<String, String> cells) {}

    private record SheetData(Map<String, Map<String, String>> specs, List<ComponentRow> components) {}

    private record Data(Map<String, SheetData> sheets, String source, boolean loaded) {}

    private static volatile Data data;

    private static Data data() {
        Data d = data;
        if (d == null) {
            synchronized (UiStyleBaseline.class) {
                d = data;
                if (d == null) {
                    d = load();
                    data = d;
                }
            }
        }
        return d;
    }

    /** Forces the next lookup to re-read the workbook (e.g. after editing it inside one JVM). */
    public static synchronized void reload() {
        data = null;
    }

    // ── Lookup ────────────────────────────────────────────────────────────────────────────────

    /**
     * The expected value of {@code property} for a check on {@code component} using style role
     * {@code spec} — see the class Javadoc for the resolution order. {@code component} may be null.
     */
    public static Resolved resolve(FieldStyleSpec spec, String component, String property) {
        Data d = data();
        if (!d.loaded()) return Resolved.MISSING;

        List<String> order = new ArrayList<>(2);
        String voucher = UiStyleScope.sheetName();
        if (voucher != null && d.sheets().containsKey(voucher)) order.add(voucher);
        order.add(COMMON);

        for (String sheetName : order) {
            SheetData sheet = d.sheets().get(sheetName);
            if (sheet == null) continue;
            boolean common = COMMON.equalsIgnoreCase(sheetName);

            if (component != null) {
                for (ComponentRow row : matching(sheet, component)) {
                    String cell = row.cells().get(property);
                    if (cell == null || cell.isEmpty()) continue;
                    return "-".equals(cell) ? Resolved.NOT_CHECKED : new Resolved(Kind.VALUE, cell);
                }
            }

            Map<String, String> specCells = sheet.specs().get(spec.name());
            if (specCells != null) {
                String cell = specCells.get(property);
                if (cell != null && !cell.isEmpty()) {
                    return "-".equals(cell) ? Resolved.NOT_CHECKED : new Resolved(Kind.VALUE, cell);
                }
                if (common) return Resolved.NOT_CHECKED;
            }
        }
        return Resolved.MISSING;
    }

    /**
     * A behaviour attribute (e.g. {@link #ELEMENT_TYPE}, {@link #MANDATORY}) for {@code component}:
     * current-voucher sheet Component row, then Common Component row, first non-blank wins.
     * Returns {@code null} when nothing is baselined or the cell is {@code -} ("not checked").
     */
    public static String attribute(String component, String key) {
        Data d = data();
        if (!d.loaded() || component == null) return null;
        List<String> order = new ArrayList<>(2);
        String voucher = UiStyleScope.sheetName();
        if (voucher != null && d.sheets().containsKey(voucher)) order.add(voucher);
        order.add(COMMON);
        for (String sheetName : order) {
            SheetData sheet = d.sheets().get(sheetName);
            if (sheet == null) continue;
            for (ComponentRow row : matching(sheet, component)) {
                String cell = row.cells().get(key);
                if (cell == null || cell.isEmpty()) continue;
                return "-".equals(cell) ? null : cell;
            }
        }
        return null;
    }

    /** Component rows of {@code sheet} matching {@code component}, most specific (longest literal part) first. */
    private static List<ComponentRow> matching(SheetData sheet, String component) {
        List<ComponentRow> hits = new ArrayList<>();
        for (ComponentRow row : sheet.components()) {
            if (row.pattern().matcher(component).matches()) hits.add(row);
        }
        hits.sort(Comparator.comparingInt(ComponentRow::literalLength).reversed());
        return hits;
    }

    /** One-line description for the report summary, e.g. {@code "Excel: Finance/UiStyleBaseline.xlsx — sheets Common+GV".} */
    public static String describe() {
        Data d = data();
        if (!d.loaded()) return "Excel baseline NOT loaded (" + d.source() + ") — style checks report SKIP";
        String voucher = UiStyleScope.sheetName();
        boolean hasVoucherSheet = voucher != null && d.sheets().containsKey(voucher);
        return "Excel: " + d.source() + " — sheets " + COMMON + (hasVoucherSheet ? "+" + voucher : "");
    }

    // ── Loading ───────────────────────────────────────────────────────────────────────────────

    private static Data load() {
        String path = ConfigFactory.getExcelDataConfig().uiStyleBaselinePath();
        Map<String, List<Map<String, String>>> raw = null;
        String source = path;

        Path file = Path.of(System.getProperty("user.dir"), "src", "test", "resources", path);
        try {
            if (Files.exists(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    raw = ExcelSheetReader.readAll(in);
                }
                source = path + " (file)";
            } else {
                try (InputStream in = UiStyleBaseline.class.getClassLoader().getResourceAsStream(path)) {
                    if (in != null) {
                        raw = ExcelSheetReader.readAll(in);
                        source = path + " (classpath)";
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.error("[UiStyleBaseline] Failed to read '{}': {}", path, e.getMessage(), e);
            return new Data(Map.of(), path + " unreadable: " + e.getMessage(), false);
        }
        if (raw == null) {
            Log.error("[UiStyleBaseline] Baseline workbook '{}' not found (looked in src/test/resources and on "
                    + "the classpath) — every UI style check will report SKIP until it exists.", path);
            return new Data(Map.of(), path + " not found", false);
        }

        Map<String, SheetData> sheets = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        int specRows = 0;
        int componentRows = 0;
        for (Map.Entry<String, List<Map<String, String>>> sheet : raw.entrySet()) {
            if (!hasColumns(sheet.getValue())) continue; // ReadMe / free-text sheets
            Map<String, Map<String, String>> specs = new LinkedHashMap<>();
            List<ComponentRow> components = new ArrayList<>();
            for (Map<String, String> row : sheet.getValue()) {
                Map<String, String> cells = new LinkedHashMap<>();
                for (Map.Entry<String, String> header : PROPERTY_BY_HEADER.entrySet()) {
                    String value = row.getOrDefault(headerName(header.getKey()), "").trim();
                    cells.put(header.getValue(), value);
                }
                for (Map.Entry<String, String> attr : ATTRIBUTE_HEADERS.entrySet()) {
                    cells.put(attr.getKey(), row.getOrDefault(attr.getValue(), "").trim());
                }
                String specName = row.getOrDefault("Spec", "").trim();
                String componentName = row.getOrDefault("Component", "").trim();
                if (!specName.isEmpty()) {
                    if (!isKnownSpec(specName)) {
                        Log.warn("[UiStyleBaseline] Sheet '{}': unknown Spec '{}' ignored.", sheet.getKey(), specName);
                    } else {
                        specs.put(specName.toUpperCase(), cells);
                        specRows++;
                    }
                } else if (!componentName.isEmpty()) {
                    components.add(new ComponentRow(wildcard(componentName), literalLength(componentName), cells));
                    componentRows++;
                }
            }
            sheets.put(sheet.getKey(), new SheetData(specs, components));
        }
        if (!sheets.containsKey(COMMON)) {
            Log.error("[UiStyleBaseline] Workbook '{}' has no '{}' sheet — every UI style check will report SKIP.",
                    path, COMMON);
            return new Data(sheets, path + " has no Common sheet", false);
        }
        Log.info("[UiStyleBaseline] Loaded '{}': sheets {}, {} spec row(s), {} component override row(s).",
                source, sheets.keySet(), specRows, componentRows);
        return new Data(sheets, source, true);
    }

    private static boolean hasColumns(List<Map<String, String>> rows) {
        return !rows.isEmpty() && rows.get(0).containsKey("Spec") && rows.get(0).containsKey("Component");
    }

    private static String headerName(String normalised) {
        return switch (normalised) {
            case "fontsize" -> "FontSize";
            case "fontfamily" -> "FontFamily";
            case "fontweight" -> "FontWeight";
            case "color" -> "Color";
            case "backgroundcolor" -> "BackgroundColor";
            default -> "BorderColor";
        };
    }

    private static boolean isKnownSpec(String name) {
        for (FieldStyleSpec s : FieldStyleSpec.values()) {
            if (s.name().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static Pattern wildcard(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (String part : pattern.split("\\*", -1)) {
            if (regex.length() > 0) regex.append(".*");
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    private static int literalLength(String pattern) {
        return pattern.replace("*", "").length();
    }
}
