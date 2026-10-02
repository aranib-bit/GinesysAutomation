package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.NegativeStockMethodType;
import io.github.sskorol.converters.IConverter;

/**
 * ZeroCell converter for the "Negative Stock Method" column of the "Others" sheet in the
 * inventory-item Excel test data ({@code ConfigFactory.getExcelDataConfig().inventoryitemPath()})
 * — resolves the raw cell text to a {@link NegativeStockMethodType} constant via
 * {@link NegativeStockMethodType#fromExcel}, which matches case-insensitively against each
 * constant's Excel label and throws {@link IllegalArgumentException} (naming the offending
 * file/sheet/column and the allowed values) for anything unrecognized.
 */
public class NegativeStockMethodConverter implements IConverter<NegativeStockMethodType> {
    /** Resolves {@code data} to its matching {@link NegativeStockMethodType} constant; see class Javadoc. */
    @Override
    public NegativeStockMethodType convert(String data) {
        return NegativeStockMethodType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "Others",
                "Negative Stock Method"
        );
    }

    /** Registers this converter's target type as {@link NegativeStockMethodType}. */
    @Override
    public Class<NegativeStockMethodType> getType() {
        return NegativeStockMethodType.class;
    }
}
