package com.gsl.pages.enums.Admin;

public enum MainMenuType {
    ORGANIZATION("Organization"),
    FINANCE("Finance"),
    SECURITY("Security"),
    UTILITIES("Utilities");

    private final String name;

    public String getName(){
        return name;
    }

    MainMenuType(String name){
        this.name=name;
    }
}
