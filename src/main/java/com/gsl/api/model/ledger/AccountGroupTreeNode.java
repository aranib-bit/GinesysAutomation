package com.gsl.api.model.ledger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One node of {@code GET /FinGroup/GetAccountGrpTree}'s recursive {@code fingrp} tree — shape
 * confirmed via HAR (identical, complete body in both Ledger HARs): the same key,
 * {@code result}, is reused both as the top-level response's node array AND as each node's own
 * children array, so this class doubles as the response's element type
 * ({@code jsonPath().getList("result", AccountGroupTreeNode.class)}) and each node's
 * {@link #getResult()} children.
 *
 * <p><b>Leaf test:</b> there is no dedicated {@code isLeaf}/child-count field — a node is a leaf
 * iff {@code result} is empty. {@code isLedgerTagged} is a business flag (tends true on
 * ledger-postable nodes) but is NOT a reliable structural leaf indicator by itself; the
 * authoritative check per the user-confirmed business rule is a DB re-check via
 * {@code FinAccGrpDao.countChildren(grpCode)} — this in-memory {@link #isLeafByTree()} is only a
 * first-pass filter over the API's own tree shape.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountGroupTreeNode {
    private long id;
    private long parentId;
    private String name;
    @Builder.Default
    private List<AccountGroupTreeNode> result = List.of();
    /** "A"/"L"/"I"/"E" — see {@link com.gsl.enums.GlAccountType}. */
    private String type;
    private int sequence;
    private long grpCode;
    private String remarks;
    private String ext;
    private boolean isLedgerTagged;

    /** In-memory leaf check from the tree shape alone — {@link #result} empty. */
    public boolean isLeafByTree() {
        return result == null || result.isEmpty();
    }

    /** Flattens this node and every descendant into a single list (pre-order). */
    public List<AccountGroupTreeNode> flatten() {
        List<AccountGroupTreeNode> all = new java.util.ArrayList<>();
        all.add(this);
        if (result != null) {
            for (AccountGroupTreeNode child : result) {
                all.addAll(child.flatten());
            }
        }
        return all;
    }
}
