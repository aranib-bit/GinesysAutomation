package com.gsl.database.dao.adm;

import com.gsl.model.adm.AdmSite;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

/**
 * DAO for admsite — the site (location) master table. Backs GV/AR/AP site resolution and the
 * snapshot-diff rules ({@code GeneralVoucherCreateChangeSets}) that assert a voucher's persisted
 * {@code admsite_code_owner} matches the site actually selected/resolved in the UI.
 */
@RegisterBeanMapper(AdmSite.class)
public interface AdmSiteDao {

    /** Returns the {@link AdmSite} row for the given {@code code}. */
    // Round 15: admou_code added so callers (GV's site-resolution OU-consistency check) can read
    // a site's own Organizational Unit off the same row they already fetch — no second query.
    @SqlQuery("SELECT code, name, shrtname, admou_code, ext FROM admsite WHERE code = :code")
    AdmSite findByCode(@Bind("code") long code);

    /** Reverse lookup — resolves a site's DB row from its UI-facing name (e.g. the "Owner Site"
     *  value a test picked/typed in the UI), so a snapshot-diff rule can assert the persisted
     *  {@code admsite_code_owner} matches the site the user actually selected. Site names aren't
     *  guaranteed globally unique, so this returns the first match — same "first usable" tolerance
     *  {@code DbGeneralVoucherDataSource.findFirstUsableOwnerSite} already accepts informally.
     *
     *  <p>Bug fix (2026-09): this query used to have no {@code sitetype}/{@code ext} filter and no
     *  {@code ORDER BY} at all — when a name collided with more than one {@code admsite} row, it
     *  could resolve a genuinely DIFFERENT row than the one
     *  {@code DbGeneralVoucherDataSource.findFirstUsableOwnerSite} actually picked and the UI
     *  actually persisted against, producing spurious {@code admsite_code_owner}/
     *  {@code fincosttag.admsite_code} MUST_CHANGE mismatches with no real data bug. Now filters/
     *  orders identically to that method ({@code sitetype = 'OS-OO-CM' AND ext = 'N'}, ordered by
     *  {@code code}) so the two are guaranteed to agree whenever a name collision exists. */
    @SqlQuery("SELECT code, name, shrtname, admou_code FROM admsite WHERE name = :name "
            + "AND sitetype = 'OS-OO-CM' AND ext = 'N' ORDER BY code")
    java.util.List<AdmSite> findByName(@Bind("name") String name);
}
