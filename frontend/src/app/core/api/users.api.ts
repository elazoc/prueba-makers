import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL, toParams } from './api-config';
import { CreateUserRequest, PageDto, Role, UpdateUserRequest, UserDto } from './dto';
import { Page, PageQuery, toPage } from './page';

@Injectable({ providedIn: 'root' })
export class UsersApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  me(): Observable<UserDto> {
    return this.http.get<UserDto>(`${this.base}/users/me`);
  }

  list(q: PageQuery & { role?: Role; active?: boolean }): Observable<Page<UserDto>> {
    return this.http
      .get<PageDto<UserDto>>(`${this.base}/users`, { params: toParams({ ...q }) })
      .pipe(map(toPage));
  }

  get(id: string): Observable<UserDto> {
    return this.http.get<UserDto>(`${this.base}/users/${id}`);
  }

  create(r: CreateUserRequest): Observable<UserDto> {
    return this.http.post<UserDto>(`${this.base}/users`, r);
  }

  update(id: string, r: UpdateUserRequest): Observable<UserDto> {
    return this.http.put<UserDto>(`${this.base}/users/${id}`, r);
  }

  deactivate(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/users/${id}`);
  }
}
