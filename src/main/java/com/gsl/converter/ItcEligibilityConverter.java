package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.ItcEligibilityType;
import io.github.sskorol.converters.IConverter;

/**
 * ZeroCell converter for the "ITC Eligibility" column of the "General" sheet in the
 * inventory-item Excel test data ({@code ConfigFactory.getExcelDataConfig().inventoryitemPath()})
 * — resolves the raw cell text to an {@link ItcEligibilityType} constant via
 * {@link ItcEligibilityType#fromExcel}, which matches case-insensitively against each constant's
 * Excel label and throws {@link IllegalArgumentException} (naming the offending file/sheet/column
 * and the allowed values) for anything unrecognized.
 */
public class ItcEligibilityConverter implements IConverter<ItcEligibilityType> {

    /** Resolves {@code data} to its matching {@link ItcEligibilityType} constant; see class Javadoc. */
    @Override
    public ItcEligibilityType convert(String data) {
        return ItcEligibilityType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "General",
                "ITC Eligibility"
        );
    }

    /** Registers this converter's target type as {@link ItcEligibilityType}. */
    @Override
    public Class<ItcEligibilityType> getType() {
        return ItcEligibilityType.class;
    }
}
