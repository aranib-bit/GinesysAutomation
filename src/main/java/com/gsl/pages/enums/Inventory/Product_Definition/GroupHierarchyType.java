package com.gsl.pages.enums.Inventory.Product_Definition;

public enum GroupHierarchyType {
    DIVISION("New Division", 1),
    SECTION("New Section", 2),
    DEPARTMENT("New Department", 3);

    private final String displayName;
    private final int depth;

    GroupHierarchyType(String displayName, int depth) {
        this.displayName = displayName;
        this.depth = depth;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDepth() {
        return depth;
    }
}
