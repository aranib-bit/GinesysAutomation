package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.MaterialTypeType;
import io.github.sskorol.converters.IConverter;

public class MaterialTypeConverter implements IConverter<MaterialTypeType> {
    @Override
    public MaterialTypeType convert(String data) {
        return MaterialTypeType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "Others",
                "Material Type"
        );
    }

    @Override
    public Class<MaterialTypeType> getType() {
        return MaterialTypeType.class;
    }
}
