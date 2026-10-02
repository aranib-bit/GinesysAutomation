package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.AllowPriceChangeType;
import com.gsl.enums.ReturnBehaviourType;
import io.github.sskorol.converters.IConverter;

/**
 * ZeroCell converter for the "Return Behaviour" column of the "POS Behaviour" sheet in the
 * inventory-item Excel test data ({@code ConfigFactory.getExcelDataConfig().inventoryitemPath()})
 * — resolves the raw cell text to a {@link ReturnBehaviourType} constant via
 * {@link ReturnBehaviourType#fromExcel}, which matches case-insensitively against each
 * constant's Excel label and throws {@link IllegalArgumentException} (naming the offending
 * file/sheet/column and the allowed values) for anything unrecognized.
 */
public class ReturnBehaviourConverter implements IConverter<ReturnBehaviourType> {
    /** Resolves {@code data} to its matching {@link ReturnBehaviourType} constant; see class Javadoc. */
    @Override
    public ReturnBehaviourType convert(String data) {
        return ReturnBehaviourType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "POS Behaviour",
                "Return Behaviour"
        );
    }

    /** Registers this converter's target type as {@link ReturnBehaviourType}. */
    @Override
    public Class<ReturnBehaviourType> getType() {
        return ReturnBehaviourType.class;
    }
}
