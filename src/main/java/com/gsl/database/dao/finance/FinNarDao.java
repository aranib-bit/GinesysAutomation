package com.gsl.database.dao.finance;

import com.gsl.model.finance.FinNar;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.Optional;

/**
 * DAO for finnar — GL-scoped registered narration texts.
 */
@RegisterBeanMapper(FinNar.class)
public interface FinNarDao {

    @SqlQuery("SELECT narcode, nartext, glcode FROM finnar WHERE glcode = :glcode AND nartext = :nartext")
    Optional<FinNar> findByGlcodeAndNartext(@Bind("glcode") long glcode, @Bind("nartext") String nartext);
}
