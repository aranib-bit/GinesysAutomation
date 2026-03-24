package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.InventoryManagementType;
import com.gsl.enums.PricingManagementType;
import io.github.sskorol.converters.IConverter;

public class PricingManagementConverter implements IConverter<PricingManagementType> {
    @Override
    public PricingManagementType convert(String data) {
        return PricingManagementType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "Inventory Tracking",
                "Pricing Management"
        );
    }

    @Override
    public Class<PricingManagementType> getType() {
        return PricingManagementType.class;
    }
}
