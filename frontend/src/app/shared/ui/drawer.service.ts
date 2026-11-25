import { CdkDialogContainer, Dialog, DialogRef } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  Injectable,
  Injector,
  Type,
  inject,
  input,
} from '@angular/core';
import { PrButton } from './button';
import { PrIcon } from './icon';

/** Panel lateral para formularios: a la derecha en escritorio, pantalla completa en el celular. */
@Injectable({ providedIn: 'root' })
export class DrawerService {
  private readonly dialog = inject(Dialog);

  /** `injector`: el de la pantalla que abre el panel, para que vea los stores de su ruta. */
  open<C, D, R>(component: Type<C>, data: D, injector?: Injector): DialogRef<R, C> {
    return this.dialog.open<R, D, C>(component, {
      data,
      injector,
      panelClass: 'pr-drawer-panel',
      backdropClass: 'pr-scrim',
      // El foco va al primer campo del formulario; si el panel no tiene campos, al panel mismo
      // (que el CDK anuncia con su título), no a la X de cerrar.
      autoFocus: '.pr-drawer__body input, .pr-drawer__body textarea, .pr-drawer__body select',
      restoreFocus: true,
    });
  }
}

let nextId = 0;

/**
 * Armazón de un panel: encabezado con título y cerrar, cuerpo con scroll y pie fijo
 * (`<div drawer-footer>`) con la acción principal al final.
 */
@Component({
  selector: 'pr-drawer',
  imports: [PrButton, PrIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pr-drawer' },
  template: `
    <header class="pr-drawer__header">
      <div>
        <h2 class="pr-drawer__title" [id]="titleId">{{ title() }}</h2>
        @if (description()) {
          <p class="pr-drawer__description">{{ description() }}</p>
        }
      </div>
      <button prButton variant="ghost" class="pr-btn--icon" type="button" aria-label="Cerrar panel"
        (click)="ref.close()">
        <pr-icon name="x" />
      </button>
    </header>
    <div class="pr-drawer__body"><ng-content /></div>
    <footer class="pr-drawer__footer"><ng-content select="[drawer-footer]" /></footer>
  `,
})
export class PrDrawer {
  readonly title = input.required<string>();
  readonly description = input<string>();
  protected readonly ref = inject(DialogRef);
  readonly titleId = `pr-drawer-title-${++nextId}`;

  constructor() {
    // El contenedor del CDK (role="dialog") toma su nombre accesible del título del panel.
    (this.ref.containerInstance as unknown as CdkDialogContainer | undefined)?._addAriaLabelledBy(
      this.titleId,
    );
  }
}
