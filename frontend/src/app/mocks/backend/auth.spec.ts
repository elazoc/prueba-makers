import { MockDb } from '../db';
import { seedDb } from '../seed';
import { login, logout, refresh, register } from './auth';
import { ACCESS_TTL_SECONDS, REFRESH_TTL_MS, authenticate } from './token';

const NOW = Date.parse('2026-11-25T12:00:00Z');
const problem = (status: number, code: string) => expect.objectContaining({ status, code });

describe('mock auth', () => {
  let db: MockDb;
  beforeEach(() => (db = seedDb(new Date(NOW))));

  it('logs in and issues an access token the mock can read back', () => {
    const { body, refreshToken } = login(db, 'USUARIO@test.com ', '123', NOW);
    expect(body.tokenType).toBe('Bearer');
    expect(body.expiresIn).toBe(ACCESS_TTL_SECONDS);
    expect(body.user).toMatchObject({ email: 'usuario@test.com', role: 'USER', fullName: 'Usuario Demo' });
    expect(body.user).not.toHaveProperty('password');
    expect(refreshToken.length).toBeGreaterThan(20);
    expect(authenticate(db, `Bearer ${body.accessToken}`, NOW).email).toBe('usuario@test.com');
  });

  it('is generic on wrong email or password', () => {
    for (const [email, password] of [
      ['nadie@test.com', '123'],
      ['usuario@test.com', 'mal'],
    ]) {
      expect(() => login(db, email, password, NOW)).toThrow(problem(401, 'INVALID_CREDENTIALS'));
    }
  });

  it('rejects an expired access token', () => {
    const { body } = login(db, 'usuario@test.com', '123', NOW);
    const later = NOW + ACCESS_TTL_SECONDS * 1000 + 1;
    expect(() => authenticate(db, `Bearer ${body.accessToken}`, later)).toThrow(
      problem(401, 'UNAUTHENTICATED'),
    );
    expect(() => authenticate(db, null, NOW)).toThrow(problem(401, 'UNAUTHENTICATED'));
  });

  it('rotates the refresh token and revokes the family on reuse', () => {
    const a = login(db, 'usuario@test.com', '123', NOW);
    const b = refresh(db, a.refreshToken, NOW + 1000);
    expect(b.refreshToken).not.toBe(a.refreshToken);
    expect(b.body.user.email).toBe('usuario@test.com');

    expect(() => refresh(db, a.refreshToken, NOW + 20_000)).toThrow(
      problem(401, 'REFRESH_TOKEN_INVALID'),
    );
    expect(() => refresh(db, b.refreshToken, NOW + 21_000)).toThrow(
      problem(401, 'REFRESH_TOKEN_INVALID'),
    );
  });

  it('does not revoke the family when a rotated token is reused within the grace period', () => {
    const a = login(db, 'usuario@test.com', '123', NOW);
    const b = refresh(db, a.refreshToken, NOW + 1000);
    expect(() => refresh(db, a.refreshToken, NOW + 5000)).toThrow(
      problem(401, 'REFRESH_TOKEN_INVALID'),
    );
    expect(refresh(db, b.refreshToken, NOW + 6000).body.user.email).toBe('usuario@test.com');
  });

  it('limits the absolute lifetime of a session', () => {
    const a = login(db, 'usuario@test.com', '123', NOW);
    const b = refresh(db, a.refreshToken, NOW + 1000);
    expect(() => refresh(db, b.refreshToken, NOW + REFRESH_TTL_MS + 1)).toThrow(
      problem(401, 'REFRESH_TOKEN_INVALID'),
    );
  });

  it('does not refresh a disabled user', () => {
    const a = login(db, 'usuario@test.com', '123', NOW);
    db.users.find((u) => u.email === 'usuario@test.com')!.active = false;
    expect(() => refresh(db, a.refreshToken, NOW + 1)).toThrow(problem(401, 'ACCOUNT_DISABLED'));
  });

  it('fails without a token and logs out by revoking the family', () => {
    expect(() => refresh(db, null, NOW)).toThrow(problem(401, 'REFRESH_TOKEN_INVALID'));
    const a = login(db, 'usuario@test.com', '123', NOW);
    logout(db, a.refreshToken);
    expect(() => refresh(db, a.refreshToken, NOW + 1)).toThrow(problem(401, 'REFRESH_TOKEN_INVALID'));
    expect(() => logout(db, null)).not.toThrow();
  });

  it('registers users with role USER and rejects duplicates and short passwords', () => {
    const user = register(db, { email: 'nuevo@correo.com', password: 'secreto123', fullName: 'Nuevo' }, NOW);
    expect(user.role).toBe('USER');
    expect(() =>
      register(db, { email: 'nuevo@correo.com', password: 'secreto123', fullName: 'Otro' }, NOW),
    ).toThrow(problem(409, 'EMAIL_ALREADY_REGISTERED'));
    expect(() =>
      register(db, { email: 'corta@correo.com', password: '123', fullName: 'Corta' }, NOW),
    ).toThrow(problem(400, 'VALIDATION_ERROR'));
  });
});
