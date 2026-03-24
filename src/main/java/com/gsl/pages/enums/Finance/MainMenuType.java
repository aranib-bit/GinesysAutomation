package com.gsl.pages.enums.Finance;

public enum MainMenuType {

    OPERATIONS("Operations"),
    SETUP("Setup");

    private final String name;

    MainMenuType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}