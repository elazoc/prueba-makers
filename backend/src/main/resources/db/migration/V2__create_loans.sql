CREATE TABLE loans (
    id UUID PRIMARY KEY,
    applicant_id UUID NOT NULL REFERENCES users (id),
    amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    term_months INT NOT NULL CHECK (term_months > 0),
    status VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    decided_by UUID REFERENCES users (id),
    rejection_reason VARCHAR(500),
    version BIGINT NOT NULL
);

CREATE INDEX ix_loans_applicant_status ON loans (applicant_id, status);
CREATE INDEX ix_loans_status_requested ON loans (status, requested_at);
