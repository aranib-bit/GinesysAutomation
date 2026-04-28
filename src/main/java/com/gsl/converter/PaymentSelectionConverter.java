package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.PaymentSelectionType;
import io.github.sskorol.converters.IConverter;

public class PaymentSelectionConverter implements IConverter<PaymentSelectionType> {

    @Override
    public PaymentSelectionType convert(String data) {
        return PaymentSelectionType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().arVoucherPath(),
                "SubLedgerInformation",
                "Payment Selection"
        );
    }

    @Override
    public Class<PaymentSelectionType> getType() {
        return PaymentSelectionType.class;
    }
}
