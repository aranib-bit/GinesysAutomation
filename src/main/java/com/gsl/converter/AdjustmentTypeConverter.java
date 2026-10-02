package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.AdjustmentType;
import io.github.sskorol.converters.IConverter;

/**
 * ZeroCell converter for the "AdjustmentType" column of the "SubLedgerInformation" sheet in the
 * AR voucher Excel test data ({@code ConfigFactory.getExcelDataConfig().arVoucherPath()}) —
 * resolves the raw cell text to an {@link AdjustmentType} constant via
 * {@link AdjustmentType#fromExcel}, which matches case-insensitively against each constant's
 * Excel label and throws {@link IllegalArgumentException} (naming the offending file/sheet/column
 * and the allowed values) for anything unrecognized.
 */
public class AdjustmentTypeConverter implements IConverter<AdjustmentType> {

    /** Resolves {@code data} to its matching {@link AdjustmentType} constant; see class Javadoc. */
    @Override
    public AdjustmentType convert(String data) {
        return AdjustmentType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().arVoucherPath(),
                "SubLedgerInformation",
                "AdjustmentType"
        );
    }

    /** Registers this converter's target type as {@link AdjustmentType}. */
    @Override
    public Class<AdjustmentType> getType() {
        return AdjustmentType.class;
    }
}
