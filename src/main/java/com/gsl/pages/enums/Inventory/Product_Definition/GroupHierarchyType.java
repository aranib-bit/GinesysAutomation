package com.gsl.pages.enums.Inventory.Product_Definition;

/**
 * The three levels of Inventory &gt; Product Definition &gt; Group's Group Hierarchy TreePanel
 * (Division/Section/Department, depths 1-3) — consumed by {@code
 * JavascriptExecutorUtils.doesTreeContainText(GroupHierarchyType, String)}/{@code
 * countTreeOccurrences(GroupHierarchyType, ...)} to match a tree line's {@code "Depth <N> → <text>"}
 * format against the level's own {@link #getDepth()} (and, for the default-node-text overloads,
 * {@link #getDisplayName()}).
 */
public enum GroupHierarchyType {
    /** Default node text for a newly-added Division node, before it's renamed. */
    DIVISION("New Division", 1),
    /** Default node text for a newly-added Section node, before it's renamed. */
    SECTION("New Section", 2),
    /** Default node text for a newly-added Department node, before it's renamed. */
    DEPARTMENT("New Department", 3);

    private final String displayName;
    private final int depth;

    GroupHierarchyType(String displayName, int depth) {
        this.displayName = displayName;
        this.depth = depth;
    }

    /** @return this level's default (pre-rename) TreePanel node text, e.g. "New Division". */
    public String getDisplayName() {
        return displayName;
    }

    /** @return this level's 1-based depth in the TreePanel, matching the "Depth &lt;N&gt;" prefix in a tree line. */
    public int getDepth() {
        return depth;
    }
}
