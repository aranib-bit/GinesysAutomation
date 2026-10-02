package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.AutoQtyPopupType;
import com.gsl.enums.ChargeExtraTaxType;
import io.github.sskorol.converters.IConverter;

/**
 * ZeroCell converter for the "Charge Extra Tax" column of the "POS Behaviour" sheet in the
 * inventory-item Excel test data ({@code ConfigFactory.getExcelDataConfig().inventoryitemPath()})
 * — resolves the raw cell text to a {@link ChargeExtraTaxType} constant via
 * {@link ChargeExtraTaxType#fromExcel}, which matches case-insensitively against each constant's
 * Excel label and throws {@link IllegalArgumentException} (naming the offending file/sheet/column
 * and the allowed values) for anything unrecognized.
 */
public class ChargeExtraTaxConverter implements IConverter<ChargeExtraTaxType> {


        /** Resolves {@code data} to its matching {@link ChargeExtraTaxType} constant; see class Javadoc. */
        @Override
        public ChargeExtraTaxType convert(String data) {
            return ChargeExtraTaxType.fromExcel(
                    data,
                    ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                    "POS Behaviour",
                    "Charge Extra Tax"
            );
        }

        /** Registers this converter's target type as {@link ChargeExtraTaxType}. */
        @Override
        public Class<ChargeExtraTaxType> getType() {
            return ChargeExtraTaxType.class;
        }
}
