package com.gsl.pages.enums.Procurement;

public enum MainMenuType {
    OPERATIONS("Operations"),
    SETUP("Setup");

    private final String name;

    public String getName(){
        return name;
    }

    MainMenuType(String name){
        this.name=name;
    }
}
