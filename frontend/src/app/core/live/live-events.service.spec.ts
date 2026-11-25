import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL } from '../api/api-config';
import { LoanEventDto } from '../api/dto';
import { AuthStore } from '../auth/auth.store';
import { LiveEventsService } from './live-events.service';

const event: LoanEventDto = {
  eventId: 'e1',
  type: 'loan.approved',
  loanId: 'l1',
  applicantId: 'u1',
  status: 'APPROVED',
  occurredAt: '2026-11-25T12:00:00Z',
};

/** Respuesta SSE que entrega `text` y queda abierta (como un flujo real). */
function openStream(text = ''): Response {
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      if (text) controller.enqueue(new TextEncoder().encode(text));
    },
  });
  return new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } });
}

describe('LiveEventsService', () => {
  let fetchMock: ReturnType<typeof vi.fn>;
  let auth: { accessToken: ReturnType<typeof signal<string | null>>; refresh: ReturnType<typeof vi.fn> };
  let service: LiveEventsService;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    auth = { accessToken: signal<string | null>('TOKEN'), refresh: vi.fn().mockResolvedValue(true) };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthStore, useValue: auth },
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });
    service = TestBed.inject(LiveEventsService);
  });

  afterEach(() => {
    service.disconnect();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('connects with the bearer token and emits parsed loan events', async () => {
    fetchMock.mockResolvedValue(openStream(`event: loan.approved\ndata: ${JSON.stringify(event)}\n\n`));
    const first = firstValueFrom(service.events$);
    service.connect();

    expect(await first).toEqual(event);
    expect(service.status()).toBe('live');
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/loans/events');
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer TOKEN');
  });

  it('backs off 1, 2 and 4 seconds and resets after connecting', async () => {
    vi.useFakeTimers();
    fetchMock
      .mockRejectedValueOnce(new TypeError('red'))
      .mockRejectedValueOnce(new TypeError('red'))
      .mockRejectedValueOnce(new TypeError('red'))
      .mockResolvedValue(openStream());

    service.connect();
    await vi.advanceTimersByTimeAsync(0);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(service.status()).toBe('reconnecting');

    await vi.advanceTimersByTimeAsync(999);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(2000);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    await vi.advanceTimersByTimeAsync(4000);
    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(service.status()).toBe('live');
  });

  it('refreshes once on 401 and stops when the refresh fails', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 401 }));
    auth.refresh.mockResolvedValue(false);
    service.connect();
    await new Promise((r) => setTimeout(r, 0));

    expect(auth.refresh).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(service.status()).toBe('off');
  });

  it('does not connect without a session', () => {
    auth.accessToken.set(null);
    service.connect();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(service.status()).toBe('off');
  });

  it('aborts the stream on disconnect', async () => {
    fetchMock.mockResolvedValue(openStream());
    service.connect();
    await new Promise((r) => setTimeout(r, 0));
    const signalUsed = (fetchMock.mock.calls[0][1] as RequestInit).signal!;

    service.disconnect();
    expect(signalUsed.aborted).toBe(true);
    expect(service.status()).toBe('off');
  });

  it('asks to resync when the tab becomes visible again', async () => {
    fetchMock.mockResolvedValue(openStream());
    service.connect();
    const resync = firstValueFrom(service.resync$);
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
    document.dispatchEvent(new Event('visibilitychange'));
    await expect(resync).resolves.toBeUndefined();
  });
});
