package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.AllowPriceChangeType;
import com.gsl.enums.AutoQtyPopupType;
import com.gsl.enums.ItcEligibilityType;
import io.github.sskorol.converters.IConverter;

public class AutoQtyPopupConverter implements IConverter<AutoQtyPopupType> {
    @Override
    public AutoQtyPopupType convert(String data) {
        return AutoQtyPopupType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "POS Behaviour",
                "Auto Qty Popup"
        );
    }

    @Override
    public Class<AutoQtyPopupType> getType() {
        return AutoQtyPopupType.class;
    }
}
