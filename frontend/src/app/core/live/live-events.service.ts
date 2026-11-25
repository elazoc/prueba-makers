import { DOCUMENT } from '@angular/common';
import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { API_BASE_URL } from '../api/api-config';
import { LoanEventDto } from '../api/dto';
import { AuthStore } from '../auth/auth.store';
import { SseMessage, createSseParser } from './sse-parser';

export type LiveStatus = 'off' | 'connecting' | 'live' | 'reconnecting';

/** Espera entre reintentos, en segundos. Se queda en 30. */
const BACKOFF_S = [1, 2, 4, 8, 16, 30];

/**
 * Avisos en vivo por SSE. Usa `fetch` con `Authorization: Bearer` porque `EventSource` no permite
 * cabeceras y el token en la URL quedaría en logs. El evento avisa; los stores vuelven a pedir lo
 * que muestran.
 */
@Injectable({ providedIn: 'root' })
export class LiveEventsService implements OnDestroy {
  private readonly auth = inject(AuthStore);
  private readonly base = inject(API_BASE_URL);
  private readonly document = inject(DOCUMENT);

  private readonly state = signal<LiveStatus>('off');
  readonly status = this.state.asReadonly();

  private readonly events = new Subject<LoanEventDto>();
  readonly events$ = this.events.asObservable();

  /** Se emite al volver a la pestaña: algo pudo pasar mientras estaba oculta. */
  private readonly resync = new Subject<void>();
  readonly resync$ = this.resync.asObservable();

  private active = false;
  private attempt = 0;
  private controller: AbortController | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | undefined;

  private readonly onVisibility = () => {
    if (this.active && this.document.visibilityState === 'visible') this.resync.next();
  };

  constructor() {
    this.document.addEventListener('visibilitychange', this.onVisibility);
  }

  connect(): void {
    if (this.active) return;
    this.active = true;
    this.attempt = 0;
    void this.open();
  }

  disconnect(): void {
    this.active = false;
    clearTimeout(this.retryTimer);
    this.controller?.abort();
    this.controller = null;
    this.state.set('off');
  }

  ngOnDestroy(): void {
    this.disconnect();
    this.document.removeEventListener('visibilitychange', this.onVisibility);
  }

  private async open(): Promise<void> {
    const token = this.auth.accessToken();
    if (!this.active || !token) {
      this.disconnect();
      return;
    }
    this.state.set(this.attempt === 0 ? 'connecting' : 'reconnecting');
    const controller = new AbortController();
    this.controller = controller;

    try {
      const res = await fetch(`${this.base}/loans/events`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
        cache: 'no-store',
        signal: controller.signal,
      });

      if (res.status === 401) {
        const renewed = await this.auth.refresh();
        if (renewed && this.active) void this.open();
        else this.disconnect();
        return;
      }
      // El servidor no ofrece avisos en vivo (404/405/406/501): no se insiste ni se muestra el indicador.
      if ([404, 405, 406, 501].includes(res.status)) {
        this.disconnect();
        return;
      }
      if (!res.ok || !res.body) throw new Error(`SSE ${res.status}`);

      this.state.set('live');
      this.attempt = 0;
      await this.read(res.body);
      // El servidor cerró (cada 30 min): se reconecta con el token vigente.
      this.scheduleRetry();
    } catch {
      if (!controller.signal.aborted) this.scheduleRetry();
    }
  }

  private async read(body: ReadableStream<Uint8Array>): Promise<void> {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    const feed = createSseParser((m) => this.handle(m));
    for (;;) {
      const { value, done } = await reader.read();
      if (done) return;
      feed(decoder.decode(value, { stream: true }));
    }
  }

  private handle(m: SseMessage): void {
    if (!m.event?.startsWith('loan.')) return;
    try {
      this.events.next(JSON.parse(m.data) as LoanEventDto);
    } catch {
      // Un evento malformado no corta el flujo.
    }
  }

  private scheduleRetry(): void {
    if (!this.active) return;
    this.state.set('reconnecting');
    const delay = BACKOFF_S[Math.min(this.attempt, BACKOFF_S.length - 1)] * 1000;
    this.attempt++;
    this.retryTimer = setTimeout(() => void this.open(), delay);
  }
}
