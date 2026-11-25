import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthStore } from './core/auth/auth.store';
import { ThemeService } from './core/layout/theme.service';
import { PrToastRegion } from './core/notify/toast-region';
import { PrLogo } from './shared/ui/logo';
import { PrSpinner } from './shared/ui/spinner';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, PrToastRegion, PrLogo, PrSpinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (auth.status() === 'restoring') {
      <div class="pr-boot" role="status">
        <pr-logo variant="mark" [size]="48" />
        <pr-spinner class="pr-spinner--lg" />
        <span class="sr-only">Cargando Prestemos…</span>
      </div>
    }
    <router-outlet />
    <pr-toast-region />
  `,
})
export class App {
  protected readonly auth = inject(AuthStore);
  // Se instancia al arrancar para aplicar el tema guardado o el del sistema.
  private readonly theme = inject(ThemeService);
}
