import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { PrIcon } from './icon';
import { IconName } from './icons';

export type BadgeTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

/** Una o dos palabras de estado. El texto es el estado; el color solo lo refuerza. */
@Component({
  selector: 'pr-badge',
  imports: [PrIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="pr-badge pr-badge--{{ tone() }}">
    @if (icon(); as name) {
      <pr-icon [name]="name" size="sm" />
    }
    <ng-content />
  </span>`,
})
export class PrBadge {
  readonly tone = input<BadgeTone>('neutral');
  readonly icon = input<IconName>();
}
