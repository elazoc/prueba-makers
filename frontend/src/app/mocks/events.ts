import { LoanEventDto } from '../core/api/dto';
import type { MockUser } from './db';

type Listener = (e: LoanEventDto) => void;

/**
 * Bus de eventos del simulador. Reparte a esta pestaña y, por BroadcastChannel, a las demás:
 * así el usuario ve en vivo la aprobación que hizo el administrador en otra pestaña.
 */
export class MockEventHub {
  private listeners = new Set<Listener>();

  constructor(private readonly channel: BroadcastChannel | null) {
    channel?.addEventListener('message', (e: MessageEvent) => this.deliver(e.data as LoanEventDto));
  }

  publish(e: LoanEventDto): void {
    this.deliver(e);
    this.channel?.postMessage(e);
  }

  subscribe(fn: Listener): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  private deliver(e: LoanEventDto): void {
    this.listeners.forEach((l) => l(e));
  }
}

/** Misma tabla que el backend (§11.2): el administrador ve todo; el usuario, solo las decisiones sobre lo suyo. */
export function visibleTo(e: LoanEventDto, user: MockUser): boolean {
  if (user.role === 'ADMIN') return true;
  return (e.type === 'loan.approved' || e.type === 'loan.rejected') && e.applicantId === user.id;
}

export function sseFrame(e: LoanEventDto): string {
  return `event: ${e.type}\nid: ${e.eventId}\ndata: ${JSON.stringify(e)}\n\n`;
}

export const mockEvents = new MockEventHub(
  typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel('prestemos-mock-events'),
);
