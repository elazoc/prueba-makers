import { setupServer } from 'msw/node';
import { cookieJar } from './cookie-jar';
import { resetDb } from './db';
import { handlers } from './handlers';

const server = setupServer(...handlers);
const url = (path: string) => new URL(path, location.origin).toString();
const json = { 'Content-Type': 'application/json' };
const session = { ...json, 'X-Requested-With': 'prestemos' };

async function loginAs(email: string, password = '123') {
  const res = await fetch(url('/api/v1/auth/login'), {
    method: 'POST',
    headers: session,
    body: JSON.stringify({ email, password }),
  });
  return (await res.json()) as { accessToken: string };
}

describe('mock handlers (HTTP end to end)', () => {
  beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
  afterAll(() => server.close());
  beforeEach(() => resetDb());

  it('logs in, sets the simulated cookie and refreshes with it', async () => {
    const { accessToken } = await loginAs('usuario@test.com');
    expect(accessToken).toContain('.');
    const firstCookie = cookieJar.get();
    expect(firstCookie).toBeTruthy();

    const refreshed = await fetch(url('/api/v1/auth/refresh'), { method: 'POST', headers: session });
    expect(refreshed.status).toBe(200);
    expect(cookieJar.get()).not.toBe(firstCookie);
  });

  it('requires the CSRF header on refresh', async () => {
    await loginAs('usuario@test.com');
    const res = await fetch(url('/api/v1/auth/refresh'), { method: 'POST', headers: json });
    expect(res.status).toBe(403);
  });

  it('answers problem details with their content type', async () => {
    const res = await fetch(url('/api/v1/me/loans'));
    expect(res.status).toBe(401);
    expect(res.headers.get('Content-Type')).toContain('application/problem+json');
    expect(await res.json()).toMatchObject({ status: 401, code: 'UNAUTHENTICATED' });
  });

  it('creates a loan with 201 and Location', async () => {
    const { accessToken } = await loginAs('ana.gomez@correo.com', 'prestemos123');
    const res = await fetch(url('/api/v1/loans'), {
      method: 'POST',
      headers: { ...json, Authorization: `Bearer ${accessToken}` },
      body: JSON.stringify({ amount: '1500000.00', termMonths: 12 }),
    });
    expect(res.status).toBe(201);
    const loan = (await res.json()) as { id: string; status: string };
    expect(res.headers.get('Location')).toBe(`/api/v1/loans/${loan.id}`);
    expect(loan.status).toBe('PENDING');
  });

  it('streams the approval to the owner over SSE', async () => {
    const user = await loginAs('usuario@test.com');
    const admin = await loginAs('admin@test.com');
    const controller = new AbortController();
    const stream = await fetch(url('/api/v1/loans/events'), {
      headers: { Authorization: `Bearer ${user.accessToken}`, Accept: 'text/event-stream' },
      signal: controller.signal,
    });
    expect(stream.headers.get('Content-Type')).toBe('text/event-stream');
    const reader = stream.body!.getReader();
    const decoder = new TextDecoder();

    await fetch(url('/api/v1/loans/7c1e2a40-5b6d-4e8f-a1b2-000000000001/approve'), {
      method: 'POST',
      headers: { ...json, Authorization: `Bearer ${admin.accessToken}` },
    });

    let received = '';
    while (!received.includes('loan.approved')) {
      const { value, done } = await reader.read();
      if (done) break;
      received += decoder.decode(value);
    }
    controller.abort();
    expect(received).toContain('event: loan.approved');
    expect(received).toContain('"status":"APPROVED"');
  });
});
