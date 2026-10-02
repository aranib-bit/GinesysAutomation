package com.gsl.database.dao.finance;

import com.gsl.model.finance.FinChqMain;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

/**
 * DAO for finchqmain — Cheque Book Master. New in Round 7, see {@link FinChqDetDao}'s Javadoc.
 */
@RegisterBeanMapper(FinChqMain.class)
public interface FinChqMainDao {

    /** Looks up the cheque book master row by its {@code bookcode} primary key. */
    @SqlQuery("SELECT * FROM finchqmain WHERE bookcode = :bookcode")
    FinChqMain findByBookcode(@Bind("bookcode") long bookcode);
}
