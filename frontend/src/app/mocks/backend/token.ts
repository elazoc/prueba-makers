import { LoginResponse, Role, UserDto } from '../../core/api/dto';
import type { MockDb, MockUser } from '../db';
import { MockProblem } from './problem';

/** Corto a propósito, para que la renovación silenciosa también se ejercite en el simulador. */
export const ACCESS_TTL_SECONDS = 120;
export const REFRESH_TTL_MS = 7 * 24 * 60 * 60 * 1000;
export const REUSE_GRACE_MS = 10_000;

interface Claims {
  sub: string;
  email: string;
  roles: Role[];
  iat: number;
  exp: number;
}

export function toUserDto(u: MockUser): UserDto {
  return {
    id: u.id,
    email: u.email,
    fullName: u.fullName,
    role: u.role,
    active: u.active,
    createdAt: u.createdAt,
  };
}

/** Token con forma de JWT, sin firma real: solo el simulador lo lee. */
export function makeAccessToken(user: MockUser, nowMs: number): string {
  const iat = Math.floor(nowMs / 1000);
  const claims: Claims = {
    sub: user.id,
    email: user.email,
    roles: [user.role],
    iat,
    exp: iat + ACCESS_TTL_SECONDS,
  };
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode(claims)}.sim`;
}

export function loginResponse(user: MockUser, nowMs: number): LoginResponse {
  return {
    accessToken: makeAccessToken(user, nowMs),
    tokenType: 'Bearer',
    expiresIn: ACCESS_TTL_SECONDS,
    user: toUserDto(user),
  };
}

/** Resuelve el usuario del token. El rol y si está activo salen de la base, no del token. */
export function authenticate(db: MockDb, authorization: string | null, nowMs: number): MockUser {
  const unauthenticated = () =>
    new MockProblem(401, 'UNAUTHENTICATED', 'Inicia sesión para continuar.');
  if (!authorization?.startsWith('Bearer ')) throw unauthenticated();

  let claims: Claims;
  try {
    claims = decode<Claims>(authorization.slice(7).split('.')[1]);
  } catch {
    throw unauthenticated();
  }
  if (claims.exp * 1000 < nowMs) throw unauthenticated();

  const user = db.users.find((u) => u.id === claims.sub);
  if (!user) throw unauthenticated();
  if (!user.active) {
    throw new MockProblem(401, 'ACCOUNT_DISABLED', 'Tu cuenta está desactivada. Habla con un administrador.');
  }
  return user;
}

export function requireRole(user: MockUser, role: Role): void {
  if (user.role !== role) {
    throw new MockProblem(403, 'FORBIDDEN', 'No tienes permiso para hacer esto.');
  }
}

function encode(value: unknown): string {
  const bytes = new TextEncoder().encode(JSON.stringify(value));
  const binary = Array.from(bytes, (b) => String.fromCharCode(b)).join('');
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function decode<T>(part: string): T {
  const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(base64);
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
  return JSON.parse(new TextDecoder().decode(bytes)) as T;
}
