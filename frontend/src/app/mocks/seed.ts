import type { MockDb, MockLoan, MockUser } from './db';

const DAY = 24 * 60 * 60 * 1000;

export const SEED_IDS = {
  demo: '0b6f6a0e-2d0f-4c1e-9a51-3f1f0c1d0a01',
  admin: '0b6f6a0e-2d0f-4c1e-9a51-3f1f0c1d0a02',
  ana: '0b6f6a0e-2d0f-4c1e-9a51-3f1f0c1d0a03',
} as const;

/** Misma siembra que el backend: los dos usuarios del enunciado y préstamos en los cuatro estados. */
export function seedDb(now: Date): MockDb {
  const at = (daysAgo: number) => new Date(now.getTime() - daysAgo * DAY).toISOString();

  const users: MockUser[] = [
    {
      id: SEED_IDS.demo,
      email: 'usuario@test.com',
      password: '123',
      fullName: 'Usuario Demo',
      role: 'USER',
      active: true,
      createdAt: at(40),
    },
    {
      id: SEED_IDS.admin,
      email: 'admin@test.com',
      password: '123',
      fullName: 'Admin Demo',
      role: 'ADMIN',
      active: true,
      createdAt: at(40),
    },
    {
      id: SEED_IDS.ana,
      email: 'ana.gomez@correo.com',
      password: 'prestemos123',
      fullName: 'Ana Gómez',
      role: 'USER',
      active: true,
      createdAt: at(32),
    },
  ];

  const loan = (
    n: number,
    applicantId: string,
    amount: string,
    termMonths: number,
    daysAgo: number,
    status: MockLoan['status'],
    rejectionReason: string | null = null,
  ): MockLoan => {
    const decided = status === 'APPROVED' || status === 'REJECTED';
    return {
      id: `7c1e2a40-5b6d-4e8f-a1b2-00000000000${n}`,
      applicantId,
      amount,
      termMonths,
      status,
      requestedAt: at(daysAgo),
      decidedAt: decided ? at(daysAgo - 1) : null,
      decidedBy: decided ? SEED_IDS.admin : null,
      rejectionReason,
    };
  };

  const loans: MockLoan[] = [
    loan(1, SEED_IDS.demo, '1500000.00', 12, 2, 'PENDING'),
    loan(2, SEED_IDS.demo, '4200000.00', 24, 1, 'PENDING'),
    loan(3, SEED_IDS.ana, '850000.00', 6, 3, 'PENDING'),
    loan(4, SEED_IDS.demo, '800000.00', 6, 15, 'APPROVED'),
    loan(5, SEED_IDS.ana, '12000000.00', 36, 20, 'APPROVED'),
    loan(
      6,
      SEED_IDS.demo,
      '30000000.00',
      60,
      23,
      'REJECTED',
      'El monto supera tu capacidad de pago con los ingresos que registraste.',
    ),
    loan(7, SEED_IDS.demo, '2500000.00', 18, 28, 'CANCELLED'),
  ];

  return { users, loans, refresh: [] };
}
