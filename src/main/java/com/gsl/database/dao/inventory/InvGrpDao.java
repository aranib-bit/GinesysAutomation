package com.gsl.database.dao.inventory;

import com.gsl.model.inventory.InvGrp;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.Optional;

/**
 * DAO for invgrp — Inventory Group table.
 * Inventory module.
 */
@RegisterBeanMapper(InvGrp.class)
public interface InvGrpDao {

    @SqlQuery("SELECT * FROM invgrp WHERE lev = 1 AND grpname = :groupName")
    Optional<InvGrp> getGroupByName(@Bind("groupName") String groupName);

    @SqlQuery("SELECT COUNT(*) FROM invgrp WHERE grpname = :groupName AND lev = :level")
    int getGroupCount(@Bind("groupName") String groupName, @Bind("level") int level);
}
