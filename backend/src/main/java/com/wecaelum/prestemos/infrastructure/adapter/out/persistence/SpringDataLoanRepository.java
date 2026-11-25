package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import com.wecaelum.prestemos.domain.model.LoanStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataLoanRepository extends JpaRepository<LoanJpaEntity, UUID> {

    @Query("select l from LoanJpaEntity l where l.applicantId = :applicantId "
            + "and (:status is null or l.status = :status)")
    Page<LoanJpaEntity> findByApplicant(
            @Param("applicantId") UUID applicantId, @Param("status") LoanStatus status, Pageable pageable);

    @Query(
            value = "select l, u.email, u.fullName from LoanJpaEntity l join UserJpaEntity u on u.id = l.applicantId "
                    + "where (:status is null or l.status = :status) and (:email is null or u.email = :email)",
            countQuery = "select count(l) from LoanJpaEntity l join UserJpaEntity u on u.id = l.applicantId "
                    + "where (:status is null or l.status = :status) and (:email is null or u.email = :email)")
    Page<Object[]> findAllWithApplicant(
            @Param("status") LoanStatus status, @Param("email") String email, Pageable pageable);

    long countByApplicantIdAndStatus(UUID applicantId, LoanStatus status);
}
