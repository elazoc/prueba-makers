import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { UserDto } from '../../core/api/dto';
import { UsersApi } from '../../core/api/users.api';
import { appError } from '../../core/auth/testing';
import { pageOf } from '../my-loans/testing';
import { UserFormDrawer } from './user-form.drawer';
import { UsersStore } from './users.store';

const user = (id: string, role: UserDto['role'] = 'USER'): UserDto => ({
  id,
  email: `${id}@test.com`,
  fullName: `Persona ${id}`,
  role,
  active: true,
  createdAt: '2026-11-01T00:00:00Z',
});

describe('UsersStore', () => {
  it('filters by role and active and sends one creation on a double submit', async () => {
    const api = {
      list: vi.fn(() => of(pageOf([user('u1')]))),
      create: vi.fn(() => of(user('nuevo'))),
    };
    TestBed.configureTestingModule({ providers: [UsersStore, { provide: UsersApi, useValue: api }] });
    const store = TestBed.inject(UsersStore);

    store.setQuery({ role: 'ADMIN', active: false, page: 0 });
    await new Promise((r) => setTimeout(r, 0));
    expect(api.list).toHaveBeenCalledWith(expect.objectContaining({ role: 'ADMIN', active: false, page: 0 }));

    const req = { email: 'n@test.com', password: 'secreto123', fullName: 'Nueva', role: 'USER' as const };
    await Promise.all([store.create(req), store.create(req)]);
    expect(api.create).toHaveBeenCalledTimes(1);
  });
});

describe('UserFormDrawer', () => {
  async function render(data: { user?: UserDto; selfId: string }) {
    const store = { create: vi.fn().mockResolvedValue(user('nuevo')), update: vi.fn().mockResolvedValue(user('u1')) };
    const ref = { close: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: UsersStore, useValue: store },
        { provide: DialogRef, useValue: ref },
        { provide: DIALOG_DATA, useValue: data },
      ],
    });
    const fixture = TestBed.createComponent(UserFormDrawer);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement, store, ref };
  }

  it('locks the role and the active flag on your own account', async () => {
    const { el } = await render({ user: user('a1', 'ADMIN'), selfId: 'a1' });
    expect(el.querySelector<HTMLSelectElement>('select')!.disabled).toBe(true);
    expect(el.querySelector<HTMLInputElement>('input[type="checkbox"]')!.disabled).toBe(true);
    expect(el.textContent).toContain('No puedes quitarte el rol ni desactivar tu cuenta.');
  });

  it('puts a taken email on the email field', async () => {
    const { fixture, el, store, ref } = await render({ selfId: 'a1' });
    store.create.mockRejectedValue(appError(409, 'EMAIL_ALREADY_REGISTERED'));
    const set = (selector: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(selector)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    set('input[formcontrolname="fullName"]', 'Laura Pérez');
    set('input[formcontrolname="email"]', 'usuario@test.com');
    set('input[formcontrolname="password"]', 'secreto123');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await new Promise((r) => setTimeout(r, 0));
    await fixture.whenStable();

    expect(ref.close).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Ya hay una cuenta con este correo.');
  });
});
