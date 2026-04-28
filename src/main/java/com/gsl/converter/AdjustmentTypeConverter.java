package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.AdjustmentType;
import io.github.sskorol.converters.IConverter;

public class AdjustmentTypeConverter implements IConverter<AdjustmentType> {

    @Override
    public AdjustmentType convert(String data) {
        return AdjustmentType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().arVoucherPath(),
                "SubLedgerInformation",
                "AdjustmentType"
        );
    }

    @Override
    public Class<AdjustmentType> getType() {
        return AdjustmentType.class;
    }
}
