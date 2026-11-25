package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import com.wecaelum.prestemos.application.model.LoanView;
import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.application.model.PageResult;
import com.wecaelum.prestemos.application.port.out.LoanRepository;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Loan;
import com.wecaelum.prestemos.domain.model.LoanId;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import com.wecaelum.prestemos.domain.model.UserId;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
class LoanPersistenceAdapter implements LoanRepository {

    private final SpringDataLoanRepository repository;

    LoanPersistenceAdapter(SpringDataLoanRepository repository) {
        this.repository = repository;
    }

    @Override
    public Loan save(Loan loan) {
        return LoanJpaMapper.toDomain(repository.saveAndFlush(LoanJpaMapper.toEntity(loan)));
    }

    @Override
    public Optional<Loan> findByIdForUpdate(LoanId id) {
        return repository.findById(id.value()).map(LoanJpaMapper::toDomain);
    }

    @Override
    public Optional<Loan> findById(LoanId id) {
        return repository.findById(id.value()).map(LoanJpaMapper::toDomain);
    }

    @Override
    public PageResult<Loan> findByApplicant(UserId applicantId, LoanStatus statusOrNull, PageQuery query) {
        Page<LoanJpaEntity> page =
                repository.findByApplicant(applicantId.value(), statusOrNull, SortWhitelist.loans(query));
        return new PageResult<>(
                page.getContent().stream().map(LoanJpaMapper::toDomain).toList(),
                query.page(), query.size(), page.getTotalElements());
    }

    @Override
    public PageResult<LoanView> findAll(LoanStatus statusOrNull, Email applicantEmailOrNull, PageQuery query) {
        Page<Object[]> page = repository.findAllWithApplicant(
                statusOrNull,
                applicantEmailOrNull == null ? null : applicantEmailOrNull.value(),
                SortWhitelist.loans(query, "l."));
        return new PageResult<>(
                page.getContent().stream()
                        .map(row -> new LoanView(
                                LoanJpaMapper.toDomain((LoanJpaEntity) row[0]), (String) row[1], (String) row[2]))
                        .toList(),
                query.page(), query.size(), page.getTotalElements());
    }

    @Override
    public long countByApplicantAndStatus(UserId applicantId, LoanStatus status) {
        return repository.countByApplicantIdAndStatus(applicantId.value(), status);
    }
}
