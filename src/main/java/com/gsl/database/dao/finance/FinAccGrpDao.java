package com.gsl.database.dao.finance;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

/**
 * DAO for finaccgrp — Finance Account Group master table.
 */
public interface FinAccGrpDao {

    @SqlQuery("SELECT COUNT(*) FROM finaccgrp WHERE grpname = :name AND parentgrpname = :parent")
    int countByNameAndParent(@Bind("name") String name, @Bind("parent") String parent);

    @SqlQuery("SELECT COUNT(*) FROM finaccgrp WHERE grpname = :name")
    int countByName(@Bind("name") String name);
}
