package com.gsl.database.dao.inventory;

import com.gsl.model.inventory.InvGrp;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.Optional;

/**
 * DAO for invgrp — the Inventory Group master table used to classify inventory items into a
 * multi-level hierarchy ({@code lev}/{@code parcode}, see {@link InvGrp}). Inventory-module test
 * data setup/verification (e.g. Product Definition group-creation tests) uses this DAO to look up
 * a group by name and to confirm row counts before/after a create action.
 */
@RegisterBeanMapper(InvGrp.class)
public interface InvGrpDao {

    /** Returns the level-1 (top-level) inventory group with the given name, if one exists. */
    @SqlQuery("SELECT * FROM invgrp WHERE lev = 1 AND grpname = :groupName")
    Optional<InvGrp> getGroupByName(@Bind("groupName") String groupName);

    /** Returns how many invgrp rows exist with the given name at the given hierarchy level —
     *  used to verify a create/update by comparing the count before and after. */
    @SqlQuery("SELECT COUNT(*) FROM invgrp WHERE grpname = :groupName AND lev = :level")
    int getGroupCount(@Bind("groupName") String groupName, @Bind("level") int level);
}
