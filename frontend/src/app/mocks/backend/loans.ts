import {
  AdminLoanDto,
  LoanDto,
  LoanEventDto,
  LoanEventType,
  LoanRequest,
  LoanStatus,
  PageDto,
} from '../../core/api/dto';
import { formatMoney, toCents } from '../../shared/money/money';
import { LOAN_POLICY } from '../../shared/forms/loan-policy';
import type { MockDb, MockLoan, MockUser } from '../db';
import { MockPageQuery, paginate, sortBy } from './paging';
import { MockProblem, assertValid } from './problem';
import { requireRole } from './token';

export interface CommandResult<T> {
  body: T;
  events: LoanEventDto[];
}

const SORTABLE = ['requestedAt', 'amount', 'status'];
const AMOUNT_SHAPE = /^\d{1,13}(\.\d{1,2})?$/;

export function requestLoan(
  db: MockDb,
  actor: MockUser,
  r: LoanRequest,
  nowMs: number,
): CommandResult<LoanDto> {
  requireRole(actor, 'USER');
  const amount = checkShape(r);
  checkPolicy(amount, r.termMonths);
  const pending = db.loans.filter((l) => l.applicantId === actor.id && l.status === 'PENDING').length;
  if (pending >= LOAN_POLICY.maxPending) {
    throw new MockProblem(
      422,
      'LOAN_POLICY_VIOLATION',
      `Ya tienes ${LOAN_POLICY.maxPending} solicitudes pendientes. Espera una decisión o cancela una.`,
    );
  }
  const loan: MockLoan = {
    id: crypto.randomUUID(),
    applicantId: actor.id,
    amount,
    termMonths: r.termMonths,
    status: 'PENDING',
    requestedAt: new Date(nowMs).toISOString(),
    decidedAt: null,
    decidedBy: null,
    rejectionReason: null,
  };
  db.loans.push(loan);
  return result(loan, 'loan.requested', nowMs);
}

export function updateLoan(db: MockDb, actor: MockUser, id: string, r: LoanRequest): CommandResult<LoanDto> {
  const loan = ownLoan(db, actor, id);
  assertPending(loan);
  const amount = checkShape(r);
  checkPolicy(amount, r.termMonths);
  loan.amount = amount;
  loan.termMonths = r.termMonths;
  return result(loan, 'loan.updated', Date.now());
}

export function cancelLoan(db: MockDb, actor: MockUser, id: string): CommandResult<void> {
  const loan = ownLoan(db, actor, id);
  assertPending(loan);
  loan.status = 'CANCELLED';
  return { body: undefined, events: [event(loan, 'loan.cancelled', Date.now())] };
}

export function approveLoan(db: MockDb, actor: MockUser, id: string, nowMs: number): CommandResult<LoanDto> {
  requireRole(actor, 'ADMIN');
  const loan = findLoan(db, id);
  assertPending(loan);
  loan.status = 'APPROVED';
  loan.decidedAt = new Date(nowMs).toISOString();
  loan.decidedBy = actor.id;
  return result(loan, 'loan.approved', nowMs);
}

export function rejectLoan(
  db: MockDb,
  actor: MockUser,
  id: string,
  reason: string,
  nowMs: number,
): CommandResult<LoanDto> {
  requireRole(actor, 'ADMIN');
  const trimmed = (reason ?? '').trim();
  assertValid(
    trimmed.length >= 5 && trimmed.length <= 500
      ? []
      : [{ field: 'reason', message: 'El motivo debe tener entre 5 y 500 caracteres.' }],
  );
  const loan = findLoan(db, id);
  assertPending(loan);
  loan.status = 'REJECTED';
  loan.rejectionReason = trimmed;
  loan.decidedAt = new Date(nowMs).toISOString();
  loan.decidedBy = actor.id;
  return result(loan, 'loan.rejected', nowMs);
}

/** Detalle: el dueño o un administrador. Un préstamo ajeno responde 404, no 403. */
export function getLoan(db: MockDb, actor: MockUser, id: string): LoanDto {
  const loan = findLoan(db, id);
  if (actor.role !== 'ADMIN' && loan.applicantId !== actor.id) throw notFound();
  return toDto(loan);
}

