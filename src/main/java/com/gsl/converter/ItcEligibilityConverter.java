package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.ItcEligibilityType;
import io.github.sskorol.converters.IConverter;

public class ItcEligibilityConverter implements IConverter<ItcEligibilityType> {

    @Override
    public ItcEligibilityType convert(String data) {
        return ItcEligibilityType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "General",
                "ITC Eligibility"
        );
    }

    @Override
    public Class<ItcEligibilityType> getType() {
        return ItcEligibilityType.class;
    }
}
