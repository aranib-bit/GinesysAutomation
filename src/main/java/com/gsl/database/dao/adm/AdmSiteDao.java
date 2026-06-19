package com.gsl.database.dao.adm;

import com.gsl.model.adm.AdmSite;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

@RegisterBeanMapper(AdmSite.class)
public interface AdmSiteDao {

    @SqlQuery("SELECT code, name, shrtname FROM admsite WHERE code = :code")
    AdmSite findByCode(@Bind("code") long code);
}
