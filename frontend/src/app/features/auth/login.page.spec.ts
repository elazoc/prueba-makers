import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthStore } from '../../core/auth/auth.store';
import { appError } from '../../core/auth/testing';
import { LoginPage } from './login.page';

describe('LoginPage', () => {
  let auth: { login: ReturnType<typeof vi.fn>; homeUrl: ReturnType<typeof signal<string>> };
  let navigate: ReturnType<typeof vi.fn>;

  async function render(inputs: Record<string, string> = {}) {
    auth = { login: vi.fn().mockResolvedValue(undefined), homeUrl: signal('/prestamos') };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthStore, useValue: auth }],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true) as never;
    const fixture = TestBed.createComponent(LoginPage);
    Object.entries(inputs).forEach(([k, v]) => fixture.componentRef.setInput(k, v));
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const buttons = (name: string) => [...el.querySelectorAll('button')].filter((b) => b.textContent?.trim() === name);
    const input = (type: string) => el.querySelector<HTMLInputElement>(`input[autocomplete="${type}"]`)!;
    const type = (i: HTMLInputElement, v: string) => {
      i.value = v;
      i.dispatchEvent(new Event('input'));
    };
    const submit = async () => {
      buttons('Ingresar')[0].click();
      await fixture.whenStable();
      await new Promise((r) => setTimeout(r, 0));
      await fixture.whenStable();
    };
    return { fixture, el, buttons, input, type, submit };
  }

  it('fills a demo account and focuses the submit button', async () => {
    const { buttons, input } = await render();
    buttons('Usar')[0].click();
    expect(input('username').value).toBe('usuario@test.com');
    expect(input('current-password').value).toBe('123');
    expect(document.activeElement).toBe(buttons('Ingresar')[0]);
  });

  it('logs in and goes home', async () => {
    const { input, type, submit } = await render();
    type(input('username'), 'usuario@test.com');
    type(input('current-password'), '123');
    await submit();
    expect(auth.login).toHaveBeenCalledWith('usuario@test.com', '123');
    expect(navigate).toHaveBeenCalledWith('/prestamos');
  });

  it('shows one generic message on wrong credentials', async () => {
    const { el, input, type, submit } = await render();
    auth.login.mockRejectedValue(appError(401, 'INVALID_CREDENTIALS'));
    type(input('username'), 'usuario@test.com');
    type(input('current-password'), 'mal');
    await submit();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'El correo o la contraseña no coinciden. Revísalos e intenta de nuevo.',
    );
  });

  it('goes back to an internal return url but ignores external ones', async () => {
    let page = await render({ volver: '/prestamos?estado=PENDING' });
    page.type(page.input('username'), 'usuario@test.com');
    page.type(page.input('current-password'), '123');
    await page.submit();
    expect(navigate).toHaveBeenCalledWith('/prestamos?estado=PENDING');

    TestBed.resetTestingModule();
    page = await render({ volver: '//malo.com' });
    page.type(page.input('username'), 'usuario@test.com');
    page.type(page.input('current-password'), '123');
    await page.submit();
    expect(navigate).toHaveBeenCalledWith('/prestamos');
  });

  it('focuses the first invalid field on an invalid submit', async () => {
    const { input, submit } = await render();
    await submit();
    expect(document.activeElement).toBe(input('username'));
    expect(auth.login).not.toHaveBeenCalled();
  });

  it.each([
    ['expired', 'Tu sesión venció. Ingresa de nuevo.'],
    ['disabled', 'Tu cuenta está desactivada. Habla con un administrador.'],
    ['elsewhere', 'Cerraste sesión en otra pestaña.'],
    ['switched', 'Ingresaste con otra cuenta en otra pestaña. Ingresa de nuevo para seguir con esta.'],
  ])('explains why the session ended (%s)', async (motivo, text) => {
    const { el } = await render({ motivo });
    expect(el.textContent).toContain(text);
  });

  it('can show the password', async () => {
    const { fixture, el, input } = await render();
    const toggle = el.querySelector<HTMLButtonElement>('button[aria-label="Mostrar contraseña"]')!;
    toggle.click();
    await fixture.whenStable();
    expect(input('current-password').type).toBe('text');
    expect(toggle.getAttribute('aria-label')).toBe('Ocultar contraseña');
    expect(toggle.getAttribute('aria-pressed')).toBe('true');
  });
});
