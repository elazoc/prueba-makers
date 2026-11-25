import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from './api-config';
import { LoansApi } from './loans.api';
import { UsersApi } from './users.api';

describe('LoansApi and UsersApi', () => {
  let loans: LoansApi;
  let users: UsersApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });
    loans = TestBed.inject(LoansApi);
    users = TestBed.inject(UsersApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists all with filters and omits undefined params', () => {
    let total = -1;
    loans
      .listAll({ page: 0, size: 20, sort: 'requestedAt,asc', status: 'PENDING' })
      .subscribe((p) => (total = p.total));
    const req = http.expectOne((r) => r.url === '/api/v1/loans');
    expect(req.request.params.get('status')).toBe('PENDING');
    expect(req.request.params.get('sort')).toBe('requestedAt,asc');
    expect(req.request.params.has('applicantEmail')).toBe(false);
    req.flush({ content: [], page: 0, size: 20, totalElements: 7, totalPages: 1 });
    expect(total).toBe(7);
  });

  it('lists mine on /me/loans', () => {
    loans.listMine({ page: 1, size: 20, status: 'APPROVED' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/me/loans');
    expect(req.request.params.get('page')).toBe('1');
    req.flush({ content: [], page: 1, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('rejects with a reason', () => {
    loans.reject('l1', { reason: 'Ingresos insuficientes' }).subscribe();
    const req = http.expectOne('/api/v1/loans/l1/reject');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Ingresos insuficientes' });
    req.flush({});
  });

  it('cancels with DELETE', () => {
    loans.cancel('l1').subscribe();
    expect(http.expectOne('/api/v1/loans/l1').request.method).toBe('DELETE');
  });

  it('filters users by role and active', () => {
    users.list({ page: 0, size: 20, role: 'ADMIN', active: false }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/users');
    expect(req.request.params.get('role')).toBe('ADMIN');
    expect(req.request.params.get('active')).toBe('false');
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });
});
