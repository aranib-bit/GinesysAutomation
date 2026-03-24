package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.AllowPriceChangeType;
import com.gsl.enums.ItcEligibilityType;
import com.gsl.enums.MultiPriceActionType;
import io.github.sskorol.converters.IConverter;

public class AllowPriceChangeConverter implements IConverter<AllowPriceChangeType> {
    @Override
    public AllowPriceChangeType convert(String data) {
        return AllowPriceChangeType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "POS Behaviour",
                "Allow Price Change"
        );
    }

    @Override
    public Class<AllowPriceChangeType> getType() {
        return AllowPriceChangeType.class;
    }
}
