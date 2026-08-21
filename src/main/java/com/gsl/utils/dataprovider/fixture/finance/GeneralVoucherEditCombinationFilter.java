package com.gsl.utils.dataprovider.fixture.finance;

/**
 * Shared {@code gv.edit.group} filtering logic for {@code DbGeneralVoucherEditDataSource} —
 * mirrors {@link GeneralVoucherCombinationFilter}'s shape, scoped to
 * {@link GeneralVoucherEditCombinations#allCombinations()} instead of Create's 128-combination
 * matrix. No tag dimension exists for Edit (plan §7 doesn't define one) — only the group gate.
 */
public final class GeneralVoucherEditCombinationFilter {

    private GeneralVoucherEditCombinationFilter() {}

    /** Resolves the active {@code gv.edit.group} filter — {@code null} means "run ALL groups". */
    public static GeneralVoucherEditExecutionGroup resolveActiveGroup(String xmlEditGroupParam) {
        String prop = System.getProperty("gv.edit.group");
        if (prop == null || prop.isBlank()) prop = xmlEditGroupParam;
        if (prop == null || prop.isBlank()) return null; // null = run ALL groups
        return GeneralVoucherEditExecutionGroup.valueOf(prop.trim().toUpperCase());
    }

    /** {@code true} when {@code variant} survives the active group filter ({@code null} active = everything passes). */
    public static boolean survives(GeneralVoucherEditVariant variant, GeneralVoucherEditExecutionGroup activeGroup) {
        return activeGroup == null || activeGroup == variant.getExecutionGroup();
    }
}
