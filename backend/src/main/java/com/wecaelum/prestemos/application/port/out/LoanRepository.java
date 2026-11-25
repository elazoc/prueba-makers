package com.wecaelum.prestemos.application.port.out;

import com.wecaelum.prestemos.application.model.LoanView;
import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.application.model.PageResult;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.domain.model.UserId;
import java.util.Optional;

public interface LoanRepository {

    Loan save(Loan loan);

    /** Uncached read with a write lock, for commands. */
    Optional<Loan> findByIdForUpdate(LoanId id);

    /** Cached read, for queries. */
    Optional<Loan> findById(LoanId id);

    PageResult<Loan> findByApplicant(UserId applicantId, LoanStatus statusOrNull, PageQuery query);

    PageResult<LoanView> findAll(LoanStatus statusOrNull, Email applicantEmailOrNull, PageQuery query);

    long countByApplicantAndStatus(UserId applicantId, LoanStatus status);
}
