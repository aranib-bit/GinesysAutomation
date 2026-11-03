package com.gsl.approval.validation;

import com.gsl.approval.model.ApprovalRequest;
import com.gsl.approval.model.PostedState;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DbAvailability;
import com.gsl.database.dao.finance.FinPostDao;
import com.gsl.model.finance.FinPost;

import java.util.List;

/**
 * Posted state from {@code finpost.release_status} ({@code P} = posted). Without database access ({@code db.available=no}) or without any
 * {@code finpost} row yet it reports {@code UNKNOWN}, never a guess.
 */
public class FinPostStateSource implements PostedStateSource {

    @Override
    public PostedState read(ApprovalRequest request) {
        if (!DbAvailability.isAvailable() || request.getDocumentCode() == null) {
            return PostedState.UNKNOWN;
        }
        List<FinPost> rows = DatabaseManager.getJdbi().onDemand(FinPostDao.class).findByEntCode(request.getDocumentCode());
        if (rows.isEmpty()) {
            return PostedState.UNKNOWN;
        }
        return rows.stream().allMatch(r -> "P".equals(r.getReleaseStatus())) ? PostedState.POSTED : PostedState.UNPOSTED;
    }
}
