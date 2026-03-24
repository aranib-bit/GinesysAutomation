package com.gsl.pages.enums.topmenucomponent;

public enum MainModuleType {
    ADMIN("Admin"),
    INVENTORY("Inventory"),
    PROCUREMENT("Procurement"),
    FINANCE("Finance"),
    SALESDISTRUBTION("Sales & Distribution"),
    POSMANAGEMENT("POS Management"),
    REPORT("Report");

    private final String name;

    public String getName(){
        return name;
    }

    MainModuleType(String name){
        this.name=name;
    }
}
