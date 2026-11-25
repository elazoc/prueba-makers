package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;
import com.wecaelum.prestemos.domain.model.LoanStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "loans")
class LoanJpaEntity {
    @Id
    private UUID id;

    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 10)
    private LoanStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected LoanJpaEntity() {}

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    UUID getApplicantId() {
        return applicantId;
    }

    void setApplicantId(UUID applicantId) {
        this.applicantId = applicantId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    int getTermMonths() {
        return termMonths;
    }

    void setTermMonths(int termMonths) {
        this.termMonths = termMonths;
    }

    LoanStatus getStatus() {
        return status;
    }

    void setStatus(LoanStatus status) {
        this.status = status;
    }

    Instant getRequestedAt() {
        return requestedAt;
    }

    void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    Instant getDecidedAt() {
        return decidedAt;
    }

    void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    UUID getDecidedBy() {
        return decidedBy;
    }

    void setDecidedBy(UUID decidedBy) {
        this.decidedBy = decidedBy;
    }

    String getRejectionReason() {
        return rejectionReason;
    }

    void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    Long getVersion() {
        return version;
    }

    void setVersion(Long version) {
        this.version = version;
    }
}
