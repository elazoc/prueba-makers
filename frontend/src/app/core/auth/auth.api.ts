import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api/api-config';
import { LoginResponse, RegisterRequest, UserDto } from '../api/dto';

/**
 * Rutas de sesión. Van con credenciales (la cookie HttpOnly del refresh token) y con la
 * cabecera que el backend exige como defensa CSRF de estas rutas.
 */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  private readonly options = {
    withCredentials: true,
    headers: new HttpHeaders({ 'X-Requested-With': 'prestemos' }),
  };

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.base}/auth/login`, { email, password }, this.options);
  }

  register(r: RegisterRequest): Observable<UserDto> {
    return this.http.post<UserDto>(`${this.base}/auth/register`, r, this.options);
  }

  refresh(): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.base}/auth/refresh`, {}, this.options);
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/logout`, {}, this.options);
  }
}
