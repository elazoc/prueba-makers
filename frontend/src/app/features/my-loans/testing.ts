import { LoanDto, LoanEventDto, LoanStatus } from '../../core/api/dto';
import { Page } from '../../core/api/page';

/** Utilidades de prueba de préstamos (solo para specs). */
export function loan(id: string, status: LoanStatus = 'PENDING', amount = '1500000.00'): LoanDto {
  return {
    id,
    applicantId: 'u1',
    amount,
    termMonths: 12,
    status,
    requestedAt: '2026-11-20T15:00:00Z',
    decidedAt: status === 'APPROVED' || status === 'REJECTED' ? '2026-11-21T15:00:00Z' : null,
    rejectionReason: status === 'REJECTED' ? 'Ingresos insuficientes' : null,
  };
}

export function pageOf<T>(items: T[], total = items.length, page = 0): Page<T> {
  return { items, page, size: 20, total, totalPages: Math.max(1, Math.ceil(total / 20)) };
}

export function loanEvent(type: LoanEventDto['type'], loanId: string): LoanEventDto {
  const status: Record<LoanEventDto['type'], LoanStatus> = {
    'loan.requested': 'PENDING',
    'loan.updated': 'PENDING',
    'loan.cancelled': 'CANCELLED',
    'loan.approved': 'APPROVED',
    'loan.rejected': 'REJECTED',
  };
  return {
    eventId: `e-${loanId}`,
    type,
    loanId,
    applicantId: 'u1',
    status: status[type],
    occurredAt: '2026-11-25T12:00:00Z',
  };
}
