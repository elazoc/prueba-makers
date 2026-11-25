import { HttpResponse, delay, http, type RequestHandler } from 'msw';
import {
  CreateUserRequest,
  LoanRequest,
  LoanStatus,
  RegisterRequest,
  RejectRequest,
  Role,
  UpdateUserRequest,
} from '../core/api/dto';
import { cookieJar } from './cookie-jar';
import { MockDb, MockUser, loadDb, withDb } from './db';
import { mockEvents, sseFrame, visibleTo } from './events';
import { login, logout, refresh, register } from './backend/auth';
import {
  CommandResult,
  approveLoan,
  cancelLoan,
  getLoan,
  listAll,
  listMine,
  rejectLoan,
  requestLoan,
  updateLoan,
} from './backend/loans';
import { MockProblem, problemJson } from './backend/problem';
import { authenticate, requireRole } from './backend/token';
import { createUser, deactivateUser, getUser, listUsers, me, updateUser } from './backend/users';

const API = '/api/v1';
export const MOCK_DELAY_MS = 300;
const HEARTBEAT_MS = 15_000;

type Params = Record<string, string | readonly string[] | undefined>;
interface Ctx {
  request: Request;
  params: Params;
}

/** Envuelve cada ruta: demora, errores en Problem Details. */
const route =
  (fn: (ctx: Ctx, nowMs: number) => Response | Promise<Response>) =>
  async (ctx: Ctx): Promise<Response> => {
    await delay(MOCK_DELAY_MS);
    try {
      return await fn(ctx, Date.now());
    } catch (e) {
      if (e instanceof MockProblem) {
        return HttpResponse.json(problemJson(e), {
          status: e.status,
          headers: { 'Content-Type': 'application/problem+json' },
        });
      }
      throw e;
    }
  };

/** Ejecuta con la base y el usuario del token. */
const asUser = <T>(ctx: Ctx, nowMs: number, fn: (db: MockDb, user: MockUser) => T): T =>
  withDb((db) => fn(db, authenticate(db, ctx.request.headers.get('Authorization'), nowMs)));

const asAdmin = <T>(ctx: Ctx, nowMs: number, fn: (db: MockDb, user: MockUser) => T): T =>
  asUser(ctx, nowMs, (db, user) => {
    requireRole(user, 'ADMIN');
    return fn(db, user);
  });

const requireCsrfHeader = (request: Request) => {
  if (request.headers.get('X-Requested-With') !== 'prestemos') {
    throw new MockProblem(403, 'FORBIDDEN', 'Falta la cabecera de seguridad de la sesión.');
  }
};

const id = (params: Params) => String(params['id']);

function query(request: Request) {
  const p = new URL(request.url).searchParams;
  const num = (k: string) => (p.has(k) ? Number(p.get(k)) : undefined);
  const str = (k: string) => p.get(k) ?? undefined;
  return {
    page: num('page'),
    size: num('size'),
    sort: str('sort'),
    status: str('status') as LoanStatus | undefined,
    applicantEmail: str('applicantEmail'),
    role: str('role') as Role | undefined,
    active: p.has('active') ? p.get('active') === 'true' : undefined,
  };
}

/** Publica los eventos de un comando y devuelve su cuerpo. */
function publish<T>(res: CommandResult<T>): T {
  res.events.forEach((e) => mockEvents.publish(e));
  return res.body;
}

const created = (body: object, location: string) =>
  HttpResponse.json(body, { status: 201, headers: { Location: location } });

function eventStream(user: MockUser, request: Request): Response {
  const encoder = new TextEncoder();
  let cleanup = () => undefined as void;

  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      const send = (text: string) => controller.enqueue(encoder.encode(text));
      send(': conectado\n\n');
      const leave = mockEvents.subscribe((e) => {
        if (visibleTo(e, user)) send(sseFrame(e));
      });
      const ping = setInterval(() => send(': ping\n\n'), HEARTBEAT_MS);
      cleanup = () => {
        leave();
        clearInterval(ping);
      };
      request.signal.addEventListener('abort', () => {
        cleanup();
        try {
          controller.close();
        } catch {
          // ya estaba cerrado
        }
      });
    },
    cancel() {
      cleanup();
    },
  });

  return new HttpResponse(stream, {
    headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' },
  });
}

