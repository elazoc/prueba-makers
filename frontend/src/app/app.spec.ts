import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { App } from './app';
import { AuthApi } from './core/auth/auth.api';
import { AuthStore } from './core/auth/auth.store';

describe('App', () => {
  it('shows the boot loader while the session is being restored, then the router outlet', async () => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), { provide: AuthApi, useValue: { refresh: () => of() } }],
    });
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.pr-boot')?.textContent).toContain('Cargando Prestemos…');
    expect(el.querySelector('router-outlet')).not.toBeNull();

    TestBed.inject(AuthStore).endSession('logout');
    await fixture.whenStable();
    expect(el.querySelector('.pr-boot')).toBeNull();
  });
});
