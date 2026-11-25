import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthStore } from '../../core/auth/auth.store';
import { appError } from '../../core/auth/testing';
import { RegisterPage } from './register.page';

describe('RegisterPage', () => {
  let auth: { register: ReturnType<typeof vi.fn>; homeUrl: ReturnType<typeof signal<string>> };

  async function render() {
    auth = { register: vi.fn().mockResolvedValue(undefined), homeUrl: signal('/prestamos') };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthStore, useValue: auth }],
    });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(RegisterPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const field = (label: string) => {
      const l = [...el.querySelectorAll('label')].find((x) => x.textContent?.trim() === label)!;
      return el.querySelector<HTMLInputElement>(`#${l.htmlFor}`)!;
    };
    const type = (label: string, v: string) => {
      const i = field(label);
      i.value = v;
      i.dispatchEvent(new Event('input'));
      i.dispatchEvent(new Event('blur'));
    };
    const submit = async () => {
      [...el.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Crear cuenta')!.click();
      await fixture.whenStable();
      await new Promise((r) => setTimeout(r, 0));
      await fixture.whenStable();
    };
    const fill = () => {
      type('Nombre completo', 'Laura Pérez');
      type('Correo', 'laura@correo.com');
      type('Contraseña', 'secreto123');
      type('Confirma la contraseña', 'secreto123');
    };
    return { fixture, el, field, type, submit, fill, navigate };
  }

  it('creates the account and goes home', async () => {
    const { fill, submit, navigate } = await render();
    fill();
    await submit();
    expect(auth.register).toHaveBeenCalledWith({
      fullName: 'Laura Pérez',
      email: 'laura@correo.com',
      password: 'secreto123',
    });
    expect(navigate).toHaveBeenCalledWith('/prestamos');
  });

  it('asks for at least 8 characters and matching passwords', async () => {
    const { fixture, el, type } = await render();
    type('Contraseña', 'corta');
    type('Confirma la contraseña', 'otra');
    await fixture.whenStable();
    expect(el.textContent).toContain('Usa al menos 8 caracteres.');
    expect(el.textContent).toContain('Las contraseñas no coinciden.');
  });

  it('shows a taken email on the email field', async () => {
    const { fixture, el, fill, submit } = await render();
    auth.register.mockRejectedValue(appError(409, 'EMAIL_ALREADY_REGISTERED'));
    fill();
    await submit();
    await fixture.whenStable();
    expect(el.textContent).toContain('Ya hay una cuenta con este correo. Ingresa o usa otro.');
  });
});
