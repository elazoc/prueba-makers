import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AuthApi } from './auth.api';
import { AuthStore } from './auth.store';
import { SessionChannel } from './session-channel';
import { appError, loginResponseFor } from './testing';

class FakeChannel {
  private listeners: (() => void)[] = [];
  announceLogout = vi.fn();
  onLogout(fn: () => void) {
    this.listeners.push(fn);
    return () => (this.listeners = this.listeners.filter((l) => l !== fn));
  }
  emitLogout() {
    this.listeners.forEach((l) => l());
  }
}

describe('AuthStore', () => {
  let api: { login: ReturnType<typeof vi.fn>; register: ReturnType<typeof vi.fn>; refresh: ReturnType<typeof vi.fn>; logout: ReturnType<typeof vi.fn> };
  let channel: FakeChannel;
  let store: InstanceType<typeof AuthStore>;

  beforeEach(() => {
    api = {
      login: vi.fn(() => of(loginResponseFor('u1', 'TOKEN-SECRETO'))),
      register: vi.fn(() => of(loginResponseFor('u1').user)),
      refresh: vi.fn(() => of(loginResponseFor('u1', 'TOKEN-NUEVO'))),
      logout: vi.fn(() => of(undefined)),
    };
    channel = new FakeChannel();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthApi, useValue: api },
        { provide: SessionChannel, useValue: channel },
      ],
    });
    store = TestBed.inject(AuthStore);
  });

  afterEach(() => vi.useRealTimers());

  it('restores the session from the refresh cookie', async () => {
    await store.restore();
    expect(store.status()).toBe('authenticated');
    expect(store.accessToken()).toBe('TOKEN-NUEVO');
    expect(store.homeUrl()).toBe('/prestamos');
  });

  it('starts anonymous when there is no cookie', async () => {
    api.refresh.mockReturnValue(throwError(() => appError(401, 'REFRESH_TOKEN_INVALID')));
    await store.restore();
    expect(store.status()).toBe('anonymous');
    expect(store.endReason()).toBeNull();
    expect(store.homeUrl()).toBe('/ingresar');
  });

  it('sends admins to the requests screen', async () => {
    api.login.mockReturnValue(of(loginResponseFor('a1', 'T', 'ADMIN')));
    await store.login('admin@test.com', '123');
    expect(store.isAdmin()).toBe(true);
    expect(store.homeUrl()).toBe('/admin/solicitudes');
  });

  it('never writes the access token to browser storage', async () => {
    await store.login('usuario@test.com', '123');
    const everything =
      JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }) + document.cookie;
    expect(store.accessToken()).toBe('TOKEN-SECRETO');
    expect(everything).not.toContain('TOKEN-SECRETO');
  });

  it('rethrows login errors without opening a session', async () => {
    api.login.mockReturnValue(throwError(() => appError(401, 'INVALID_CREDENTIALS')));
    await expect(store.login('x@test.com', 'mal')).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' });
    expect(store.isAuthenticated()).toBe(false);
    expect(store.accessToken()).toBeNull();
  });

  it('registers and then logs in', async () => {
    await store.register({ email: 'u1@test.com', password: 'secreto123', fullName: 'Persona' });
    expect(api.login).toHaveBeenCalledWith('u1@test.com', 'secreto123');
    expect(store.isAuthenticated()).toBe(true);
  });

  it('refreshes silently one minute before the access token expires', async () => {
    vi.useFakeTimers();
    await store.login('usuario@test.com', '123'); // expiresIn 900 s
    vi.advanceTimersByTime(839_000);
    expect(api.refresh).not.toHaveBeenCalled();
    vi.advanceTimersByTime(2_000);
    expect(api.refresh).toHaveBeenCalledTimes(1);
  });

  it('ends the session as expired when the refresh is rejected', async () => {
    await store.login('usuario@test.com', '123');
    api.refresh.mockReturnValue(throwError(() => appError(401, 'REFRESH_TOKEN_INVALID')));
    expect(await store.refresh()).toBe(false);
    expect(store.status()).toBe('anonymous');
    expect(store.endReason()).toBe('expired');
  });

  it('ends the session as disabled when the account was deactivated', async () => {
    await store.login('usuario@test.com', '123');
    api.refresh.mockReturnValue(throwError(() => appError(401, 'ACCOUNT_DISABLED')));
    expect(await store.refresh()).toBe(false);
    expect(store.endReason()).toBe('disabled');
  });

  it('keeps the session when the refresh fails for a network problem', async () => {
    await store.login('usuario@test.com', '123');
    api.refresh.mockReturnValue(throwError(() => appError(0, 'NETWORK_ERROR')));
    expect(await store.refresh()).toBe(false);
    expect(store.isAuthenticated()).toBe(true);
  });

  it('ends the session instead of switching identity when the cookie belongs to another account', async () => {
    await store.login('usuario@test.com', '123');
    api.refresh.mockReturnValue(of(loginResponseFor('a1', 'T', 'ADMIN')));
    expect(await store.refresh()).toBe(false);
    expect(store.endReason()).toBe('switched');
    expect(store.user()).toBeNull();
  });

  it('logs out, tells other tabs and ignores logout errors', async () => {
    await store.login('usuario@test.com', '123');
    api.logout.mockReturnValue(throwError(() => appError(0, 'NETWORK_ERROR')));
    await store.logout();
    expect(store.status()).toBe('anonymous');
    expect(store.endReason()).toBe('logout');
    expect(channel.announceLogout).toHaveBeenCalled();
  });

  it('ends the session when another tab logs out', async () => {
    await store.login('usuario@test.com', '123');
    channel.emitLogout();
    expect(store.endReason()).toBe('elsewhere');
    expect(store.accessToken()).toBeNull();
  });
});
