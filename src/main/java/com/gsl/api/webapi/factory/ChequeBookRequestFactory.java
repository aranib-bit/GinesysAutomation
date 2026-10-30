package com.gsl.api.webapi.factory;

import com.gsl.api.webapi.constants.ChequeBookDefaults;
import com.gsl.api.webapi.model.request.chequebook.ChequeDetailRequest;
import com.gsl.api.webapi.model.request.chequebook.CreateChequeBookRequest;

import java.util.ArrayList;
import java.util.List;

/** Builds valid default Cheque Book requests. Stateless; sends nothing. */
public final class ChequeBookRequestFactory {

    private ChequeBookRequestFactory() {}

    /**
     * A book of {@code leafCount} consecutive leaves starting at {@code start}, numbers zero-padded to {@code width}.
     *
     * @throws IllegalArgumentException when {@code leafCount} or {@code width} is not positive
     * @throws ArithmeticException      when the last number would overflow an int
     */
    public static CreateChequeBookRequest create(long ownerSiteCode, long empCode, long glCode, String workDate, int start, int leafCount, int width) {
        if (leafCount < 1) throw new IllegalArgumentException("leafCount must be greater than zero");
        if (width < 1) throw new IllegalArgumentException("width must be greater than zero");
        int endExclusive = Math.addExact(start, leafCount);

        List<ChequeDetailRequest> details = new ArrayList<>(leafCount);
        for (int n = start; n < endExclusive; n++) {
            details.add(new ChequeDetailRequest(String.format("%0" + width + "d", n), null, null,
                    ChequeBookDefaults.ACTIVE_CHEQUE_TYPE, ChequeBookDefaults.UNUSED_CHEQUE_STATUS));
        }
        return CreateChequeBookRequest.builder()
                .code(ChequeBookDefaults.NEW_BOOK_CODE)
                .ownerSiteCode(ownerSiteCode)
                .dateOfIssue(workDate)
                .remarks(ChequeBookDefaults.DEFAULT_REMARKS)
                .empCode(empCode)
                .glcode(glCode)
                .dataversion(ChequeBookDefaults.INITIAL_DATA_VERSION)
                .chequeDetails(details)
                .build();
    }
}
