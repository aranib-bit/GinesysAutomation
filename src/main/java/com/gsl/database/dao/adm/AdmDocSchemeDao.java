package com.gsl.database.dao.adm;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

/**
 * Forward lookup for {@code admdocscheme} — resolves a document scheme's {@code doccode} from its
 * {@code docname} (e.g. "General Voucher"), so a snapshot-diff rule can assert a voucher header's
 * persisted {@code doccode} matches the scheme its own voucher type is registered under.
 *
 * <p>No DAO/model class existed for this table before this round — every prior reference
 * (AR/AP test-data generation, {@code DbArVoucherDataSource}/{@code DbApVoucherDataSource}) only
 * ever did the REVERSE lookup (resolve {@code docname} from a known {@code doccode}, scoped by
 * menu-shortcode/site) via ad-hoc inline SQL. This is deliberately generic (plain {@code docname}
 * parameter, no "General Voucher" literal baked in) so any voucher domain can resolve its own
 * scheme name the same way — GV's own {@code GeneralVoucherCreateChangeSets} is the only caller
 * for now; AR/AP/Contra can reuse this DAO directly whenever their own Create changesets get the
 * same treatment.
 */
public interface AdmDocSchemeDao {

    /** Returns the {@code doccode} registered for the given {@code docname} (e.g. "General
     *  Voucher"), or {@code null} if no scheme row matches. */
    @SqlQuery("SELECT doccode FROM admdocscheme WHERE docname = :docname")
    Long findDoccodeByDocname(@Bind("docname") String docname);

    /**
     * Reverse lookup — resolves a known {@code doccode} back to its {@code docname}, for report
     * display (e.g. {@code SnapshotValidationReporter} showing "137 (General Voucher)" instead of
     * a bare numeric code). {@code null} if no scheme row matches.
     */
    @SqlQuery("SELECT docname FROM admdocscheme WHERE doccode = :doccode")
    String findDocnameByDoccode(@Bind("doccode") long doccode);
}
