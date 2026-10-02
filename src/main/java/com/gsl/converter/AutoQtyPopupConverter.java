package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.AllowPriceChangeType;
import com.gsl.enums.AutoQtyPopupType;
import com.gsl.enums.ItcEligibilityType;
import io.github.sskorol.converters.IConverter;

/**
 * ZeroCell converter for the "Auto Qty Popup" column of the "POS Behaviour" sheet in the
 * inventory-item Excel test data ({@code ConfigFactory.getExcelDataConfig().inventoryitemPath()})
 * — resolves the raw cell text to an {@link AutoQtyPopupType} constant via
 * {@link AutoQtyPopupType#fromExcel}, which matches case-insensitively against each constant's
 * Excel label and throws {@link IllegalArgumentException} (naming the offending file/sheet/column
 * and the allowed values) for anything unrecognized.
 */
public class AutoQtyPopupConverter implements IConverter<AutoQtyPopupType> {
    /** Resolves {@code data} to its matching {@link AutoQtyPopupType} constant; see class Javadoc. */
    @Override
    public AutoQtyPopupType convert(String data) {
        return AutoQtyPopupType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "POS Behaviour",
                "Auto Qty Popup"
        );
    }

    /** Registers this converter's target type as {@link AutoQtyPopupType}. */
    @Override
    public Class<AutoQtyPopupType> getType() {
        return AutoQtyPopupType.class;
    }
}
