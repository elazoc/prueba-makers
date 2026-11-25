import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { LiveEventsService } from './live-events.service';

const LABEL = {
  off: '',
  connecting: 'Conectando…',
  live: 'En vivo',
  reconnecting: 'Reconectando…',
} as const;

/** Estado de los avisos en vivo, siempre con palabra (no solo un punto de color). */
@Component({
  selector: 'pr-live-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pr-live', '[class.pr-live--hidden]': "status() === 'off'" },
  template: `
    <span class="pr-live__badge pr-live__badge--{{ status() }}" aria-live="polite">
      <span class="pr-live__dot" aria-hidden="true"></span>{{ label() }}
    </span>
  `,
})
export class PrLiveStatus {
  protected readonly status = inject(LiveEventsService).status;
  protected readonly label = computed(() => LABEL[this.status()]);
}
