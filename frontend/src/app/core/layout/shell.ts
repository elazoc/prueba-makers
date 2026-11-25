import { ChangeDetectionStrategy, Component, effect, inject, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { AuthStore } from '../auth/auth.store';
import { PrBottomNav } from './bottom-nav';
import { PrSidebar } from './sidebar';
import { PrTopbar } from './topbar';

/** Título de la ruta más profunda (`data.title`). */
function deepestTitle(route: ActivatedRoute): string {
  let current = route.snapshot;
  let title = '';
  while (current) {
    title = (current.data['title'] as string | undefined) ?? title;
    current = current.firstChild!;
  }
  return title;
}

/** Marco de la app con sesión: barra lateral, barra superior, contenido y navegación del celular. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, PrSidebar, PrTopbar, PrBottomNav],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pr-shell' },
  template: `
    <a class="pr-skip" href="#contenido">Saltar al contenido</a>
    <pr-sidebar />
    <div class="pr-shell__main">
      <pr-topbar [title]="title()" />
      <main id="contenido" class="pr-shell__content" tabindex="-1">
        <router-outlet />
      </main>
    </div>
    <pr-bottom-nav />
  `,
})
export class Shell {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthStore);

  protected readonly title = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      startWith(null),
      map(() => deepestTitle(this.route)),
    ),
    { initialValue: '' },
  );

  constructor() {
    // Si la sesión termina estando aquí (venció, otra pestaña, cuenta desactivada), se va a ingresar
    // con el motivo y la ruta para volver.
    effect(() => {
      if (this.auth.status() !== 'anonymous') return;
      untracked(() => {
        const reason = this.auth.endReason();
        const queryParams =
          reason && reason !== 'logout' ? { motivo: reason, volver: this.router.url } : {};
        void this.router.navigate(['/ingresar'], { queryParams });
      });
    });
  }
}
