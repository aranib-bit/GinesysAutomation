package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.ItcEligibilityType;
import com.gsl.enums.MultiPriceActionType;
import io.github.sskorol.converters.IConverter;

/**
 * ZeroCell converter for the "Multi Price Action" column of the "POS Behaviour" sheet in the
 * inventory-item Excel test data ({@code ConfigFactory.getExcelDataConfig().inventoryitemPath()})
 * — resolves the raw cell text to a {@link MultiPriceActionType} constant via
 * {@link MultiPriceActionType#fromExcel}, which matches case-insensitively against each
 * constant's Excel label and throws {@link IllegalArgumentException} (naming the offending
 * file/sheet/column and the allowed values) for anything unrecognized.
 */
public class MultiPriceActionConverter implements IConverter<MultiPriceActionType> {
    /** Resolves {@code data} to its matching {@link MultiPriceActionType} constant; see class Javadoc. */
    @Override
    public MultiPriceActionType convert(String data) {
        return MultiPriceActionType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "POS Behaviour",
                "Multi Price Action"
        );
    }

    /** Registers this converter's target type as {@link MultiPriceActionType}. */
    @Override
    public Class<MultiPriceActionType> getType() {
        return MultiPriceActionType.class;
    }
}
