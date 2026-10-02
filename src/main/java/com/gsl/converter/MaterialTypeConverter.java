package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.MaterialTypeType;
import io.github.sskorol.converters.IConverter;

/**
 * ZeroCell converter for the "Material Type" column of the "Others" sheet in the inventory-item
 * Excel test data ({@code ConfigFactory.getExcelDataConfig().inventoryitemPath()}) — resolves the
 * raw cell text to a {@link MaterialTypeType} constant via {@link MaterialTypeType#fromExcel},
 * which matches case-insensitively against each constant's Excel label and throws
 * {@link IllegalArgumentException} (naming the offending file/sheet/column and the allowed
 * values) for anything unrecognized.
 */
public class MaterialTypeConverter implements IConverter<MaterialTypeType> {
    /** Resolves {@code data} to its matching {@link MaterialTypeType} constant; see class Javadoc. */
    @Override
    public MaterialTypeType convert(String data) {
        return MaterialTypeType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().inventoryitemPath(),
                "Others",
                "Material Type"
        );
    }

    /** Registers this converter's target type as {@link MaterialTypeType}. */
    @Override
    public Class<MaterialTypeType> getType() {
        return MaterialTypeType.class;
    }
}