export const handlers: RequestHandler[] = [
  // Sesión
  http.post(
    `${API}/auth/login`,
    route(async ({ request }, now) => {
      const { email, password } = (await request.json()) as { email: string; password: string };
      const res = withDb((db) => login(db, email, password, now));
      cookieJar.set(res.refreshToken);
      return HttpResponse.json(res.body);
    }),
  ),
  http.post(
    `${API}/auth/register`,
    route(async ({ request }, now) => {
      const body = (await request.json()) as RegisterRequest;
      const user = withDb((db) => register(db, body, now));
      return created(user, `${API}/users/${user.id}`);
    }),
  ),
  http.post(
    `${API}/auth/refresh`,
    route(({ request }, now) => {
      requireCsrfHeader(request);
      const res = withDb((db) => refresh(db, cookieJar.get(), now));
      cookieJar.set(res.refreshToken);
      return HttpResponse.json(res.body);
    }),
  ),
  http.post(
    `${API}/auth/logout`,
    route(({ request }) => {
      requireCsrfHeader(request);
      withDb((db) => logout(db, cookieJar.get()));
      cookieJar.clear();
      return new HttpResponse(null, { status: 204 });
    }),
  ),

  // Usuarios
  http.get(`${API}/users/me`, route((ctx, now) => HttpResponse.json(asUser(ctx, now, (_db, u) => me(u))))),
  http.get(
    `${API}/users`,
    route((ctx, now) => HttpResponse.json(asAdmin(ctx, now, (db) => listUsers(db, query(ctx.request))))),
  ),
  http.get(
    `${API}/users/:id`,
    route((ctx, now) => HttpResponse.json(asAdmin(ctx, now, (db) => getUser(db, id(ctx.params))))),
  ),
  http.post(
    `${API}/users`,
    route(async (ctx, now) => {
      const body = (await ctx.request.json()) as CreateUserRequest;
      const user = asAdmin(ctx, now, (db) => createUser(db, body, now));
      return created(user, `${API}/users/${user.id}`);
    }),
  ),
  http.put(
    `${API}/users/:id`,
    route(async (ctx, now) => {
      const body = (await ctx.request.json()) as UpdateUserRequest;
      return HttpResponse.json(asAdmin(ctx, now, (db, actor) => updateUser(db, actor, id(ctx.params), body)));
    }),
  ),
  http.delete(
    `${API}/users/:id`,
    route((ctx, now) => {
      asAdmin(ctx, now, (db, actor) => deactivateUser(db, actor, id(ctx.params)));
      return new HttpResponse(null, { status: 204 });
    }),
  ),

  // Préstamos (la ruta de eventos va antes que /loans/:id)
  http.get(`${API}/loans/events`, async ({ request }) => {
    try {
      const user = authenticate(loadDb(), request.headers.get('Authorization'), Date.now());
      return eventStream(user, request);
    } catch (e) {
      if (e instanceof MockProblem) return HttpResponse.json(problemJson(e), { status: e.status });
      throw e;
    }
  }),
  http.get(
    `${API}/me/loans`,
    route((ctx, now) => HttpResponse.json(asUser(ctx, now, (db, u) => listMine(db, u, query(ctx.request))))),
  ),
  http.get(
    `${API}/loans`,
    route((ctx, now) => HttpResponse.json(asAdmin(ctx, now, (db) => listAll(db, query(ctx.request))))),
  ),
  http.post(
    `${API}/loans`,
    route(async (ctx, now) => {
      const body = (await ctx.request.json()) as LoanRequest;
      const loan = publish(asUser(ctx, now, (db, u) => requestLoan(db, u, body, now)));
      return created(loan, `${API}/loans/${loan.id}`);
    }),
  ),
  http.get(
    `${API}/loans/:id`,
    route((ctx, now) => HttpResponse.json(asUser(ctx, now, (db, u) => getLoan(db, u, id(ctx.params))))),
  ),
  http.put(
    `${API}/loans/:id`,
    route(async (ctx, now) => {
      const body = (await ctx.request.json()) as LoanRequest;
      return HttpResponse.json(publish(asUser(ctx, now, (db, u) => updateLoan(db, u, id(ctx.params), body))));
    }),
  ),
  http.delete(
    `${API}/loans/:id`,
    route((ctx, now) => {
      publish(asUser(ctx, now, (db, u) => cancelLoan(db, u, id(ctx.params))));
      return new HttpResponse(null, { status: 204 });
    }),
  ),
  http.post(
    `${API}/loans/:id/approve`,
    route((ctx, now) =>
      HttpResponse.json(publish(asUser(ctx, now, (db, u) => approveLoan(db, u, id(ctx.params), now)))),
    ),
  ),
  http.post(
    `${API}/loans/:id/reject`,
    route(async (ctx, now) => {
      const { reason } = (await ctx.request.json()) as RejectRequest;
      return HttpResponse.json(
        publish(asUser(ctx, now, (db, u) => rejectLoan(db, u, id(ctx.params), reason, now))),
      );
    }),
  ),
];
