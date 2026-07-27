package com.gsl.database.dao.finance;

import com.gsl.model.finance.AdmCls;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.Optional;

/**
 * DAO for admcls — Sub-Ledger Classification master table (also used for Account Class).
 * See {@link AdmCls} for the column meanings.
 */
@RegisterBeanMapper(AdmCls.class)
public interface FinAccClassDao {

    @SqlQuery("SELECT COUNT(*) FROM admcls WHERE clsname = :name")
    int countByName(@Bind("name") String name);

    @SqlQuery("SELECT clscode, cmpcode, clsname, clstype, ext FROM admcls WHERE clsname = :name")
    Optional<AdmCls> findByName(@Bind("name") String name);

    /**
     * Live max length of admcls.clsname, read from the schema at test time rather than assumed —
     * the boundary-length test builds its exact-max and max+1 names from this value so it stays
     * correct even if the column is ever resized.
     */
    @SqlQuery("SELECT character_maximum_length FROM information_schema.columns " +
              "WHERE table_name = 'admcls' AND column_name = 'clsname'")
    int getClassNameMaxLength();
}
