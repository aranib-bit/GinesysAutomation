package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.AutoQtyPopupType;
import com.gsl.enums.ChargeExtraTaxType;
import io.github.sskorol.converters.IConverter;

public class ChargeExtraTaxConverter implements IConverter<ChargeExtraTaxType> {


        @Override
        public ChargeExtraTaxType convert(String data) {
            return ChargeExtraTaxType.fromExcel(
                    data,
                    ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                    "POS Behaviour",
                    "Charge Extra Tax"
            );
        }

        @Override
        public Class<ChargeExtraTaxType> getType() {
            return ChargeExtraTaxType.class;
        }
}
