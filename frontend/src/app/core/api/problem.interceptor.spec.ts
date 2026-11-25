import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { AppError } from './app-error';
import { problemInterceptor } from './problem.interceptor';

describe('problemInterceptor', () => {
  it('turns http errors into app errors', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([problemInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    const http = TestBed.inject(HttpClient);
    const ctl = TestBed.inject(HttpTestingController);

    const result = firstValueFrom(http.get('/api/v1/loans/x')).catch((e: unknown) => e);
    ctl
      .expectOne('/api/v1/loans/x')
      .flush(
        { status: 404, code: 'LOAN_NOT_FOUND', detail: 'No encontramos este préstamo.' },
        { status: 404, statusText: 'Not Found' },
      );

    expect(await result).toMatchObject<Partial<AppError>>({
      kind: 'app-error',
      status: 404,
      code: 'LOAN_NOT_FOUND',
      detail: 'No encontramos este préstamo.',
    });
  });
});
