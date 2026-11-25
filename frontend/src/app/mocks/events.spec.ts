import { LoanEventDto } from '../core/api/dto';
import { MockEventHub, sseFrame } from './events';

const event: LoanEventDto = {
  eventId: 'e1',
  type: 'loan.approved',
  loanId: 'l1',
  applicantId: 'u1',
  status: 'APPROVED',
  occurredAt: '2026-11-25T12:00:00Z',
};

describe('MockEventHub', () => {
  it('delivers published events to local subscribers until they leave', () => {
    const hub = new MockEventHub(null);
    const got: LoanEventDto[] = [];
    const leave = hub.subscribe((e) => got.push(e));

    hub.publish(event);
    leave();
    hub.publish({ ...event, eventId: 'e2' });

    expect(got.map((e) => e.eventId)).toEqual(['e1']);
  });

  it('relays events coming from other tabs', () => {
    const listeners: ((e: MessageEvent) => void)[] = [];
    const channel = {
      postMessage: vi.fn(),
      addEventListener: (_: string, l: (e: MessageEvent) => void) => listeners.push(l),
    };
    const hub = new MockEventHub(channel as unknown as BroadcastChannel);
    const got: string[] = [];
    hub.subscribe((e) => got.push(e.eventId));

    hub.publish(event);
    listeners.forEach((l) => l({ data: { ...event, eventId: 'remoto' } } as MessageEvent));

    expect(channel.postMessage).toHaveBeenCalledWith(event);
    expect(got).toEqual(['e1', 'remoto']);
  });
});

describe('sseFrame', () => {
  it('writes an SSE frame', () => {
    expect(sseFrame(event)).toBe(`event: loan.approved\nid: e1\ndata: ${JSON.stringify(event)}\n\n`);
  });
});
