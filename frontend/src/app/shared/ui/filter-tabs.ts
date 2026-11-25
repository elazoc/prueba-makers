import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

export interface FilterOption<T> {
  value: T;
  label: string;
}

/** Filtro de una sola opción, con el aspecto de las pestañas del manual. */
@Component({
  selector: 'pr-filter-tabs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pr-tabs" role="group" [attr.aria-label]="label()">
      @for (option of options(); track option.value) {
        <button type="button" class="pr-tab" [attr.aria-pressed]="option.value === value()"
          (click)="value.set(option.value)">{{ option.label }}</button>
      }
    </div>
  `,
})
export class PrFilterTabs<T> {
  readonly options = input.required<FilterOption<T>[]>();
  readonly value = model.required<T>();
  readonly label = input.required<string>();
}
