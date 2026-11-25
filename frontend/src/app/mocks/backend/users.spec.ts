import { MockDb, MockUser } from '../db';
import { seedDb } from '../seed';
import { createUser, deactivateUser, getUser, listUsers, updateUser } from './users';

const NOW = Date.parse('2026-11-25T12:00:00Z');
const problem = (status: number, code: string) => expect.objectContaining({ status, code });

describe('mock users', () => {
  let db: MockDb;
  let admin: MockUser;
  let demo: MockUser;

  beforeEach(() => {
    db = seedDb(new Date(NOW));
    admin = db.users.find((u) => u.email === 'admin@test.com')!;
    demo = db.users.find((u) => u.email === 'usuario@test.com')!;
  });

  it('lists with filters and never exposes passwords', () => {
    const page = listUsers(db, { role: 'ADMIN' });
    expect(page.content.map((u) => u.email)).toEqual(['admin@test.com']);
    expect(JSON.stringify(page)).not.toContain('password');
    expect(listUsers(db, { active: true }).totalElements).toBe(3);
  });

  it('returns 404 for an unknown user', () => {
    expect(() => getUser(db, 'no-existe')).toThrow(problem(404, 'USER_NOT_FOUND'));
  });

  it('creates users with any role and rejects duplicate emails', () => {
    const created = createUser(
      db,
      { email: 'jefe@correo.com', password: 'secreto123', fullName: 'Jefa Nueva', role: 'ADMIN' },
      NOW,
    );
    expect(created.role).toBe('ADMIN');
    expect(() =>
      createUser(db, { email: 'jefe@correo.com', password: 'secreto123', fullName: 'X Y', role: 'USER' }, NOW),
    ).toThrow(problem(409, 'EMAIL_ALREADY_REGISTERED'));
  });

  it('keeps an admin from demoting or deactivating themselves', () => {
    expect(() =>
      updateUser(db, admin, admin.id, { fullName: 'Admin Demo', role: 'USER', active: true }),
    ).toThrow(problem(409, 'CANNOT_DEMOTE_SELF'));
    expect(() =>
      updateUser(db, admin, admin.id, { fullName: 'Admin Demo', role: 'ADMIN', active: false }),
    ).toThrow(problem(409, 'CANNOT_DEACTIVATE_SELF'));
    expect(() => deactivateUser(db, admin, admin.id)).toThrow(problem(409, 'CANNOT_DEACTIVATE_SELF'));
  });

  it('updates and deactivates other users', () => {
    const updated = updateUser(db, admin, demo.id, { fullName: 'Usuario Renombrado', role: 'USER', active: true });
    expect(updated.fullName).toBe('Usuario Renombrado');
    deactivateUser(db, admin, demo.id);
    expect(getUser(db, demo.id).active).toBe(false);
  });
});
