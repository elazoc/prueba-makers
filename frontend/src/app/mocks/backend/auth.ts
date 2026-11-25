import { LoginResponse, RegisterRequest, UserDto } from '../../core/api/dto';
import type { MockDb, MockRefresh, MockUser } from '../db';
import { EMAIL_PATTERN, MockProblem, assertValid } from './problem';
import { REFRESH_TTL_MS, REUSE_GRACE_MS, loginResponse, toUserDto } from './token';

export interface SessionResult {
  body: LoginResponse;
  refreshToken: string;
}

const invalidRefresh = () =>
  new MockProblem(401, 'REFRESH_TOKEN_INVALID', 'Tu sesión venció. Ingresa de nuevo.');
const disabled = () =>
  new MockProblem(401, 'ACCOUNT_DISABLED', 'Tu cuenta está desactivada. Habla con un administrador.');

export function login(db: MockDb, email: string, password: string, nowMs: number): SessionResult {
  const user = findByEmail(db, email);
  if (!user || user.password !== password) {
    throw new MockProblem(401, 'INVALID_CREDENTIALS', 'El correo o la contraseña no coinciden.');
  }
  if (!user.active) throw disabled();
  return issue(db, user, crypto.randomUUID(), nowMs, nowMs);
}

/** Rotación con detección de reúso, como `2026-11-25-refresh-token-backend.md`. */
export function refresh(db: MockDb, token: string | null, nowMs: number): SessionResult {
  const current = token ? db.refresh.find((r) => r.token === token) : undefined;
  if (!current) throw invalidRefresh();

  if (current.revoked) {
    const withinGrace = current.rotatedAt !== null && nowMs - current.rotatedAt <= REUSE_GRACE_MS;
    if (!withinGrace) revokeFamily(db, current.familyId);
    throw invalidRefresh();
  }
  if (nowMs - current.familyStartedAt > REFRESH_TTL_MS) {
    revokeFamily(db, current.familyId);
    throw invalidRefresh();
  }

  const user = db.users.find((u) => u.id === current.userId);
  if (!user) throw invalidRefresh();
  if (!user.active) {
    revokeFamily(db, current.familyId);
    throw disabled();
  }

  current.revoked = true;
  current.rotatedAt = nowMs;
  return issue(db, user, current.familyId, current.familyStartedAt, nowMs);
}

export function logout(db: MockDb, token: string | null): void {
  const current = token ? db.refresh.find((r) => r.token === token) : undefined;
  if (current) revokeFamily(db, current.familyId);
}

export function register(db: MockDb, r: RegisterRequest, nowMs: number): UserDto {
  return toUserDto(addUser(db, { ...r, role: 'USER' }, nowMs));
}

/** Alta compartida por el registro y por la administración de usuarios. */
export function addUser(
  db: MockDb,
  r: RegisterRequest & { role: MockUser['role'] },
  nowMs: number,
): MockUser {
  const email = (r.email ?? '').trim().toLowerCase();
  const fullName = (r.fullName ?? '').trim();
  assertValid([
    ...(EMAIL_PATTERN.test(email) ? [] : [{ field: 'email', message: 'Escribe un correo válido.' }]),
    ...(fullName.length >= 2 && fullName.length <= 100
      ? []
      : [{ field: 'fullName', message: 'El nombre debe tener entre 2 y 100 caracteres.' }]),
    ...((r.password ?? '').length >= 8
      ? []
      : [{ field: 'password', message: 'La contraseña debe tener al menos 8 caracteres.' }]),
  ]);
  if (findByEmail(db, email)) {
    throw new MockProblem(409, 'EMAIL_ALREADY_REGISTERED', 'Ya hay una cuenta con este correo.');
  }
  const user: MockUser = {
    id: crypto.randomUUID(),
    email,
    password: r.password,
    fullName,
    role: r.role,
    active: true,
    createdAt: new Date(nowMs).toISOString(),
  };
  db.users.push(user);
  return user;
}

export function revokeUserSessions(db: MockDb, userId: string): void {
  db.refresh.filter((r) => r.userId === userId).forEach((r) => (r.revoked = true));
}

function issue(
  db: MockDb,
  user: MockUser,
  familyId: string,
  familyStartedAt: number,
  nowMs: number,
): SessionResult {
  const record: MockRefresh = {
    token: randomToken(),
    userId: user.id,
    familyId,
    issuedAt: nowMs,
    familyStartedAt,
    rotatedAt: null,
    revoked: false,
  };
  db.refresh.push(record);
  return { body: loginResponse(user, nowMs), refreshToken: record.token };
}

function revokeFamily(db: MockDb, familyId: string): void {
  db.refresh.filter((r) => r.familyId === familyId).forEach((r) => (r.revoked = true));
}

function findByEmail(db: MockDb, email: string): MockUser | undefined {
  const normalized = (email ?? '').trim().toLowerCase();
  return db.users.find((u) => u.email === normalized);
}

function randomToken(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}
