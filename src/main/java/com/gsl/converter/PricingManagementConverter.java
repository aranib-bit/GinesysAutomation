package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.InventoryManagementType;
import com.gsl.enums.PricingManagementType;
import io.github.sskorol.converters.IConverter;

/**
 * ZeroCell converter for the "Pricing Management" column of the "Inventory Tracking" sheet in the
 * inventory-item Excel test data ({@code ConfigFactory.getExcelDataConfig().inventoryitemPath()})
 * — resolves the raw cell text to a {@link PricingManagementType} constant via
 * {@link PricingManagementType#fromExcel}, which matches case-insensitively against each
 * constant's Excel label and throws {@link IllegalArgumentException} (naming the offending
 * file/sheet/column and the allowed values) for anything unrecognized.
 */
public class PricingManagementConverter implements IConverter<PricingManagementType> {
    /** Resolves {@code data} to its matching {@link PricingManagementType} constant; see class Javadoc. */
    @Override
    public PricingManagementType convert(String data) {
        return PricingManagementType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "Inventory Tracking",
                "Pricing Management"
        );
    }

    /** Registers this converter's target type as {@link PricingManagementType}. */
    @Override
    public Class<PricingManagementType> getType() {
        return PricingManagementType.class;
    }
}
