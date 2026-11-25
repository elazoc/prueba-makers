import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL, toParams } from './api-config';
import { AdminLoanDto, LoanDto, LoanRequest, LoanStatus, PageDto, RejectRequest } from './dto';
import { Page, PageQuery, toPage } from './page';

@Injectable({ providedIn: 'root' })
export class LoansApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  listMine(q: PageQuery & { status?: LoanStatus }): Observable<Page<LoanDto>> {
    return this.http
      .get<PageDto<LoanDto>>(`${this.base}/me/loans`, { params: toParams({ ...q }) })
      .pipe(map(toPage));
  }

  listAll(
    q: PageQuery & { status?: LoanStatus; applicantEmail?: string },
  ): Observable<Page<AdminLoanDto>> {
    return this.http
      .get<PageDto<AdminLoanDto>>(`${this.base}/loans`, { params: toParams({ ...q }) })
      .pipe(map(toPage));
  }

  get(id: string): Observable<LoanDto> {
    return this.http.get<LoanDto>(`${this.base}/loans/${id}`);
  }

  request(r: LoanRequest): Observable<LoanDto> {
    return this.http.post<LoanDto>(`${this.base}/loans`, r);
  }

  update(id: string, r: LoanRequest): Observable<LoanDto> {
    return this.http.put<LoanDto>(`${this.base}/loans/${id}`, r);
  }

  cancel(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/loans/${id}`);
  }

  approve(id: string): Observable<LoanDto> {
    return this.http.post<LoanDto>(`${this.base}/loans/${id}/approve`, {});
  }

  reject(id: string, r: RejectRequest): Observable<LoanDto> {
    return this.http.post<LoanDto>(`${this.base}/loans/${id}/reject`, r);
  }
}
