package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.ReturnBehaviourType;
import com.gsl.enums.ValidityModeType;
import io.github.sskorol.converters.IConverter;

public class ValidityModeConverter implements IConverter<ValidityModeType> {
    @Override
    public ValidityModeType convert(String data) {
        return ValidityModeType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "Inventory Tracking",
                "Validity Mode"
        );
    }

    @Override
    public Class<ValidityModeType> getType() {
        return ValidityModeType.class;
    }
}
