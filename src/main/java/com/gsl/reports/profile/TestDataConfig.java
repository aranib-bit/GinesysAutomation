package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestDataConfig {
    private String source;
    private String masterTestData;
    private String receiptsData;
    private String inventoryGroupData;
    private String inventoryItemData;
    private String arVoucherData;
    private String usersListData;
}
