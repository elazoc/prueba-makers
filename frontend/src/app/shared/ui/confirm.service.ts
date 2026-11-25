import { Dialog } from '@angular/cdk/dialog';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ConfirmDialogData, ConfirmOptions, PrConfirmDialog } from './confirm-dialog';

let nextId = 0;

/** Pide confirmación antes de una acción irreversible. Escape o «Volver» responden `false`. */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly dialog = inject(Dialog);

  async confirm(options: ConfirmOptions): Promise<boolean> {
    const id = ++nextId;
    const data: ConfirmDialogData = {
      ...options,
      titleId: `pr-confirm-title-${id}`,
      textId: `pr-confirm-text-${id}`,
    };
    const ref = this.dialog.open<boolean, ConfirmDialogData, PrConfirmDialog>(PrConfirmDialog, {
      data,
      role: 'alertdialog',
      ariaLabelledBy: data.titleId,
      ariaDescribedBy: data.textId,
      panelClass: 'pr-dialog-panel',
      backdropClass: 'pr-scrim',
      // El foco empieza en «Volver»: confirmar algo irreversible pide un gesto deliberado.
      autoFocus: '[data-autofocus]',
      restoreFocus: true,
    });
    return (await firstValueFrom(ref.closed)) === true;
  }
}
