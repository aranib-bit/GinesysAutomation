package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.PaymentSelectionType;
import io.github.sskorol.converters.IConverter;

public class PaymentModeConverter implements IConverter<PaymentSelectionType> {

    @Override
    public PaymentSelectionType convert(String data) {
        return PaymentSelectionType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().arVoucherPath(),
                "PaymentInformation",
                "Payment Mode"
        );
    }

    @Override
    public Class<PaymentSelectionType> getType() {
        return PaymentSelectionType.class;
    }
}
