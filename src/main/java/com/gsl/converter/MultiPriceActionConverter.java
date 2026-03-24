package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.ItcEligibilityType;
import com.gsl.enums.MultiPriceActionType;
import io.github.sskorol.converters.IConverter;

public class MultiPriceActionConverter implements IConverter<MultiPriceActionType> {
    @Override
    public MultiPriceActionType convert(String data) {
        return MultiPriceActionType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "POS Behaviour",
                "Multi Price Action"
        );
    }

    @Override
    public Class<MultiPriceActionType> getType() {
        return MultiPriceActionType.class;
    }
}
