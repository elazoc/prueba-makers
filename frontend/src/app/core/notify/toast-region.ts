import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { PrButton } from '../../shared/ui/button';
import { PrIcon } from '../../shared/ui/icon';
import { IconName } from '../../shared/ui/icons';
import { ToastService, ToastTone } from './toast.service';

const ICON: Record<ToastTone, IconName> = {
  success: 'circle-check',
  info: 'info',
  warning: 'triangle-alert',
  danger: 'circle-alert',
};

/** Avisos abajo a la derecha (abajo al centro en el celular). La región existe siempre para que se anuncien. */
@Component({
  selector: 'pr-toast-region',
  imports: [PrIcon, PrButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pr-toasts" aria-live="polite" aria-relevant="additions">
      @for (toast of toasts.toasts(); track toast.id) {
        <div class="pr-toast pr-toast--{{ toast.tone }}" [attr.role]="toast.tone === 'danger' ? 'alert' : null">
          <pr-icon class="pr-toast__icon" [name]="icon[toast.tone]" />
          <p class="pr-toast__message">{{ toast.message }}</p>
          <button prButton variant="ghost" size="sm" class="pr-btn--icon" type="button"
            aria-label="Cerrar aviso" (click)="toasts.dismiss(toast.id)">
            <pr-icon name="x" size="sm" />
          </button>
        </div>
      }
    </div>
  `,
})
export class PrToastRegion {
  protected readonly toasts = inject(ToastService);
  protected readonly icon = ICON;
}
