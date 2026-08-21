package com.gsl.utils.dataprovider.fixture.finance;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared {@code gv.group}/{@code gv.tag} filtering logic for General Voucher's DB-mode data
 * sources — extracted out of {@code DbGeneralVoucherDataSource} so
 * {@code DbGeneralVoucherRevertDataSource} applies the exact same execution-group/tag semantics
 * to the same {@link GeneralVoucherCombinations#allCombinations()} matrix without duplicating this
 * logic. Both data sources read the {@code gv.group} (single value) and {@code gv.tag}
 * (comma-separated) filters the same way, whether supplied as a system property (Maven Surefire
 * path) or a TestNG XML {@code <parameter>} (IntelliJ direct-run path).
 */
public final class GeneralVoucherCombinationFilter {

    private GeneralVoucherCombinationFilter() {}

    /** Resolves the active {@code gv.group} filter — {@code null} means "run ALL groups". */
    public static GeneralVoucherExecutionGroup resolveActiveGroup(String xmlGroupParam) {
        String prop = System.getProperty("gv.group");
        if (prop == null || prop.isBlank()) prop = xmlGroupParam;
        if (prop == null || prop.isBlank()) return null; // null = run ALL groups
        return GeneralVoucherExecutionGroup.valueOf(prop.trim().toUpperCase());
    }

    /** Resolves the active {@code gv.tag} filter set — empty means "no tag filter" (OR-matched when non-empty). */
    public static Set<GeneralVoucherTag> resolveActiveTags(String xmlTagParam) {
        String prop = System.getProperty("gv.tag");
        if (prop == null || prop.isBlank()) prop = xmlTagParam;
        if (prop == null || prop.isBlank()) return Collections.emptySet();
        return Arrays.stream(prop.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> GeneralVoucherTag.valueOf(s.toUpperCase()))
                .collect(Collectors.toSet());
    }

    /** {@code true} when {@code target} passes the active group filter ({@code null} active = everything passes). */
    public static boolean matchesGroup(GeneralVoucherExecutionGroup active, GeneralVoucherExecutionGroup target) {
        return active == null || active == target;
    }

    /** Derives the full dimensional tag set for one variant — see {@link GeneralVoucherTag}. */
    public static Set<GeneralVoucherTag> tagsFor(GeneralVoucherVariant variant) {
        Set<GeneralVoucherTag> tags = new HashSet<>();
        switch (variant.getAccountType()) {
            case EXPENSE -> tags.add(GeneralVoucherTag.EXPENSE);
            case INCOME -> tags.add(GeneralVoucherTag.INCOME);
            case ASSET -> tags.add(GeneralVoucherTag.ASSET);
            case LIABILITY -> tags.add(GeneralVoucherTag.LIABILITY);
        }
        switch (variant.getGlLineType()) {
            case TYPE1_NONE -> tags.add(GeneralVoucherTag.TYPE1_NONE);
            case TYPE2_SITE_ONLY -> tags.add(GeneralVoucherTag.TYPE2_SITE_ONLY);
            case TYPE3_SITE_AND_SUBLEDGER -> tags.add(GeneralVoucherTag.TYPE3_SITE_AND_SUBLEDGER);
        }
        tags.add(variant.isDebitSide() ? GeneralVoucherTag.DEBIT_SIDE : GeneralVoucherTag.CREDIT_SIDE);
        tags.add(variant.isReversal() ? GeneralVoucherTag.REVERSAL_YES : GeneralVoucherTag.REVERSAL_NO);
        switch (variant.getPaymentVariant()) {
            case CHEQUE -> tags.add(GeneralVoucherTag.PAYMENT_CHEQUE);
            case NEFT_RTGS -> tags.add(GeneralVoucherTag.PAYMENT_NEFT_RTGS);
            case NONE -> tags.add(GeneralVoucherTag.PAYMENT_NONE);
            case CASH -> tags.add(GeneralVoucherTag.PAYMENT_CASH);
        }
        return tags;
    }

    /** {@code true} when {@code variant} survives both the group and tag filters. */
    public static boolean survives(GeneralVoucherVariant variant, GeneralVoucherExecutionGroup activeGroup,
            Set<GeneralVoucherTag> activeTags) {
        if (!matchesGroup(activeGroup, variant.getExecutionGroup())) return false;
        if (activeTags.isEmpty()) return true;
        return !Collections.disjoint(activeTags, tagsFor(variant));
    }
}
