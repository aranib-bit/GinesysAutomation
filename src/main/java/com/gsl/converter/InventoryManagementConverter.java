package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.InventoryManagementType;
import io.github.sskorol.converters.IConverter;

/**
 * ZeroCell converter for the "Inventory Management" column of the "Inventory Tracking" sheet in
 * the inventory-item Excel test data ({@code ConfigFactory.getExcelDataConfig().inventoryitemPath()})
 * — resolves the raw cell text to an {@link InventoryManagementType} constant via
 * {@link InventoryManagementType#fromExcel}, which matches case-insensitively against each
 * constant's Excel label and throws {@link IllegalArgumentException} (naming the offending
 * file/sheet/column and the allowed values) for anything unrecognized.
 */
public class InventoryManagementConverter implements IConverter<InventoryManagementType> {
    /** Resolves {@code data} to its matching {@link InventoryManagementType} constant; see class Javadoc. */
    @Override
    public InventoryManagementType convert(String data) {
        return InventoryManagementType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "Inventory Tracking",
                "Inventory Management"
        );
    }

    /** Registers this converter's target type as {@link InventoryManagementType}. */
    @Override
    public Class<InventoryManagementType> getType() {
        return InventoryManagementType.class;
    }
}
