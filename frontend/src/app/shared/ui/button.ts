import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  inject,
  input,
  untracked,
} from '@angular/core';
import { PrSpinner } from './spinner';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';

/**
 * Botón de la marca sobre el `<button>` nativo (conserva teclado y foco).
 * Mientras carga muestra el cargador y la palabra («Aprobando…»), conserva su ancho,
 * queda `aria-busy` y no deja pasar clics.
 */
@Component({
  // Selector de atributo a propósito: se monta sobre el <button>/<a> nativo para conservar su
  // semántica, teclado y foco.
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'button[prButton], a[prButton]',
  imports: [PrSpinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'pr-btn',
    '[class.pr-btn--primary]': "variant() === 'primary'",
    '[class.pr-btn--secondary]': "variant() === 'secondary'",
    '[class.pr-btn--danger]': "variant() === 'danger'",
    '[class.pr-btn--ghost]': "variant() === 'ghost'",
    '[class.pr-btn--sm]': "size() === 'sm'",
    '[class.pr-btn--md]': "size() === 'md'",
    '[class.pr-btn--lg]': "size() === 'lg'",
    '[class.pr-btn--loading]': 'loading()',
    '[attr.aria-busy]': "loading() ? 'true' : null",
    '[attr.aria-disabled]': "loading() ? 'true' : null",
  },
  template: `
    @if (loading()) {
      <pr-spinner [tone]="variant() === 'primary' || variant() === 'danger' ? 'on-action' : 'default'" />
    }
    <span class="pr-btn__content" [hidden]="loading() && !!loadingText()"><ng-content /></span>
    @if (loading() && loadingText()) {
      <span class="pr-btn__content">{{ loadingText() }}</span>
    }
  `,
})
export class PrButton {
  readonly variant = input<ButtonVariant>('secondary');
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  readonly loading = input(false);
  readonly loadingText = input<string>();

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

    // En la fase de captura del propio botón: corre antes que los (click) de quien lo usa.
    host.addEventListener(
      'click',
      (e) => {
        if (this.loading()) {
          e.preventDefault();
          e.stopImmediatePropagation();
        }
      },
      { capture: true },
    );

    effect(() => {
      const loading = this.loading();
      untracked(() => {
        host.style.minWidth = loading ? `${host.offsetWidth}px` : '';
      });
    });
  }
}
