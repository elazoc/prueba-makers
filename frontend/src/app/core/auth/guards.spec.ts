import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Role } from '../api/dto';
import { ToastService } from '../notify/toast.service';
import { AuthStore, SessionStatus } from './auth.store';
import { authGuard, guestGuard, homeGuard, roleGuard } from './guards';

@Component({ template: 'pantalla', changeDetection: ChangeDetectionStrategy.OnPush })
class Screen {}

function fakeAuth() {
  const status = signal<SessionStatus>('restoring');
  const role = signal<Role | null>(null);
  return {
    status,
    role,
    isAuthenticated: computed(() => status() === 'authenticated'),
    homeUrl: computed(() =>
      role() === 'ADMIN' ? '/admin/solicitudes' : role() === 'USER' ? '/prestamos' : '/ingresar',
    ),
    signIn(r: Role) {
      role.set(r);
      status.set('authenticated');
    },
    anonymous() {
      role.set(null);
      status.set('anonymous');
    },
  };
}

describe('route guards', () => {
  let auth: ReturnType<typeof fakeAuth>;
  let harness: RouterTestingHarness;
  let router: Router;
  let adminLoads: number;

  beforeEach(async () => {
    auth = fakeAuth();
    adminLoads = 0;
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthStore, useValue: auth },
        provideRouter([
          { path: '', pathMatch: 'full', canMatch: [homeGuard], children: [] },
          { path: 'ingresar', canMatch: [guestGuard], component: Screen },
          { path: 'prestamos', canMatch: [authGuard, roleGuard('USER')], component: Screen },
          {
            path: 'admin/solicitudes',
            canMatch: [authGuard, roleGuard('ADMIN')],
            loadComponent: () => {
              adminLoads++;
              return Promise.resolve(Screen);
            },
          },
        ]),
      ],
    });
    harness = await RouterTestingHarness.create();
    router = TestBed.inject(Router);
  });

  it('sends anonymous people to log in with a return url', async () => {
    auth.anonymous();
    await harness.navigateByUrl('/prestamos');
    expect(router.url).toBe('/ingresar?volver=%2Fprestamos');
  });

  it('sends a user who opens an admin screen home with a notice, without loading the admin code', async () => {
    auth.signIn('USER');
    await harness.navigateByUrl('/admin/solicitudes');
    expect(router.url).toBe('/prestamos');
    expect(TestBed.inject(ToastService).toasts().map((t) => t.message)).toContain(
      'Esa sección es solo para administradores.',
    );
    expect(adminLoads).toBe(0);
  });

  it('lets admins in', async () => {
    auth.signIn('ADMIN');
    await harness.navigateByUrl('/admin/solicitudes');
    expect(router.url).toBe('/admin/solicitudes');
    expect(adminLoads).toBe(1);
  });

  it('sends people with a session away from the login', async () => {
    auth.signIn('ADMIN');
    await harness.navigateByUrl('/ingresar');
    expect(router.url).toBe('/admin/solicitudes');
  });

  it('redirects the root to the home of each role', async () => {
    auth.signIn('USER');
    await harness.navigateByUrl('/');
    expect(router.url).toBe('/prestamos');
  });

  it('waits for the session to be restored before deciding', async () => {
    const navigation = harness.navigateByUrl('/prestamos');
    await new Promise((r) => setTimeout(r, 0));
    auth.signIn('USER');
    await navigation;
    expect(router.url).toBe('/prestamos');
  });
});
