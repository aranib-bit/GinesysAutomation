package com.gsl.database.dao.finance;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

/**
 * DAO for fingrp — Finance Account Group master table.
 * Parent linkage is by parcode (numeric FK), not by name —
 * so the duplicate check resolves the parent name to its grpcode first.
 */
public interface FinAccGrpDao {

    // Duplicate check: group with this name whose parent node has the given parent name
    @SqlQuery("SELECT COUNT(*) FROM fingrp " +
              "WHERE grpname = :name " +
              "AND parcode = (SELECT grpcode FROM fingrp WHERE grpname = :parent LIMIT 1)")
    int countByNameAndParent(@Bind("name") String name, @Bind("parent") String parent);

    // Existence check by name alone (used for root-level nodes)
    @SqlQuery("SELECT COUNT(*) FROM fingrp WHERE grpname = :name")
    int countByName(@Bind("name") String name);

    // Leaf-node check for Ledger creation: a group with any child (a row whose parcode points at
    // it) is restricted and must never be used as a ledger's grpcode — sample confirmed by the
    // user: "fingrp is 10, under fingrp 10 there are 2 leaf nodes then ledger creation fingrp is
    // restricted" i.e. `SELECT * FROM fingrp WHERE parcode = 10`. Zero rows = genuine leaf, safe
    // to attach a ledger to.
    @SqlQuery("SELECT COUNT(*) FROM fingrp WHERE parcode = :grpcode")
    int countChildren(@Bind("grpcode") long grpcode);

    // Detects a stray/garbled leftover from a failed rename — e.g. "New Group" itself, or a
    // botched replace like "New GroupaZZZ Test Verify Group" (confirmed to actually occur:
    // a broken Ctrl+A selection left the placeholder prefix intact and appended the real
    // name after it instead of replacing it). Matches anything containing "New Group" under
    // the given parent, catching both the clean and the garbled leftover cases.
    @SqlQuery("SELECT grpname FROM fingrp WHERE grpname LIKE '%New Group%' " +
              "AND parcode = (SELECT grpcode FROM fingrp WHERE grpname = :parent LIMIT 1)")
    java.util.List<String> findStrayNewGroupNamesUnderParent(@Bind("parent") String parent);

    // Full ancestor chain of a group, root-most first (excludes the group itself and the
    // virtual "Tree" root row — grpcode 1, parcode NULL — which is never rendered as a UI
    // node). Walks the live fingrp.parcode chain rather than relying on a second Excel file
    // (AccountGroup.xlsx) staying complete/in-sync — a deeply-nested parent is invisible in a
    // freshly-loaded, fully-collapsed tree until every real ancestor above it has been expanded.
    @SqlQuery("WITH RECURSIVE anc AS ( " +
              "  SELECT parcode AS grpcode, 1 AS depth FROM fingrp WHERE grpname = :name " +
              "  UNION ALL " +
              "  SELECT f.parcode AS grpcode, a.depth + 1 " +
              "  FROM fingrp f JOIN anc a ON f.grpcode = a.grpcode " +
              "  WHERE f.parcode IS NOT NULL " +
              ") " +
              "SELECT f.grpname FROM anc a JOIN fingrp f ON f.grpcode = a.grpcode " +
              "WHERE f.grpname <> 'Tree' ORDER BY a.depth DESC")
    java.util.List<String> getAncestorChain(@Bind("name") String name);
}
