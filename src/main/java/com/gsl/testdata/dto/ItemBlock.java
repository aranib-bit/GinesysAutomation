package com.gsl.testdata.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ItemBlock {
    private ItemDetailsRow itemDetails;
    private List<ScanItemRow> scanItems;
    private List<SelectItemRow> selectItems;
    private List<SelectSetRow> selectSets;
}
