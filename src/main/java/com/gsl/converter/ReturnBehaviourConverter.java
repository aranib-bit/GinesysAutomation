package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.AllowPriceChangeType;
import com.gsl.enums.ReturnBehaviourType;
import io.github.sskorol.converters.IConverter;

public class ReturnBehaviourConverter implements IConverter<ReturnBehaviourType> {
    @Override
    public ReturnBehaviourType convert(String data) {
        return ReturnBehaviourType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "POS Behaviour",
                "Return Behaviour"
        );
    }

    @Override
    public Class<ReturnBehaviourType> getType() {
        return ReturnBehaviourType.class;
    }
}
