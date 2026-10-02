package com.gsl.converter;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.PaymentSelectionType;
import io.github.sskorol.converters.IConverter;

/**
 * ZeroCell converter for the "Payment Mode" column of the "PaymentInformation" sheet in the AR
 * voucher Excel test data ({@code ConfigFactory.getExcelDataConfig().arVoucherPath()}) — resolves
 * the raw cell text to a {@link PaymentSelectionType} constant via
 * {@link PaymentSelectionType#fromExcel}, which matches case-insensitively against each
 * constant's Excel label and throws {@link IllegalArgumentException} (naming the offending
 * file/sheet/column and the allowed values) for anything unrecognized.
 *
 * <p>Distinct from {@link PaymentSelectionConverter}, which converts to the same
 * {@link PaymentSelectionType} but reads the "Payment Selection" column of the
 * "SubLedgerInformation" sheet instead — the two exist because the same enum backs two different
 * Excel columns/UI fields.
 */
public class PaymentModeConverter implements IConverter<PaymentSelectionType> {

    /** Resolves {@code data} to its matching {@link PaymentSelectionType} constant; see class Javadoc. */
    @Override
    public PaymentSelectionType convert(String data) {
        return PaymentSelectionType.fromExcel(
                data,
                ConfigFactory.getExcelDataConfig().arVoucherPath(),
                "PaymentInformation",
                "Payment Mode"
        );
    }

    /** Registers this converter's target type as {@link PaymentSelectionType}. */
    @Override
    public Class<PaymentSelectionType> getType() {
        return PaymentSelectionType.class;
    }
}
