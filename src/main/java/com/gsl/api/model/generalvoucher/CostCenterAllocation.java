package com.gsl.api.model.generalvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One site allocation of a General Voucher line ({@code costCenterAllocations[]}) — shape confirmed via
 * HAR ({@code GVVoucherSave…har}): {@code {"code":42,"name":"AFFIFA 1","siteType":"OS-OO-CM","shortName":"AFF",
 * "amount":1,"ouCode":1}}. The sum of a line's allocation amounts must equal the line amount.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CostCenterAllocation {
    /** The allocated site's {@code admsite.code}. */
    private long code;
    private String name;
    private String siteType;
    private String shortName;
    private double amount;
    private long ouCode;
}
