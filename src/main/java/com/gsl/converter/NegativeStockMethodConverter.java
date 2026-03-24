package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.NegativeStockMethodType;
import io.github.sskorol.converters.IConverter;

public class NegativeStockMethodConverter implements IConverter<NegativeStockMethodType> {
    @Override
    public NegativeStockMethodType convert(String data) {
        return NegativeStockMethodType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "Others",
                "Negative Stock Method"
        );
    }

    @Override
    public Class<NegativeStockMethodType> getType() {
        return NegativeStockMethodType.class;
    }
}
