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
