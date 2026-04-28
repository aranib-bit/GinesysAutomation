package com.gsl.validation.db.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinPostDao;
import com.gsl.model.finance.FinPost;
import com.gsl.reports.ExtentLogger;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures a point-in-time snapshot of invoice-side finpost rows.
 * Finance module — called before and after AR Voucher creation.
 *
 * NOTE: If the posting engine is asynchronous, poll finvchmain.release_status
 * until POSTED before calling captureInvoiceState for the post-creation snapshot.
 */
public class StateCapture {

    private final FinPostDao finPostDao;

    public StateCapture() {
        this.finPostDao = DatabaseManager.getJdbi().onDemand(FinPostDao.class);
    }

    /**
     * Queries finpost for each invoice number and returns a keyed snapshot.
     *
     * @param invoiceDocNos invoice entno values (e.g. ["INV-001", "INV-002"])
     * @return map of invoiceNo → FinPost; null value if no finpost row found
     */
    public Map<String, FinPost> captureInvoiceState(List<String> invoiceDocNos) {
        Map<String, FinPost> snapshot = new LinkedHashMap<>();

        for (String invoiceNo : invoiceDocNos) {
            List<FinPost> rows = finPostDao.findByEntNo(invoiceNo);

            FinPost arRow = rows.stream()
                    .filter(r -> r.getCamount() != null
                            && r.getCamount().compareTo(BigDecimal.ZERO) > 0)
                    .findFirst()
                    .orElse(rows.isEmpty() ? null : rows.get(0));

            snapshot.put(invoiceNo, arRow);

            if (arRow == null)
                ExtentLogger.info("StateCapture — no finpost row for invoice [" + invoiceNo + "]");
        }

        ExtentLogger.info("StateCapture — captured state for " + snapshot.size() + " invoice(s)");
        return snapshot;
    }
}
