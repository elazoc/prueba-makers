import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { API_BASE_URL } from '../api/api-config';
import { problemInterceptor } from '../api/problem.interceptor';
import { AuthApi } from './auth.api';
import { authInterceptor } from './auth.interceptor';
import { AuthStore } from './auth.store';
import { SessionChannel } from './session-channel';
import { appError, loginResponseFor } from './testing';

const flushMicrotasks = () => new Promise((r) => setTimeout(r, 0));
const problem401 = { status: 401, code: 'UNAUTHENTICATED', detail: 'Inicia sesión.' };

describe('authInterceptor', () => {
  let api: { login: ReturnType<typeof vi.fn>; refresh: ReturnType<typeof vi.fn>; logout: ReturnType<typeof vi.fn>; register: ReturnType<typeof vi.fn> };
  let http: HttpClient;
  let ctl: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    api = {
      login: vi.fn(() => of(loginResponseFor('u1', 'OLD'))),
      refresh: vi.fn(() => of(loginResponseFor('u1', 'NEW'))),
      logout: vi.fn(() => of(undefined)),
      register: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor, problemInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
        { provide: AuthApi, useValue: api },
        { provide: SessionChannel, useValue: { announceLogout: vi.fn(), onLogout: () => () => undefined } },
      ],
    });
    http = TestBed.inject(HttpClient);
    ctl = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    await TestBed.inject(AuthStore).login('usuario@test.com', '123');
  });

  it('adds the bearer token to API calls', () => {
    http.get('/api/v1/me/loans').subscribe();
    expect(ctl.expectOne('/api/v1/me/loans').request.headers.get('Authorization')).toBe('Bearer OLD');
  });

  it('does not add the token to auth routes or other hosts', () => {
    http.post('/api/v1/auth/login', {}).subscribe();
    http.get('https://otro.com/api/v1/x').subscribe();
    expect(ctl.expectOne('/api/v1/auth/login').request.headers.has('Authorization')).toBe(false);
    expect(ctl.expectOne('https://otro.com/api/v1/x').request.headers.has('Authorization')).toBe(false);
  });

  it('refreshes once for two concurrent 401 and retries both', async () => {
    http.get('/api/v1/me/loans').subscribe();
    http.get('/api/v1/users/me').subscribe();
    ctl
      .match((r) => r.url.startsWith('/api/v1/'))
      .forEach((r) => r.flush(problem401, { status: 401, statusText: 'Unauthorized' }));
    await flushMicrotasks();

    expect(api.refresh).toHaveBeenCalledTimes(1);
    expect(ctl.match((r) => r.headers.get('Authorization') === 'Bearer NEW')).toHaveLength(2);
  });

  it('sends the person to log in with a return url when the refresh fails', async () => {
    api.refresh.mockReturnValue(throwError(() => appError(401, 'REFRESH_TOKEN_INVALID')));
    vi.spyOn(router, 'url', 'get').mockReturnValue('/prestamos');
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    let failed: unknown;
    http.get('/api/v1/me/loans').subscribe({ error: (e) => (failed = e) });

    ctl.expectOne('/api/v1/me/loans').flush(problem401, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();

    expect(navigate).toHaveBeenCalledWith(['/ingresar'], {
      queryParams: { volver: '/prestamos', motivo: 'expired' },
    });
    expect(failed).toMatchObject({ status: 401 });
  });

  it('does not retry other errors', () => {
    let failed: unknown;
    http.get('/api/v1/loans/x').subscribe({ error: (e) => (failed = e) });
    ctl.expectOne('/api/v1/loans/x').flush({ status: 404, code: 'LOAN_NOT_FOUND', detail: 'No' }, { status: 404, statusText: 'Not Found' });
    expect(api.refresh).not.toHaveBeenCalled();
    expect(failed).toMatchObject({ code: 'LOAN_NOT_FOUND' });
  });
});
