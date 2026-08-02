package com.gsl.api.model.serviceinvoiceadhoc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ServiceInvoice/Save's {@code ownerSite} field — a distinct, reduced shape (13 fields) from
 * {@link RefSite} (29-30 fields), confirmed via HAR. GetOtherDetails only returns one live site
 * object (under {@code refSite}), so this is populated by mapping that same site's data into this
 * shape field-by-field — not by reusing the {@link RefSite} object wholesale, which would send
 * the wrong (larger) shape under the {@code ownerSite} key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerSiteForSave {
    private Integer admou_Code;
    private int code;
    private String ext;
    private String name;
    private String ouname;
    private String shrtname;
    private Integer siteTaxCode;
    private String siteType;
    private String slcode;
    private String slname;
    private String reserveInventory;
    private String gstinno;
    private String stateCode;

    /** Maps a {@link RefSite} (the only live site data GetOtherDetails returns) into this reduced shape. */
    public static OwnerSiteForSave fromRefSite(RefSite refSite) {
        if (refSite == null) {
            return null;
        }
        return OwnerSiteForSave.builder()
                .admou_Code(refSite.getAdmou_Code())
                .code(refSite.getCode())
                .ext(refSite.getExt())
                .name(refSite.getName())
                .ouname(refSite.getOuname())
                .shrtname(refSite.getShrtname())
                .siteTaxCode(refSite.getSiteTaxCode())
                .siteType(refSite.getSiteType())
                .slcode(refSite.getSlcode())
                .slname(refSite.getSlname())
                .reserveInventory(refSite.getReserveInventory())
                .gstinno(refSite.getGstinno())
                .stateCode(refSite.getStateCode())
                .build();
    }
}
