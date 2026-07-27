package com.gsl.testdata.dto;

import com.gsl.testdata.Finance.Setup.AccountGroup.AccountGroupLifecycleRow;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MergedAccountGroupLifecycleData {
    private List<AccountGroupLifecycleRow> rows;
}
