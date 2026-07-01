package com.gsl.testdata.dto;

import com.gsl.testdata.Finance.Setup.AccountGroup.AccountGroupRow;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MergedFinanceAccountGroupData {
    private List<AccountGroupRow> rows;
}
