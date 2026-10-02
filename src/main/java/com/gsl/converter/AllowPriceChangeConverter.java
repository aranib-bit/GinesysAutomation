package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.AllowPriceChangeType;
import com.gsl.enums.ItcEligibilityType;
import com.gsl.enums.MultiPriceActionType;
import io.github.sskorol.converters.IConverter;

/**
 * ZeroCell converter for the "Allow Price Change" column of the "POS Behaviour" sheet in the
 * inventory-item Excel test data ({@code ConfigFactory.getExcelDataConfig().inventoryitemPath()})
 * — resolves the raw cell text to an {@link AllowPriceChangeType} constant via
 * {@link AllowPriceChangeType#fromExcel}, which matches case-insensitively against each
 * constant's Excel label and throws {@link IllegalArgumentException} (naming the offending
 * file/sheet/column and the allowed values) for anything unrecognized.
 */
public class AllowPriceChangeConverter implements IConverter<AllowPriceChangeType> {
    /** Resolves {@code data} to its matching {@link AllowPriceChangeType} constant; see class Javadoc. */
    @Override
    public AllowPriceChangeType convert(String data) {
        return AllowPriceChangeType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "POS Behaviour",
                "Allow Price Change"
        );
    }

    /** Registers this converter's target type as {@link AllowPriceChangeType}. */
    @Override
    public Class<AllowPriceChangeType> getType() {
        return AllowPriceChangeType.class;
    }
}
