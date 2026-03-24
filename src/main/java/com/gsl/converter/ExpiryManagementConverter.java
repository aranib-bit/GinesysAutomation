package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.ExpiryManagementType;
import io.github.sskorol.converters.IConverter;

public class ExpiryManagementConverter implements IConverter<ExpiryManagementType> {
    @Override
    public ExpiryManagementType convert(String data) {
        return ExpiryManagementType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "Inventory Tracking",
                "Expiry Management"
        );
    }

    @Override
    public Class<ExpiryManagementType> getType() {
        return ExpiryManagementType.class;
    }
}
