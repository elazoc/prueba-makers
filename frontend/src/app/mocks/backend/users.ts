import { CreateUserRequest, PageDto, Role, UpdateUserRequest, UserDto } from '../../core/api/dto';
import type { MockDb, MockUser } from '../db';
import { addUser, revokeUserSessions } from './auth';
import { MockPageQuery, paginate, sortBy } from './paging';
import { MockProblem, assertValid } from './problem';
import { toUserDto } from './token';

export function me(user: MockUser): UserDto {
  return toUserDto(user);
}

export function listUsers(
  db: MockDb,
  q: MockPageQuery & { role?: Role; active?: boolean },
): PageDto<UserDto> {
  const filtered = db.users.filter(
    (u) => (q.role === undefined || u.role === q.role) && (q.active === undefined || u.active === q.active),
  );
  const sorted = sortBy(filtered, q.sort, ['createdAt', 'email', 'fullName'], 'createdAt,desc');
  return paginate(sorted.map(toUserDto), q);
}

export function getUser(db: MockDb, id: string): UserDto {
  return toUserDto(find(db, id));
}

export function createUser(db: MockDb, r: CreateUserRequest, nowMs: number): UserDto {
  return toUserDto(addUser(db, r, nowMs));
}

export function updateUser(db: MockDb, actor: MockUser, id: string, r: UpdateUserRequest): UserDto {
  const user = find(db, id);
  if (actor.id === id && r.role !== actor.role) {
    throw new MockProblem(409, 'CANNOT_DEMOTE_SELF', 'No puedes quitarte el rol de administrador.');
  }
  if (actor.id === id && r.active === false) {
    throw new MockProblem(409, 'CANNOT_DEACTIVATE_SELF', 'No puedes desactivar tu propia cuenta.');
  }
  const fullName = (r.fullName ?? '').trim();
  assertValid(
    fullName.length >= 2 && fullName.length <= 100
      ? []
      : [{ field: 'fullName', message: 'El nombre debe tener entre 2 y 100 caracteres.' }],
  );
  user.fullName = fullName;
  user.role = r.role;
  user.active = r.active;
  if (!user.active) revokeUserSessions(db, user.id);
  return toUserDto(user);
}

export function deactivateUser(db: MockDb, actor: MockUser, id: string): void {
  const user = find(db, id);
  if (actor.id === id) {
    throw new MockProblem(409, 'CANNOT_DEACTIVATE_SELF', 'No puedes desactivar tu propia cuenta.');
  }
  user.active = false;
  revokeUserSessions(db, user.id);
}

function find(db: MockDb, id: string): MockUser {
  const user = db.users.find((u) => u.id === id);
  if (!user) throw new MockProblem(404, 'USER_NOT_FOUND', 'No encontramos este usuario.');
  return user;
}
