package com.gsl.database.dao.adm;

import com.gsl.model.adm.AdmDocSchemeCandidate;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;

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

    /**
     * Every document scheme mapped to {@code menuShortcode} ({@code admmenu_docscheme}) AND enabled
     * for at least one of {@code siteCodes} ({@code admou_docscheme}), with its current counter state.
     *
     * <p>{@code admlastdoc} is keyed {@code (doccode, ycode, admou_code)} — its own
     * {@code mdmenushortcode} is mostly null, so it is joined on doccode/ycode/OU only. Two
     * aggregates are returned ({@code lastno_year} for the given year, {@code lastno_any} across all
     * years) so the caller can honour the scheme's own reset interval. Ranking and the
     * available/exhausted decision are deliberately NOT done in SQL — see {@code DocSchemeResolver}.
     */
    @SqlQuery("SELECT d.doccode AS doccode, d.docname AS docname, d.width AS width, d.starting AS starting, "
            + "d.manual AS manual, d.generation_interval AS generation_interval, d.ext AS ext, "
            + "(SELECT MAX(l.lastno) FROM admlastdoc l WHERE l.doccode = d.doccode "
            + "   AND l.ycode = :ycode AND l.admou_code = :ouCode) AS lastno_year, "
            + "(SELECT MAX(l2.lastno) FROM admlastdoc l2 WHERE l2.doccode = d.doccode "
            + "   AND l2.admou_code = :ouCode) AS lastno_any "
            + "FROM admdocscheme d "
            + "WHERE d.doccode IN (SELECT mds.doccode FROM admmenu_docscheme mds "
            + "                    WHERE mds.mdmenushortcode = :menuShortcode) "
            + "AND d.doccode IN (SELECT ods.doccode FROM admou_docscheme ods "
            + "                  WHERE ods.admsite_code IN (<siteCodes>)) "
            + "ORDER BY d.doccode")
    @RegisterBeanMapper(AdmDocSchemeCandidate.class)
    List<AdmDocSchemeCandidate> findCandidates(@Bind("ycode") long ycode,
                                               @Bind("ouCode") long ouCode,
                                               @Bind("menuShortcode") String menuShortcode,
                                               @BindList("siteCodes") List<Long> siteCodes);
}
