package com.gsl.model.adm;

import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

/**
 * Maps to admsite — the site (location) master table. Backs GV/AR/AP site resolution and the
 * snapshot-diff rules ({@code GeneralVoucherCreateChangeSets}) that assert a voucher's persisted
 * {@code admsite_code_owner} matches the site actually selected/resolved in the UI. Fetched via
 * {@link com.gsl.database.dao.adm.AdmSiteDao#findByCode(long)} (by primary key) or
 * {@link com.gsl.database.dao.adm.AdmSiteDao#findByName(String)} (reverse lookup from a UI-facing
 * site name, which is not guaranteed globally unique — see that method's Javadoc).
 */
@Data
public class AdmSite {
    private long code;
    private String name;
    private String shrtname;

    /** The Organizational Unit (admou.code) this site belongs to. Round 15 — needed so GV's
     *  site resolution can confirm a candidate Type2/Type3 site shares the SAME OU as the
     *  voucher's own Owner Site; a cross-OU site is invisible to the app's Allocate Sites
     *  picker regardless of GL/user-assignment linkage (confirmed live: "S&CM" is linked to
     *  the same GL and assigned to the same user as valid same-OU sites, but its own
     *  admou_code=3 differs from the Owner Site's admou_code=1, so the picker returns "No
     *  data to display" for it). Nullable — not every admsite row is guaranteed to carry one. */
    @ColumnName("admou_code")
    private Long admouCode;
}
