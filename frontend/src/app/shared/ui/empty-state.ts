import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { PrIcon } from './icon';
import { IconName } from './icons';

/** Lista vacía: dice qué aparecerá y cómo empezar. Su botón va como secundario. */
@Component({
  selector: 'pr-empty-state',
  imports: [PrIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pr-empty' },
  template: `
    @if (icon(); as name) {
      <span class="pr-empty__icon"><pr-icon [name]="name" /></span>
    }
    <p class="pr-empty__title">{{ title() }}</p>
    @if (description()) {
      <p class="pr-empty__description">{{ description() }}</p>
    }
    <ng-content />
  `,
})
export class PrEmptyState {
  readonly title = input.required<string>();
  readonly description = input<string>();
  readonly icon = input<IconName>();
}
