package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.InventoryItemType;
import io.github.sskorol.converters.IConverter;

/**
 * ZeroCell converter for the "Inventory Item" column of the "Others" sheet in the inventory-item
 * Excel test data ({@code ConfigFactory.getExcelDataConfig().inventoryitemPath()}) — resolves the
 * raw cell text to an {@link InventoryItemType} constant via {@link InventoryItemType#fromExcel},
 * which matches case-insensitively against each constant's Excel label and throws
 * {@link IllegalArgumentException} (naming the offending file/sheet/column and the allowed
 * values) for anything unrecognized.
 */
public class InventoryItemConverter implements IConverter<InventoryItemType> {
    /** Resolves {@code data} to its matching {@link InventoryItemType} constant; see class Javadoc. */
    @Override
    public InventoryItemType convert(String data) {
        return InventoryItemType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "Others",
                "Inventory Item"
        );
    }

    /** Registers this converter's target type as {@link InventoryItemType}. */
    @Override
    public Class<InventoryItemType> getType() {
        return InventoryItemType.class;
    }
}
