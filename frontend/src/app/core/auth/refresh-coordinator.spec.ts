import { TestBed } from '@angular/core/testing';
import { RefreshCoordinator } from './refresh-coordinator';
import { loginResponseFor } from './testing';

describe('RefreshCoordinator', () => {
  it('shares one refresh among concurrent callers', async () => {
    const coordinator = TestBed.inject(RefreshCoordinator);
    const task = vi.fn(async () => loginResponseFor('u1'));

    const [a, b] = await Promise.all([coordinator.run(task), coordinator.run(task)]);

    expect(task).toHaveBeenCalledTimes(1);
    expect(a).toBe(b);
  });

  it('runs again once the previous refresh finished', async () => {
    const coordinator = TestBed.inject(RefreshCoordinator);
    const task = vi.fn(async () => loginResponseFor('u1'));

    await coordinator.run(task);
    await coordinator.run(task);

    expect(task).toHaveBeenCalledTimes(2);
  });

  it('serializes across tabs with the Web Locks API when available', async () => {
    const request = vi.fn((_name: string, cb: () => Promise<unknown>) => cb());
    Object.defineProperty(navigator, 'locks', { value: { request }, configurable: true });
    try {
      const coordinator = TestBed.inject(RefreshCoordinator);
      await coordinator.run(async () => loginResponseFor('u1'));
      expect(request).toHaveBeenCalledWith('prestemos-refresh', expect.any(Function));
    } finally {
      delete (navigator as unknown as Record<string, unknown>)['locks'];
    }
  });
});
