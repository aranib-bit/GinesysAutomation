package com.gsl.testdata.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * One item (or set) entry within a Procurement Receipt's item list, as assembled by
 * {@code MergedReceiptDataSupplier} for {@link MergedReceiptData#getItemBlocks()}.
 *
 * <p>Wraps a single {@link ItemDetailsRow} (which method/way the item was selected by) together
 * with whichever of the selection-detail row lists actually applies to it — the supplier picks the
 * matching one based on {@link ItemDetailsRow#getItemSelectionMajorMethod()}/{@link
 * ItemDetailsRow#getItemSetSelectionWay()} and leaves the rest empty:
 * <ul>
 *   <li>{@link #scanItems} — populated for major method {@code "Item"}, way {@code "Scan Item"}</li>
 *   <li>{@link #selectItems} — populated for major method {@code "Item"}, way {@code "Select Item"}</li>
 *   <li>{@link #selectSets} — populated for major method {@code "Set"}, way {@code "Select Set"}</li>
 * </ul>
 */
@Data
@Builder
public class ItemBlock {
    private ItemDetailsRow itemDetails;
    private List<ScanItemRow> scanItems;
    private List<SelectItemRow> selectItems;
    private List<SelectSetRow> selectSets;
}
