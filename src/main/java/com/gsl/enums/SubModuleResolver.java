package com.gsl.enums;

import com.gsl.pages.enums.Admin.Organization.OrganizationMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.pages.enums.Finance.Setup.SetupMenuType;
import com.gsl.pages.enums.Inventory.Logistics.LogisticsMenuType;
import com.gsl.pages.enums.Inventory.Product_Definition.Product_DefintionMenuType;
import com.gsl.pages.enums.Inventory.WMS.WMSMenuType;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;

import java.util.List;
import java.util.Map;

/**
 * Resolves a {@code FrameworkAnnotation#subModule()} short code (e.g. {@code
 * "FinanceARVoucher"}) to the {@link ISubModule} constant it names, scoped to the annotation's
 * {@link MainModuleType}. Used by {@code AnnotationMethodInterceptor} and {@code TestListener} to
 * turn a test's declared sub-module string into a real enum constant (and, from there, its
 * {@code fw.group} for CI/CD pipeline-stage filtering).
 *
 * <p>{@link #REGISTRY} enumerates every {@code *MenuType} enum (one per module/page-tree branch)
 * that implements {@link ISubModule} for each {@link MainModuleType} — adding a new sub-module
 * enum for an existing module means adding its {@code .class} to that module's list here.
 */
public final class SubModuleResolver {

    private static final Map<MainModuleType, List<Class<? extends Enum<?>>>> REGISTRY = Map.of(
        MainModuleType.FINANCE,
            List.of(OperationsMenuType.class, SetupMenuType.class),
        MainModuleType.INVENTORY,
            List.of(
                Product_DefintionMenuType.class,
                com.gsl.pages.enums.Inventory.Operations.OperationsMenuType.class,
                WMSMenuType.class,
                LogisticsMenuType.class),
        MainModuleType.PROCUREMENT,
            List.of(
                com.gsl.pages.enums.Procurement.Operations.OperationsMenuType.class,
                com.gsl.pages.enums.Procurement.Setup.SetupMenuType.class),
        MainModuleType.ADMIN,
            List.of(OrganizationMenuType.class)
    );

    private SubModuleResolver() {}

    /**
     * Scans every {@link ISubModule}-implementing enum registered for {@code module} (see {@link
     * #REGISTRY}) and returns the constant whose {@link ISubModule#getShortCode()} matches {@code
     * shortCode}.
     *
     * @throws IllegalArgumentException if {@code shortCode} isn't found among {@code module}'s
     *                                  registered sub-module enums (or {@code module} has none registered)
     */
    public static ISubModule resolve(MainModuleType module, String shortCode) {
        for (Class<? extends Enum<?>> cls : REGISTRY.getOrDefault(module, List.of())) {
            for (Enum<?> constant : cls.getEnumConstants()) {
                if (constant instanceof ISubModule sm && sm.getShortCode().equals(shortCode)) {
                    return sm;
                }
            }
        }
        throw new IllegalArgumentException(
            "Unknown shortCode '" + shortCode + "' for module " + module);
    }
}