export function listMine(
  db: MockDb,
  actor: MockUser,
  q: MockPageQuery & { status?: LoanStatus },
): PageDto<LoanDto> {
  requireRole(actor, 'USER');
  const mine = db.loans.filter(
    (l) => l.applicantId === actor.id && (q.status === undefined || l.status === q.status),
  );
  return paginate(sortBy(mine, q.sort, SORTABLE, 'requestedAt,desc').map(toDto), q);
}

export function listAll(
  db: MockDb,
  q: MockPageQuery & { status?: LoanStatus; applicantEmail?: string },
): PageDto<AdminLoanDto> {
  const email = q.applicantEmail?.trim().toLowerCase();
  const rows = db.loans
    .filter((l) => q.status === undefined || l.status === q.status)
    .map((l) => {
      const applicant = db.users.find((u) => u.id === l.applicantId);
      return {
        ...toDto(l),
        applicantEmail: applicant?.email ?? '',
        applicantName: applicant?.fullName ?? '',
      };
    })
    .filter((l) => !email || l.applicantEmail === email);
  return paginate(sortBy(rows, q.sort, SORTABLE, 'requestedAt,desc'), q);
}

function checkShape(r: LoanRequest): string {
  const errors: { field: string; message: string }[] = [];
  if (typeof r.amount !== 'string' || !AMOUNT_SHAPE.test(r.amount)) {
    errors.push({ field: 'amount', message: 'Escribe un monto positivo con máximo 2 decimales.' });
  }
  if (!Number.isInteger(r.termMonths) || r.termMonths <= 0) {
    errors.push({ field: 'termMonths', message: 'Escribe el plazo en meses enteros.' });
  }
  assertValid(errors);
  const [integer, decimals = ''] = r.amount.split('.');
  return `${integer.replace(/^0+(?=\d)/, '')}.${decimals.padEnd(2, '0')}`;
}

function checkPolicy(amount: string, termMonths: number): void {
  const cents = toCents(amount);
  if (cents < toCents(LOAN_POLICY.minAmount)) {
    throw policy(`El monto mínimo es ${formatMoney(LOAN_POLICY.minAmount)}.`);
  }
  if (cents > toCents(LOAN_POLICY.maxAmount)) {
    throw policy(`El monto máximo es ${formatMoney(LOAN_POLICY.maxAmount)}.`);
  }
  if (termMonths < LOAN_POLICY.minTermMonths || termMonths > LOAN_POLICY.maxTermMonths) {
    throw policy(`El plazo va de ${LOAN_POLICY.minTermMonths} a ${LOAN_POLICY.maxTermMonths} meses.`);
  }
}

function policy(detail: string): MockProblem {
  return new MockProblem(422, 'LOAN_POLICY_VIOLATION', detail);
}

function assertPending(loan: MockLoan): void {
  if (loan.status !== 'PENDING') {
    throw new MockProblem(409, 'LOAN_NOT_PENDING', 'Este préstamo ya no está pendiente. Recarga para ver su estado.');
  }
}

function ownLoan(db: MockDb, actor: MockUser, id: string): MockLoan {
  requireRole(actor, 'USER');
  const loan = findLoan(db, id);
  if (loan.applicantId !== actor.id) throw notFound();
  return loan;
}

function findLoan(db: MockDb, id: string): MockLoan {
  const loan = db.loans.find((l) => l.id === id);
  if (!loan) throw notFound();
  return loan;
}

function notFound(): MockProblem {
  return new MockProblem(404, 'LOAN_NOT_FOUND', 'No encontramos este préstamo.');
}

function toDto(l: MockLoan): LoanDto {
  return {
    id: l.id,
    applicantId: l.applicantId,
    amount: l.amount,
    termMonths: l.termMonths,
    status: l.status,
    requestedAt: l.requestedAt,
    decidedAt: l.decidedAt,
    rejectionReason: l.rejectionReason,
  };
}

function event(l: MockLoan, type: LoanEventType, nowMs: number): LoanEventDto {
  return {
    eventId: crypto.randomUUID(),
    type,
    loanId: l.id,
    applicantId: l.applicantId,
    status: l.status,
    occurredAt: new Date(nowMs).toISOString(),
  };
}

function result(l: MockLoan, type: LoanEventType, nowMs: number): CommandResult<LoanDto> {
  return { body: toDto(l), events: [event(l, type, nowMs)] };
}
