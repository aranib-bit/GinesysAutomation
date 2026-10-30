package com.gsl.api.client;

import com.gsl.api.core.ApiClient;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.webapi.model.request.audit.ApplicableAuditModulesRequest;
import com.gsl.api.webapi.model.request.audit.AuditHistoryQuery;
import com.gsl.api.webapi.model.request.audit.AuditModuleLogRequest;
import com.gsl.api.model.audit.AuditRow;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Read-only client for the Audit module ({@code /AuditLog/...}, recorded in the Audit*.har captures): which modules audit, which
 * documents have audit history, and the per-table history rows (full row snapshots, one per version).
 *
 * <p>The date range only selects which documents are listed ({@link #fetchDocuments}); {@link #fetchHistory} returns every version of a
 * document regardless of it.
 */
public class AuditLogApiClient extends ApiClient {

    public static final String MODULE_VOUCHER = "FinVoucher";
    public static final String MODULE_SITE_ALLOCATION = "Finance Site Allocation";

    public AuditLogApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** One entry of {@link #fetchDocuments}: the document code (vchcode for FinVoucher, finpost code for Site Allocation) and its label. */
    public record AuditDoc(String docCode, String docNo, int count) {}

    /** The audit state of one module as {@code GetApplicableAuditModules} reports it. */
    public record ModuleState(String moduleShortCode, boolean enabled, boolean mandatory) {}

    /** POST /AuditLog/GetApplicableAuditModules — the module's state, or {@code null} when it is not listed. */
    public ModuleState fetchModuleState(String moduleShortCode, LocalDate workDate) {
        Response response = postRaw(WebApiEndpoints.Audit.APPLICABLE_MODULES,
                ApplicableAuditModulesRequest.builder().workDate(stamp(workDate)).page(1).start(0).limit(500).build());
        List<Map<String, Object>> items = response.jsonPath().getList("result.items");
        if (items == null) return null;
        for (Map<String, Object> m : items) {
            if (moduleShortCode.equalsIgnoreCase(String.valueOf(m.get("moduleShortCode")))) {
                return new ModuleState(moduleShortCode,
                        "Enabled".equalsIgnoreCase(String.valueOf(m.get("isActive"))),
                        "Yes".equalsIgnoreCase(String.valueOf(m.get("isMandatory"))));
            }
        }
        return null;
    }

    /** POST /AuditLog/GetAuditModuleLog — documents of {@code module} that have audit activity in {@code [from, to]}. */
    public List<AuditDoc> fetchDocuments(String module, LocalDate from, LocalDate to) {
        Response response = postRaw(WebApiEndpoints.Audit.MODULE_LOG, AuditModuleLogRequest.builder()
                .moduleShortCode(module).moduleShortName(MODULE_VOUCHER.equals(module) ? "Voucher" : module)
                .fromDate(stamp(from)).toDate(stamp(to)).user(null).build());
        List<Map<String, Object>> items = response.jsonPath().getList("result");
        List<AuditDoc> docs = new ArrayList<>();
        if (items != null) {
            for (Map<String, Object> m : items) {
                Object count = m.get("count");
                docs.add(new AuditDoc(String.valueOf(m.get("docCode")), String.valueOf(m.get("docNo")),
                        count instanceof Number n ? n.intValue() : 0));
            }
        }
        return docs;
    }

    /** GET /AuditLog/GetTabWiseAuditTransactonDetails — every version of {@code code} in {@code table}, oldest first. */
    public List<AuditRow> fetchHistory(String code, String table, String module, LocalDate from, LocalDate to) {
        Response response = getRawQuery(WebApiEndpoints.Audit.HISTORY, AuditHistoryQuery.builder()
                .code(code).tableName(table).moduleShortCode(module).fromDate(stamp(from)).toDate(stamp(to)).revisionFilter(false).build());
        List<Map<String, Object>> items = response.jsonPath().getList("result");
        List<AuditRow> rows = new ArrayList<>();
        if (items != null) items.forEach(m -> rows.add(new AuditRow(m)));
        rows.sort(Comparator.comparingInt(AuditRow::version));
        return rows;
    }

    private static String stamp(LocalDate d) {
        return d + "T00:00:00";
    }
}
