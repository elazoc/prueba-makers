import { LoanDto, UserDto } from '../core/api/dto';
import { seedDb } from './seed';

/** Usuario del simulador. La contraseña va en texto plano porque esto es solo un simulador. */
export interface MockUser extends UserDto {
  password: string;
}

export interface MockLoan extends LoanDto {
  decidedBy: string | null;
}

export interface MockRefresh {
  token: string;
  userId: string;
  familyId: string;
  issuedAt: number;
  familyStartedAt: number;
  rotatedAt: number | null;
  revoked: boolean;
}

export interface MockDb {
  users: MockUser[];
  loans: MockLoan[];
  refresh: MockRefresh[];
}

const DB_KEY = 'prestemos-mock-db';
export const MOCK_STORAGE_PREFIX = 'prestemos-mock-';

export function loadDb(): MockDb {
  const raw = localStorage.getItem(DB_KEY);
  if (raw) return JSON.parse(raw) as MockDb;
  const db = seedDb(new Date());
  saveDb(db);
  return db;
}

export function saveDb(db: MockDb): void {
  localStorage.setItem(DB_KEY, JSON.stringify(db));
}

/** Borra los datos del simulador (base y «cookie»); la próxima lectura vuelve a sembrar. */
export function resetDb(): void {
  for (const key of Object.keys(localStorage)) {
    if (key.startsWith(MOCK_STORAGE_PREFIX)) localStorage.removeItem(key);
  }
}

/**
 * Lee la base, ejecuta y guarda. Cada petición relee para convivir con otras pestañas.
 * Guarda también cuando la operación falla, porque algunas fallas cambian estado
 * (por ejemplo, revocar una familia de refresh tokens ante un reúso).
 */
export function withDb<T>(fn: (db: MockDb) => T): T {
  const db = loadDb();
  try {
    return fn(db);
  } finally {
    saveDb(db);
  }
}
