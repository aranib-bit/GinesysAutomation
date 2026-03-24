package com.gsl.pages.enums.Procurement.Operations.GoodsReceiveChallanItemSelectionType;

public enum SetSelectionType {
    SELECT_SET ("ITEM Sets");

    private final String name;

    public String getName(){
        return name;
    }

    SetSelectionType(String name){
        this.name=name;
    }
}
