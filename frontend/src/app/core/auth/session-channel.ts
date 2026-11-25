import { Injectable, OnDestroy } from '@angular/core';

/** Avisa a las demás pestañas que se cerró la sesión. */
@Injectable({ providedIn: 'root' })
export class SessionChannel implements OnDestroy {
  private readonly channel =
    typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel('prestemos-auth');

  announceLogout(): void {
    this.channel?.postMessage({ type: 'logout' });
  }

  onLogout(fn: () => void): () => void {
    if (!this.channel) return () => undefined;
    const listener = (e: MessageEvent) => {
      if ((e.data as { type?: string })?.type === 'logout') fn();
    };
    this.channel.addEventListener('message', listener);
    return () => this.channel?.removeEventListener('message', listener);
  }

  ngOnDestroy(): void {
    this.channel?.close();
  }
}
