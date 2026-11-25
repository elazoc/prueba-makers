import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { LoanStatus } from '../../core/api/dto';
import { BadgeTone, PrBadge } from './badge';
import { IconName } from './icons';

const STATUS: Record<LoanStatus, { tone: BadgeTone; icon: IconName; label: string }> = {
  PENDING: { tone: 'warning', icon: 'clock', label: 'Pendiente' },
  APPROVED: { tone: 'success', icon: 'check', label: 'Aprobado' },
  REJECTED: { tone: 'danger', icon: 'x', label: 'Rechazado' },
  CANCELLED: { tone: 'neutral', icon: 'ban', label: 'Cancelado' },
};

export const statusLabel = (s: LoanStatus) => STATUS[s].label;

/** Estado de un préstamo: palabra, ícono y color. */
@Component({
  selector: 'pr-status-badge',
  imports: [PrBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<pr-badge [tone]="config().tone" [icon]="config().icon">{{ config().label }}</pr-badge>`,
})
export class PrStatusBadge {
  readonly status = input.required<LoanStatus>();
  protected readonly config = computed(() => STATUS[this.status()]);
}
