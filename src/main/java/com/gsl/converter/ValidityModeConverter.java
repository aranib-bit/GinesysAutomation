package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.ReturnBehaviourType;
import com.gsl.enums.ValidityModeType;
import io.github.sskorol.converters.IConverter;

/**
 * ZeroCell converter for the "Validity Mode" column of the "Inventory Tracking" sheet in the
 * inventory-item Excel test data ({@code ConfigFactory.getExcelDataConfig().inventoryitemPath()})
 * — resolves the raw cell text to a {@link ValidityModeType} constant via
 * {@link ValidityModeType#fromExcel}, which matches case-insensitively against each constant's
 * Excel label and throws {@link IllegalArgumentException} (naming the offending file/sheet/column
 * and the allowed values) for anything unrecognized.
 */
public class ValidityModeConverter implements IConverter<ValidityModeType> {
    /** Resolves {@code data} to its matching {@link ValidityModeType} constant; see class Javadoc. */
    @Override
    public ValidityModeType convert(String data) {
        return ValidityModeType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "Inventory Tracking",
                "Validity Mode"
        );
    }

    /** Registers this converter's target type as {@link ValidityModeType}. */
    @Override
    public Class<ValidityModeType> getType() {
        return ValidityModeType.class;
    }
}
