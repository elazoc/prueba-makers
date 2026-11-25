import { MockDb, MockUser } from '../db';
import { SEED_IDS, seedDb } from '../seed';
import { visibleTo } from '../events';
import {
  approveLoan,
  cancelLoan,
  getLoan,
  listAll,
  listMine,
  rejectLoan,
  requestLoan,
  updateLoan,
} from './loans';

const NOW = Date.parse('2026-11-25T12:00:00Z');
const problem = (status: number, code: string) => expect.objectContaining({ status, code });

describe('mock loans', () => {
  let db: MockDb;
  let demo: MockUser;
  let ana: MockUser;
  let admin: MockUser;
  const demoPendingId = '7c1e2a40-5b6d-4e8f-a1b2-000000000001';
  const demoApprovedId = '7c1e2a40-5b6d-4e8f-a1b2-000000000004';

  beforeEach(() => {
    db = seedDb(new Date(NOW));
    demo = db.users.find((u) => u.id === SEED_IDS.demo)!;
    ana = db.users.find((u) => u.id === SEED_IDS.ana)!;
    admin = db.users.find((u) => u.id === SEED_IDS.admin)!;
  });

  it('creates a pending loan and emits loan.requested', () => {
    const { body, events } = requestLoan(db, ana, { amount: '1500000.00', termMonths: 12 }, NOW);
    expect(body).toMatchObject({ status: 'PENDING', amount: '1500000.00', applicantId: ana.id });
    expect(events.map((e) => e.type)).toEqual(['loan.requested']);
  });

  it('only users can request loans', () => {
    expect(() => requestLoan(db, admin, { amount: '1500000.00', termMonths: 12 }, NOW)).toThrow(
      problem(403, 'FORBIDDEN'),
    );
  });

  it('applies the policy with 422', () => {
    expect(() => requestLoan(db, ana, { amount: '99.99', termMonths: 12 }, NOW)).toThrow(
      problem(422, 'LOAN_POLICY_VIOLATION'),
    );
    expect(() => requestLoan(db, ana, { amount: '1000.00', termMonths: 121 }, NOW)).toThrow(
      problem(422, 'LOAN_POLICY_VIOLATION'),
    );
  });

  it('rejects a malformed amount with 400', () => {
    expect(() => requestLoan(db, ana, { amount: '1,5', termMonths: 12 }, NOW)).toThrow(
      problem(400, 'VALIDATION_ERROR'),
    );
  });

  it('allows at most three pending loans', () => {
    requestLoan(db, demo, { amount: '1000.00', termMonths: 6 }, NOW); // la siembra ya trae 2
    expect(() => requestLoan(db, demo, { amount: '1000.00', termMonths: 6 }, NOW)).toThrow(
      problem(422, 'LOAN_POLICY_VIOLATION'),
    );
  });

  it('hides foreign loans with 404', () => {
    expect(() => getLoan(db, ana, demoPendingId)).toThrow(problem(404, 'LOAN_NOT_FOUND'));
    expect(getLoan(db, admin, demoPendingId).id).toBe(demoPendingId);
  });

  it('updates and cancels only pending loans of the owner', () => {
    const { body } = updateLoan(db, demo, demoPendingId, { amount: '2000000.00', termMonths: 24 });
    expect(body).toMatchObject({ amount: '2000000.00', termMonths: 24 });
    expect(() => updateLoan(db, demo, demoApprovedId, { amount: '1000.00', termMonths: 6 })).toThrow(
      problem(409, 'LOAN_NOT_PENDING'),
    );
    expect(() => cancelLoan(db, ana, demoPendingId)).toThrow(problem(404, 'LOAN_NOT_FOUND'));
    expect(cancelLoan(db, demo, demoPendingId).events[0].type).toBe('loan.cancelled');
  });

  it('decides once: a second decision is 409', () => {
    approveLoan(db, admin, demoPendingId, NOW);
    expect(() => rejectLoan(db, admin, demoPendingId, 'Motivo válido', NOW)).toThrow(
      problem(409, 'LOAN_NOT_PENDING'),
    );
  });

  it('requires a 5 to 500 character reason to reject', () => {
    expect(() => rejectLoan(db, admin, demoPendingId, ' mal ', NOW)).toThrow(problem(400, 'VALIDATION_ERROR'));
    const { body } = rejectLoan(db, admin, demoPendingId, '  Ingresos insuficientes  ', NOW);
    expect(body).toMatchObject({ status: 'REJECTED', rejectionReason: 'Ingresos insuficientes' });
    expect(body.decidedAt).not.toBeNull();
  });

  it('only admins decide', () => {
    expect(() => approveLoan(db, demo, demoPendingId, NOW)).toThrow(problem(403, 'FORBIDDEN'));
  });

  it('emits approvals visible to the owner and admins only', () => {
    const { events } = approveLoan(db, admin, demoPendingId, NOW);
    expect(events[0].type).toBe('loan.approved');
    expect(visibleTo(events[0], demo)).toBe(true);
    expect(visibleTo(events[0], admin)).toBe(true);
    expect(visibleTo(events[0], ana)).toBe(false);
  });

  it('keeps requests, updates and cancellations for admins', () => {
    const { events } = requestLoan(db, ana, { amount: '1500000.00', termMonths: 12 }, NOW);
    expect(visibleTo(events[0], ana)).toBe(false);
    expect(visibleTo(events[0], admin)).toBe(true);
  });

  it('lists mine with a status filter, newest first', () => {
    const page = listMine(db, demo, { status: 'PENDING' });
    expect(page.content.every((l) => l.applicantId === demo.id && l.status === 'PENDING')).toBe(true);
    expect(page.totalElements).toBe(2);
    expect(listMine(db, demo, {}).content[0].requestedAt >= listMine(db, demo, {}).content[1].requestedAt).toBe(true);
  });

  it('lists all with applicant data and an exact, case-insensitive email filter', () => {
    const page = listAll(db, { applicantEmail: 'USUARIO@test.com', sort: 'requestedAt,asc' });
    expect(page.content.length).toBe(5);
    expect(page.content.every((l) => l.applicantEmail === 'usuario@test.com')).toBe(true);
    expect(page.content[0].applicantName).toBe('Usuario Demo');
    expect(listAll(db, { applicantEmail: 'usuario@' }).totalElements).toBe(0);
  });
});
