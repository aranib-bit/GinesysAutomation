package com.gsl.utils.dataprovider.fixture.finance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The execution-group/tag filters that were in effect for a run - the "why was this combination
 * not run?" half of the coverage workbook. Reads the same two-source priority the data sources
 * use (system property first, then the TestNG XML {@code <parameter>}):
 * {@code gv.group}/{@code gv.tag} (via {@link GeneralVoucherCombinationFilter}), {@code gv.edit.group}
 * (via {@link GeneralVoucherEditCombinationFilter}), {@code arv.group}, {@code apv.group} and
 * {@code apv.tag}. Domain layer (it knows what the filters mean); the workbook writer stays generic.
 *
 * <p>{@link #isSelected(CoverageRow)} answers "would the data source have emitted this catalog
 * row under these filters?". Rows with group {@link CoverageRow#GROUP_ALWAYS} or {@code VARIES} are
 * never filtered out. The {@code apv.tag} filter cannot be evaluated per catalog row (the catalog
 * carries no AP tags), so it is reported in {@link #describe()} but not applied.
 */
public final class ActiveGroupFilters {

    private final String gvGroup;
    private final Set<String> gvTags;
    private final String gvEditGroup;
    private final String arvGroup;
    private final String apvGroup;
    private final Set<String> apvTags;

    private Map<String, GeneralVoucherVariant> gvVariants;

    public ActiveGroupFilters(String gvGroup, Set<String> gvTags, String gvEditGroup, String arvGroup,
                              String apvGroup, Set<String> apvTags) {
        this.gvGroup = blankToNull(gvGroup);
        this.gvTags = gvTags == null ? Set.of() : new TreeSet<>(gvTags);
        this.gvEditGroup = blankToNull(gvEditGroup);
        this.arvGroup = blankToNull(arvGroup);
        this.apvGroup = blankToNull(apvGroup);
        this.apvTags = apvTags == null ? Set.of() : new TreeSet<>(apvTags);
    }

    /** No filters at all - every catalog row counts as selected. */
    public static ActiveGroupFilters none() {
        return new ActiveGroupFilters(null, null, null, null, null, null);
    }

    /**
     * Resolves the filters from system properties, falling back to {@code xmlParam} (name to TestNG
     * XML parameter value; may return {@code null}). Never throws.
     */
    public static ActiveGroupFilters resolve(Function<String, String> xmlParam) {
        Function<String, String> xml = xmlParam == null ? k -> null : xmlParam;
        String gvGroup;
        try {
            var g = GeneralVoucherCombinationFilter.resolveActiveGroup(xml.apply("gv.group"));
            gvGroup = g == null ? null : g.name();
        } catch (RuntimeException e) {
            gvGroup = raw("gv.group", xml);   // unknown value: report it as written
        }
        Set<String> gvTags;
        try {
            gvTags = GeneralVoucherCombinationFilter.resolveActiveTags(xml.apply("gv.tag")).stream()
                    .map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
        } catch (RuntimeException e) {
            gvTags = csv(raw("gv.tag", xml));
        }
        String gvEdit;
        try {
            var g = GeneralVoucherEditCombinationFilter.resolveActiveGroup(xml.apply("gv.edit.group"));
            gvEdit = g == null ? null : g.name();
        } catch (RuntimeException e) {
            gvEdit = raw("gv.edit.group", xml);
        }
        return new ActiveGroupFilters(gvGroup, gvTags, gvEdit, upper(raw("arv.group", xml)),
                upper(raw("apv.group", xml)), csv(raw("apv.tag", xml)));
    }

    /** System property first, then the XML parameter, blank-safe. */
    private static String raw(String key, Function<String, String> xml) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) v = xml.apply(key);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String upper(String s) {
        return s == null ? null : s.toUpperCase();
    }

    private static Set<String> csv(String s) {
        if (s == null) return Set.of();
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).map(String::toUpperCase)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public boolean anyActive() {
        return gvGroup != null || !gvTags.isEmpty() || gvEditGroup != null || arvGroup != null
                || apvGroup != null || !apvTags.isEmpty();
    }

    /** One readable line per active filter, e.g. {@code gv.group=MANDATORY}; empty when nothing is filtered. */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        if (gvGroup != null) out.add("gv.group=" + gvGroup);
        if (!gvTags.isEmpty()) out.add("gv.tag=" + String.join(",", gvTags));
        if (gvEditGroup != null) out.add("gv.edit.group=" + gvEditGroup);
        if (arvGroup != null) out.add("arv.group=" + arvGroup);
        if (apvGroup != null) out.add("apv.group=" + apvGroup);
        if (!apvTags.isEmpty()) out.add("apv.tag=" + String.join(",", apvTags) + " (not applied per row)");
        return out;
    }

    /** {@code true} when the data sources would have emitted {@code row} under these filters. */
    public boolean isSelected(CoverageRow row) {
        String group = row.getGroup();
        if (group == null || CoverageRow.GROUP_ALWAYS.equals(group) || "VARIES".equals(group)) return true;
        switch (row.getModule()) {
            case "GV": {
                if ("Edit".equals(row.getFlow())) return gvEditGroup == null || gvEditGroup.equals(group);
                if (gvGroup != null && !gvGroup.equals(group)) return false;
                if (!gvTags.isEmpty()) {
                    GeneralVoucherVariant v = gvVariant(row.getVariantId());
                    if (v != null) {
                        Set<String> tags = GeneralVoucherCombinationFilter.tagsFor(v).stream().map(Enum::name)
                                .collect(Collectors.toSet());
                        return !Collections.disjoint(gvTags, tags);
                    }
                }
                return true;
            }
            case "AR":
                return arvGroup == null || arvGroup.equals(group);
            case "AP":
                return apvGroup == null || apvGroup.equals(group);
            default:
                return true;
        }
    }

    private GeneralVoucherVariant gvVariant(String variantId) {
        if (gvVariants == null) {
            Map<String, GeneralVoucherVariant> m = new HashMap<>();
            for (GeneralVoucherVariant v : GeneralVoucherCombinations.allCombinations()) m.put(v.getVariantId(), v);
            gvVariants = m;
        }
        return gvVariants.get(variantId);
    }
}
