import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { PrButton } from './button';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel: string;
  tone?: 'primary' | 'danger';
}

export interface ConfirmDialogData extends ConfirmOptions {
  titleId: string;
  textId: string;
}

/** Confirmación corta (≤ 440 px): el título dice la acción y el texto, la consecuencia. */
@Component({
  selector: 'pr-confirm-dialog',
  imports: [PrButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pr-dialog' },
  template: `
    <h2 class="pr-dialog__title" [id]="data.titleId">{{ data.title }}</h2>
    <p class="pr-dialog__text" [id]="data.textId">{{ data.message }}</p>
    <div class="pr-dialog__actions">
      <button prButton type="button" data-autofocus (click)="ref.close(false)">Volver</button>
      <button prButton type="button" [variant]="data.tone === 'danger' ? 'danger' : 'primary'"
        (click)="ref.close(true)">{{ data.confirmLabel }}</button>
    </div>
  `,
})
export class PrConfirmDialog {
  protected readonly data = inject<ConfirmDialogData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<boolean>>(DialogRef);
}
