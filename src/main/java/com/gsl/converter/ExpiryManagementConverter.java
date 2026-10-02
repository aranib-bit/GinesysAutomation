package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.ExpiryManagementType;
import io.github.sskorol.converters.IConverter;

/**
 * ZeroCell converter for the "Expiry Management" column of the "Inventory Tracking" sheet in the
 * inventory-item Excel test data ({@code ConfigFactory.getExcelDataConfig().inventoryitemPath()})
 * — resolves the raw cell text to an {@link ExpiryManagementType} constant via
 * {@link ExpiryManagementType#fromExcel}, which matches case-insensitively against each
 * constant's Excel label and throws {@link IllegalArgumentException} (naming the offending
 * file/sheet/column and the allowed values) for anything unrecognized.
 */
public class ExpiryManagementConverter implements IConverter<ExpiryManagementType> {
    /** Resolves {@code data} to its matching {@link ExpiryManagementType} constant; see class Javadoc. */
    @Override
    public ExpiryManagementType convert(String data) {
        return ExpiryManagementType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "Inventory Tracking",
                "Expiry Management"
        );
    }

    /** Registers this converter's target type as {@link ExpiryManagementType}. */
    @Override
    public Class<ExpiryManagementType> getType() {
        return ExpiryManagementType.class;
    }
}
