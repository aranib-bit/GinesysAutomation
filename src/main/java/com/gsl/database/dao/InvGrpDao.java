package com.gsl.database.dao;

import com.gsl.model.InvGrp;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.customizer.Bind;
import java.util.Optional;

@RegisterBeanMapper(InvGrp.class)
public interface InvGrpDao {


//    @SqlQuery("SELECT * FROM invgrp WHERE lev = 1 AND grpname = :groupName")
//    Optional<InvGrp> getGroupByName(@Bind("groupName") String groupName);

    // ✅ Count check for before/after save
    //@SqlQuery("SELECT COUNT(*) FROM main.invgrp WHERE grpname = :groupName AND lev = :level")
    @SqlQuery("SELECT COUNT(*) FROM invgrp WHERE grpname = :groupName AND lev = :level")
    int getGroupCount(@Bind("groupName") String groupName, @Bind("level") int level);

}
