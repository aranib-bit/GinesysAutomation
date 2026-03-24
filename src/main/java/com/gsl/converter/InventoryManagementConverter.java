package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.InventoryManagementType;
import io.github.sskorol.converters.IConverter;

public class InventoryManagementConverter implements IConverter<InventoryManagementType> {
    @Override
    public InventoryManagementType convert(String data) {
        return InventoryManagementType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "Inventory Tracking",
                "Inventory Management"
        );
    }

    @Override
    public Class<InventoryManagementType> getType() {
        return InventoryManagementType.class;
    }
}
