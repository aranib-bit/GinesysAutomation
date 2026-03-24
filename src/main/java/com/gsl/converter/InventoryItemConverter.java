package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.InventoryItemType;
import io.github.sskorol.converters.IConverter;

public class InventoryItemConverter implements IConverter<InventoryItemType> {
    @Override
    public InventoryItemType convert(String data) {
        return InventoryItemType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "Others",
                "Inventory Item"
        );
    }

    @Override
    public Class<InventoryItemType> getType() {
        return InventoryItemType.class;
    }
}
